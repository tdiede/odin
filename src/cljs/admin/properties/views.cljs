(ns admin.properties.views
  (:require [admin.content :as content]
            [antizer.reagent :as ant]
            [cljs.core.match :refer-macros [match]]
            [clojure.string :as string]
            [iface.utils.formatters :as format]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [iface.typography :as typography]
            [admin.routes :as routes]
            [iface.loading :as loading]))

;; What do we want to be able to see in a property's detail view?

;; 1. List of units:
;;   - Who lives in them (avatar, name, email)
;;   - What the unit rates are per-term
;;   - Searchable by occupant name or room number
;;   - Can edit unit rates
;;
;; 2. Building financials:
;;   - Rent overview (payment totals paid, pending, unpaid)
;;   - Premium service income
;;   - Security deposit overview
;;   - Operational fees for rent & services modifications
;;   - Default rates per term modifiable



;; ==============================================================================
;; components ===================================================================
;; ==============================================================================


(defn property-card
  "Display a property as a card form."
  [{:keys [name cover-image-url href is-loading]
    :or   {is-loading false, href "#"}
    :as   props}]
  [ant/card {:class   "is-flush"
             :loading is-loading}
   [:div.card-image
    [:figure.image
     [:a {:href href}
      [:img {:src    cover-image-url
             :style {:height "196px"}}]]]]

   [:div.card-content
    [:div.content
     [:h5.title.is-5 name]
     [:a {:href href}
      "Details "
      [ant/icon {:type "right"}]]]]])


(defn- unit-list-item
  [active {:keys [id name href occupant on-click]}]
  [:a.unit-list-item
   (tb/assoc-when
    {}
    :on-click (when-some [f on-click] #(f id))
    :href href)
   [:li
    {:class (when (= id active) "is-active")}
    [:span.unit-name name]
    [:span.divider {:dangerouslySetInnerHTML {:__html "&#10072;"}}]
    (if-let [occupant occupant]
      [:span.occupied
       [ant/icon {:type :home}]
       " for" [:span.occupant-name (:name occupant)] " until "
       [:span.date (-> occupant :ends format/date-short-num)]]
      [:span.unoccupied "vacant"])]])


(defn- matches-query? [q {:keys [name occupant]}]
  (let [s (string/lower-case (str name " " (:name occupant)))]
    (string/includes? s (string/lower-case q))))


(defn units-list
  "Display a list of units. Can provide the following:

  - `units`: the units to render
  - `page-size`: number of units to show per-page
  - `active`: the `id` of the active unit
  - `on-click`: callback that will be passed the id of the clicked unit"
  [{:keys [units page-size active on-click]
    :or   {page-size 10, active false}}]
  (let [state (r/atom {:current 1 :q ""})]
    (fn [{:keys [units page-size active on-click]
         :or   {page-size 10, active false}}]
      (let [{:keys [current q]} @state
            units'               (->> (drop (* (dec current) page-size) units)
                                     (take (* current page-size))
                                     (filter (partial matches-query? q)))]
        [:div.admin-property-unit-list
         [ant/input {:placeholder "search units by name or occupant"
                     :class       "search-bar"
                     :on-change   #(swap! state assoc :q (.. % -target -value))
                     :suffix (r/as-element [ant/icon {:type :search}])}]
         [:ul.unit-list
          (if (empty? units')
            [:div.has-text-centered {:style {:margin "24px 0"}} "No matches"]
            (map-indexed
             #(with-meta
                [unit-list-item active (assoc %2 :on-click on-click)]
                {:key %1})
             units'))]
         [ant/pagination
          {:size      "small"
           :current   current
           :total     (count units)
           :showTotal (fn [total] (str total " units"))
           :on-change #(swap! state assoc :current %)}]]))))


(defn rate-input
  "An input for manipulating the rate for a given term."
  [{:keys [term value on-change]}]
  [ant/form-item {:label (str term " Month Rate")}
   [ant/input-number {:formatter #(string/replace (str "$ " %) #"\B(?=(\d{3})+(?!\d))" ",")
                      :parser    #(string/replace % #"\$\s?|(,*)" "")
                      :value     value
                      :on-change on-change}]])


(defn rate-form
  "A form for manipulating the `rates` for multiple terms, for e.g. a property or unit."
  [{:keys [rates on-change can-submit is-loading]
    :or   {on-change  (constantly nil)
           can-submit true}
    :as   opts}]
  [ant/card
   [ant/form
    [:div.columns
     (doall
      (for [{:keys [id term rate]} rates]
        ^{:key id}
        [:div.column
         [rate-input {:value     rate
                      :term      term
                      :on-change (fn [amount]
                                   (on-change id amount))}]]))]
    [ant/button
     {:type     :primary
      :disabled (not can-submit)
      :loading  is-loading}
     "Save"]]])


;; ==============================================================================
;; entry layout =================================================================
;; ==============================================================================


(defn menu [property-id selected]
  [ant/menu {:mode          :horizontal
             :selected-keys [selected]}
   [ant/menu-item {:key :entry}
    [:a {:href (routes/path-for :properties/entry :property-id property-id)}
     "Overview"]]
   [ant/menu-item {:key :units}
    [:a {:href (routes/path-for :properties.entry/units :property-id property-id)}
     "Units"]]])


(defmethod content/view :properties [{:keys [params path]}]
  (let [property (subscribe [:property (tb/str->int (:property-id params))])]
    [:div
     (typography/view-header (:name @property))
     [menu (:id @property) (last path)]
     (let [path (vec (rest path))]
       (match [path]
         [[:entry]] [:p "entry view"]
         [[:entry :units]] [:p "units view"]
         :else [:p "unmatched"]))]))


;; ==============================================================================
;; list layout ==================================================================
;; ==============================================================================


(defmethod content/view :properties/list [_]
  (let [properties (subscribe [:properties/list])
        is-loading (subscribe [:ui/loading? :properties/query])]
    [:div
    (typography/view-header "Communities" "Manage and view our communities.")
     (if @is-loading
       (loading/fullpage "Loading properties...")
       [:div.columns
       (doall
        (for [{:keys [id name cover_image_url units]} @properties]
          ^{:key id}
          [:div.column.is-4
           [property-card
            {:name            name
             :cover-image-url cover_image_url
             :href            (routes/path-for :properties/entry :property-id id)}]]))])]))
