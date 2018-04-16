(ns onboarding.db
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [toolbelt.core :as tb]
            [cljs.spec.alpha :as s]))

;; =============================================================================
;; Menu
;; =============================================================================

(defn keypath
  "Given a sequence of keywords, produce a single (possibly) namespaced
  keyword representation of the keyword as path. See pattern:

  '(:a)       -> :a
  '(:a :b)    -> :a/b
  '(:a :b :c) -> :a.b/c"
  [& kws]
  (if (= (count kws) 1)
    (first kws)
    (keyword
     (->> (butlast kws)
          (map name)
          (interpose ".")
          (apply str))
     (name (last kws)))))

(defn- with-keypaths
  "Recursively add `:keypath` keys to each menu entry, the values of which are
  derived by combining the sequence of keys from each menu item through all of
  its children."
  ([menu]
   (with-keypaths menu []))
  ([menu path]
   (reduce
    (fn [menu {:keys [key children save skip] :as item}]
      (let [path (conj path key)]
        (conj menu (-> (assoc item :keypath (apply keypath path))
                       (assoc :children (with-keypaths children path))))))
    []
    menu)))

(defn- key->uri [key]
  (str "/" (name key)))

(defn menu->routes
  "Convert the `menu` data structure into bidi routes."
  ([menu]
   (menu->routes menu []))
  ([menu routes]
   (reduce
    (fn [routes {:keys [key keypath children] :as item}]
      (conj routes [(key->uri key)
                    (if (empty? children)
                      keypath
                      (vec (cons ["" keypath] (menu->routes children))))]))
    routes
    menu)))

(defn requirements-for
  "Produce the requirements for the menu item found under `keypath`."
  [keypath items]
  (reduce
   (fn [found {:keys [requires children] :as item}]
     (cond
       ;; if found, return
       found                       found
       (= keypath (:keypath item)) requires
       (not (empty? children))     (requirements-for keypath children)))
   nil
   items))

(defn- update-in-menu
  [menu-items keypath k v]
  (reduce
   (fn [acc item]
     (->> (if (= (:keypath item) keypath)
            (assoc item k v)
            (update item :children update-in-menu keypath k v))
          (conj acc)))
   []
   menu-items))

(defn show-children
  "Set the children of the menu item found under `keypath` to `show`."
  [db keypath show]
  (update-in db [:menu :items] update-in-menu keypath :show-children show))

(defn update-requires
  "Update the requires of menu item found under `keypath` to `new-requires`."
  [db keypath new-requires]
  (update-in db [:menu :items] update-in-menu keypath :requires new-requires))

;; NOTE: The `show-children` key is needed below because we use this data
;; structure to generate the list of all potential app (menu-based) routes.
;; Showing the routes is then a matter of toggling the `show-children` value.
(def menu
  (with-keypaths
    [{:key      :overview
      :children [{:key   :start
                  :label "Get Started"}]}
     {:key      :admin
      :label    "Administrative"
      :children [{:key   :emergency
                  :label "Emergency Contact"}]}
     {:key      :services
      :label    "Services"
      :children [{:key   :moving
                  :save  true
                  :label "Moving Assistance"}
                 {:key      :storage
                  :requires #{:services/moving}
                  :label    "Storage Options"}
                 {:key      :cleaning
                  :label    "Cleaning &amp; Laundry"
                  :requires #{:services/storage}}
                 {:key      :upgrades
                  :label    "Room Upgrades"
                  :requires #{:services/cleaning}}]}
     {:key      :deposit
      :label    "Security Deposit"
      :children [{:key           :method
                  :label         "Payment Method"
                  :show-children false
                  :children      [{:key      :bank
                                   :requires #{:deposit/method}
                                   :label    "Bank Information"}
                                  {:key      :verify
                                   :requires #{:deposit.method/bank}
                                   :label    "Verification"}]}
                 {:key      :pay
                  :requires #{:deposit/method}
                  :label    "Make Payment"}]}
     {:key      :finish
      :children [{:key      :review
                  :label    "Review &amp; Complete"
                  :requires #{:deposit/pay
                              :admin/emergency
                              :services/moving
                              :services/storage
                              :services/cleaning
                              :services/upgrades}}]}]))

;; =============================================================================
;; Default Db
;; =============================================================================

(def default-value
  {:menu          {:active   :overview/start
                   :default  :overview/start
                   :items    menu
                   :complete #{}}
   ;; Starts off as being bootstrapped
   :bootstrapping true
   ;; Always complete, since there's nothing to do
   ;; :overview/start   {:complete true}
   })

(defn can-navigate-to?
  [db keypath]
  (let [{:keys [items complete]} (:menu db)
        requirements             (requirements-for keypath items)]
    (or (nil? requirements) (set/subset? requirements complete))))

;; =============================================================================
;; Prompts
;; =============================================================================

(declare post-save next-prompt can-navigate-to?)

(defn- keypath-progress
  "Given the server-side progress (`data`) and the menu, accumulate a map of
  which keys are `:incomplete` and which keys are `:complete`."
  ([menu-items data]
   (keypath-progress menu-items data {:incomplete [] :complete []}))
  ([menu-items data acc]
   (reduce
    (fn [acc {:keys [keypath children] :as item}]
      (let [v   (get data keypath)
            acc (cond
                  (:complete v)          (update acc :complete conj keypath)
                  (false? (:complete v)) (update acc :incomplete conj keypath)
                  :otherwise             acc)]
        (keypath-progress children data acc)))
    acc
    menu-items)))

(defn- active-item
  "Produce the `keypath` of the active menu item."
  [db data]
  (let [{:keys [complete incomplete] :as kp}
        (keypath-progress (get-in db [:menu :items]) data)]
    (if (empty? complete)
      :overview/start
      (or (first incomplete)
          (next-prompt db (last complete))
          (get-in db [:menu :default])))))

(defn- init-prompts
  "Populate the `db` with prompt `data` from the server "
  [db data]
  (reduce
   (fn [acc [k v]]
     (let [acc (-> (assoc acc k v) (post-save k (:data v)))]
       (if (:complete v)
         (update-in acc [:menu :complete] conj k)
         acc)))
   db
   data))

(defn- preprocess-catalogues
  "For all items in catalogues present in `data`, produce the sequence of user
  input fields. Creates a 'synthetic' field type out of the `:variants` when
  present."
  [db data]
  (letfn [(-variants->fields [{:as item :keys [fields variants]}]
            (if (empty? variants)
              item
              (let [variants (sort-by :price < variants)]
                (-> (update item :fields conj {:type     :variants
                                               :key      :variant
                                               :variants variants})
                    (assoc :variants variants)))))]
    (reduce
     (fn [acc [k v]]
       (if (get-in v [:data :catalogue])
         (update-in acc [k :data :catalogue :items] #(map -variants->fields %))
         acc))
     db
     data)))

(defn- init-active-item
  "Set the menu's active item."
  [db data]
  (assoc-in db [:menu :active] (active-item db data)))

(defn bootstrap
  "Bootstrap the application database `db` with server-side `data`."
  [db data]
  (-> (init-prompts db data)
      (preprocess-catalogues data)
      (init-active-item data)))

;; =============================================================================
;; Save

;; NOTE: We can effectively "disable" a step by giving it an unsatisfiable
;; requirement, /e.g./ `::disabled`
(defn- disable-steps
  "Disable all steps found under `keypaths` when the first keypath in `keypaths`
  is `complete`."
  [db & keypaths]
  (let [keypath (first keypaths)]
    (reduce
     #(let [complete (get-in db [keypath :complete])]
        (if complete
          (update-requires %1 %2 #{::disabled})
          %1))
     db
     keypaths)))

;; =====================================
;; Pre

(defmulti pre-save (fn [db keypath] keypath))

(defmethod pre-save :default [db _] db)

;; =====================================
;; Post

(defmulti post-save (fn [db keypath result] keypath))

(defmethod post-save :default [db _ _] db)

(defmethod post-save :deposit/method [db keypath result]
  (let [method   (:method result)
        requires (if (= "ach" method) #{:deposit.method/verify} #{:deposit/method})]
    (-> (show-children db :deposit/method (= "ach" method))
        (update-requires :deposit/pay requires))))

(defmethod post-save :deposit.method/bank [db keypath result]
  (disable-steps db :deposit.method/bank :deposit/method))

(defmethod post-save :deposit.method/verify [db keypath result]
  (disable-steps db :deposit.method/verify))

(defmethod post-save :deposit/pay [db keypath result]
  (disable-steps db :deposit/pay :deposit/method))

;; =============================================================================
;; Advancement

(defmulti can-advance? :keypath)

(defmethod can-advance? :default [_]
  true)

(defmethod can-advance? :admin/emergency [prompt]
  (let [{:keys [first-name last-name phone-number]} (:data prompt)]
    (not (or (string/blank? first-name)
             (string/blank? last-name)
             (string/blank? phone-number)))))

(defmethod can-advance? :deposit/method
  [{:keys [data]}]
  (#{"ach" "check"} (:method data)))

(defmethod can-advance? :deposit.method/bank [prompt]
  (let [{:keys [name account-number routing-number]} (:data prompt)]
    (not (or (string/blank? name)
             (string/blank? account-number)
             (string/blank? routing-number)))))

(defmethod can-advance? :deposit.method/verify [prompt]
  (let [{:keys [amount-1 amount-2]} (:data prompt)]
    (and (> amount-1 0) (> amount-2 0))))

(defmethod can-advance? :deposit/pay [{data :data}]
  (:method data))

(defmethod can-advance? :services/moving [{data :data}]
  (let [{:keys [furniture mattress date time]} data]
    (or (and (false? furniture) (false? mattress))
        (and mattress date time)
        (and furniture date time))))

;; =============================================================================

(defmulti ^:private next-prompt*
  (fn [db keypath] keypath))

(defmethod next-prompt* :default [db keypath]
  (get-in db [:menu :default]))

(defmethod next-prompt* :overview/start [_ _]
  :admin/emergency)

(defmethod next-prompt* :admin/emergency [_ _]
  :services/moving)

(defmethod next-prompt* :services/moving [_ _]
  :services/storage)

(defmethod next-prompt* :services/storage [_ _]
  :services/cleaning)

(defmethod next-prompt* :services/cleaning [_ _]
  :services/upgrades)

(defmethod next-prompt* :services/upgrades [_ _]
  :deposit/method)

(defmethod next-prompt* :deposit/method [db _]
  (let [method (get-in db [:deposit/method :data :method])]
    (if (= method "ach")
      :deposit.method/bank
      :deposit/pay)))

(defmethod next-prompt* :deposit.method/bank [_ _]
  :deposit.method/verify)

(defmethod next-prompt* :deposit.method/verify [_ _]
  :deposit/pay)

(defmethod next-prompt* :deposit/pay [_ _]
  :finish/review)

(defn next-prompt
  "Given the current app-state (`db`) and current `keypath`, determine the
  keypath for the next prompt."
  [db keypath]
  (let [next (next-prompt* db keypath)]
    (if (can-navigate-to? db next)
      next
      (active-item db db))))

;; =============================================================================
;; Previous

;; NOTE: A return value of `nil` from `previous-prompt` means that there should
;; be no ability to "go back" from the prompt in question.

(defmulti previous-prompt
  "Given the current app-state (`db`) and current `keypath`, determine the
  keypath for the previous prompt."
  (fn [db keypath] keypath))

(defmethod previous-prompt :default [db keypath]
  nil)

(defmethod previous-prompt :admin/emergency [_ _] :overview/start)
(defmethod previous-prompt :services/moving [_ _] :admin/emergency)
(defmethod previous-prompt :services/storage [_ _] :services/moving)
(defmethod previous-prompt :services/cleaning [_ _] :services/storage)
(defmethod previous-prompt :services/upgrades [_ _] :services/cleaning)
(defmethod previous-prompt :deposit/method [_ _] :services/upgrades)
(defmethod previous-prompt :deposit.method/bank [_ _] :deposit/method)

(defmethod previous-prompt :deposit/pay [db _]
  (when (= (get-in db [:deposit/method :data :method]) "check")
    :deposit/method))

;; =============================================================================
;; Can Save

(defn can-save? [_ keypath]
  (boolean (#{:admin/emergency
              :services/moving
              :services/storage
              :services/cleaning
              :services/upgrades} keypath)))
