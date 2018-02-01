(ns onboarding.prompts.events
  (:require [onboarding.db :as db]
            [re-frame.core :as rf :refer [reg-event-fx
                                          reg-event-db
                                          path]]
            [onboarding.stripe]
            [onboarding.routes :as routes]
            [reagent.core :as r]
            [ajax.core :as ajax]
            [toolbelt.core :as tb]
            [antizer.reagent :as ant]))

;; =============================================================================
;; Prompt-specific Initialization
;; =============================================================================

(defmulti init-prompt (fn [db keypath] keypath))

(defmethod init-prompt :default
  [db _]
  {:db db})

(defmethod init-prompt :services/moving
  [db _]
  (let [move-in (aget js/account "move-in")
        moving  {:commencement move-in
                 :data         {:date move-in}}]
    {:db (-> (assoc-in db [:services/moving :commencement] move-in)
             (update-in [:services/moving :data :date] #(or % move-in)))}))

(defn- enforce-seen
  [db keypath]
  {:db (let [seen (get-in db [keypath :data :seen])]
         (if seen
           db
           (-> (assoc-in db [keypath :data :seen] true)
               (assoc-in [keypath :dirty] true))))})

;; Only enforces that this prompt is seen at least once
(defmethod init-prompt :services/storage [db keypath]
  (enforce-seen db keypath))

(defmethod init-prompt :services/customize [db keypath]
  (enforce-seen db keypath))

(defmethod init-prompt :services/cleaning [db keypath]
  (enforce-seen db keypath))

(defmethod init-prompt :services/upgrades [db keypath]
  (enforce-seen db keypath))

(defmethod init-prompt :finish/review [db keypath]
  (let [db (if (empty? (get-in db [keypath :orders]))
             (assoc-in db [keypath :orders-loading] true)
             db)]
    {:db       db
     :dispatch [:orders/fetch keypath]}))

(reg-event-fx
 :orders/fetch
 (fn [_ [_ keypath]]
   {:http-xhrio {:method          :get
                 :uri             "/api/orders"
                 :response-format (ajax/transit-response-format)
                 :on-success      [:orders.fetch/success]
                 :on-failure      [:orders.fetch/failure keypath]}}))

(reg-event-db
 :orders.fetch/success
 (fn [db [_ {orders :result}]]
   (-> (assoc db :orders/loading false)
       (assoc :orders/list orders)
       (assoc :orders.fetch/error false))))

(reg-event-fx
 :orders.fetch/failure
 (fn [{:keys [db]} [_ keypath error]]
   (tb/log error)
   (ant/message-error {:content "Failed to fetch your orders."})
   {:db (-> (assoc-in db [keypath :orders-loading] false)
            (assoc-in [keypath :error] true))}))

(reg-event-fx
 :prompt/init
 (fn [{:keys [db]} [_ keypath]]
   (init-prompt db keypath)))

;; =============================================================================
;; Navigation
;; =============================================================================

;; =============================================================================
;; Advancement

(def ^:private deposit-modal-content
  (r/as-element
   [:p "By pressing the " [:b "Pay Now"] " button below, I authorize Starcity to
   electronically debit my account and, if necessary, electronically credit my
   account to correct erroneous debits."]))

(def ^:private bank-info-modal-content
  (r/as-element
   [:div
    [:p {:dangerouslySetInnerHTML {:__html "Over the next <b>24-48 hours</b>,
     two small deposits will be made in your account with the statement
     description <b>VERIFICATION</b> &mdash; enter them in the next step to
     verify that you are the owner of this bank account."}}]]))

(defmulti begin-save
  "Used to determine how to proceed after a successful press of the 'Continue'
  button. In most cases we'll simply send the form data directly to the
  server (see the `:default` case), but in some cases other client-side steps
  may need to be performed."
  (fn [db keypath] keypath))

(defmethod begin-save :default [db keypath]
  {:dispatch [:prompt/save keypath (get-in db [keypath :data])]})

(defmethod begin-save :deposit/pay [db keypath]
  (ant/modal-confirm {:title   "Payment Confirmation"
                      :content deposit-modal-content
                      :ok-text "Pay Now"
                      :on-ok   #(rf/dispatch [:prompt/save keypath (get-in db [keypath :data])])})
  {})

(defmethod begin-save :deposit.method/bank [_ keypath]
  (ant/modal-info {:title   "Microdeposit Lag-time"
                   :content bank-info-modal-content
                   :on-ok   #(rf/dispatch [:deposit.method.bank/submit keypath])})
  {})

(reg-event-fx
 :deposit.method.bank/submit
 (fn [{:keys [db]} [_ keypath]]
   (let [{:keys [name routing-number account-number]} (get-in db [keypath :data])]
     {:db (db/pre-save (assoc db :saving true) keypath)
      :stripe.bank-account/create-token
      {:country             "US"
       :currency            "USD"
       :account-holder-type "individual"
       :key                 (.-key js/stripe)
       :account-holder-name name
       :routing-number      routing-number
       :account-number      account-number
       :on-success          [:stripe.bank-account.create-token/success keypath]
       :on-failure          [:stripe.bank-account.create-token/failure]}})))

(reg-event-fx
 :stripe.bank-account.create-token/success
 (fn [_ [_ keypath res]]
   {:dispatch [:prompt/save keypath {:stripe-token (:id res)}]}))

(reg-event-fx
 :stripe.bank-account.create-token/failure
 (fn [{:keys [db]} [_ error]]
   (tb/error "Failed to create Stripe Token:" error)
   (ant/notification-error {:duration    8
                            :message     "Error!"
                            :description "Something went wrong while submitting your bank information. Please check the account and routing number and try again."})
   {:db (assoc db :saving false)}))

(reg-event-fx
 :prompt/continue
 (fn [{:keys [db]} [_ keypath]]
   (if (get-in db [keypath :dirty])
     (begin-save db keypath)
     {:route (routes/path-for (db/next-prompt db keypath))})))

(reg-event-fx
 :prompt/save
 (fn [{:keys [db]} [_ keypath data opts]]
   {:db         (db/pre-save (assoc db :saving true) keypath)
    :http-xhrio {:method          :post
                 :uri             "/api/onboarding"
                 :params          {:step keypath :data (dissoc data :catalogue)}
                 :format          (ajax/transit-request-format)
                 :response-format (ajax/transit-response-format)
                 :on-success      [:prompt.save/success keypath opts]
                 :on-failure      [:prompt.save/failure keypath]}}))

(reg-event-fx
 :prompt.save/success
 (fn [{:keys [db]} [_ keypath {:keys [nav] :or {nav true}} {result :result}]]
   (let [db (-> (assoc db :saving false)
                (assoc-in [keypath :dirty] false)
                (update-in [:menu :complete] conj keypath)
                (assoc-in [keypath :complete] true)
                (db/post-save keypath (:data result)))]
     (if nav
       {:db    db
        :route (routes/path-for (db/next-prompt db keypath))}
       {:db db}))))

(reg-event-fx
 :prompt.save/failure
 (fn [{:keys [db]} [_ keypath error]]
   (tb/error error)
   (if-let [errors (get-in error [:response :errors])]
     (ant/notification-error {:message     "Error!"
                              :description (first errors)})
     (ant/message-error {:description "Yikes! A server-side error was encountered."}))
   (merge {:db (assoc db :saving false)})))

;; =============================================================================
;; Previous

(reg-event-fx
 :prompt/previous
 (fn [{:keys [db]} [_ keypath]]
   {:route (routes/path-for (db/previous-prompt db keypath))}))

;; =============================================================================
;; Updates
;; =============================================================================

(defmulti update-prompt (fn [db keypath k v] keypath))

(defmethod update-prompt :default
  [db keypath k v]
  (assoc-in db [keypath :data k] v))

(reg-event-db
 :prompt/update
 (fn [db [_ keypath k v]]
   (update-prompt (assoc-in db [keypath :dirty] true) keypath k v)))

(reg-event-fx
 :prompt.orders/select
 (fn [{:keys [db]} [_ keypath {:keys [service fields variants] :as item}]]
   (let [defaults {:quantity 1 :desc "" :variants (:id (first variants))}
         init     (reduce #(assoc %1 (:key %2) (get defaults (:type %2))) {} fields)]
     {:dispatch [:prompt.orders/update keypath [service init]]})))

(reg-event-fx
 :prompt.orders/update
 (fn [{:keys [db]} [_ keypath [service params]]]
   (let [orders   (-> (get-in db [keypath :data :orders])
                      (assoc service params))]
     {:dispatch [:prompt/update keypath :orders orders]})))

(reg-event-fx
 :prompt.orders/remove
 (fn [{:keys [db]} [_ keypath service]]
   (let [orders (-> (get-in db [keypath :data :orders])
                    (dissoc service))]
     {:dispatch [:prompt/update keypath :orders orders]})))

;; =============================================================================
;; Orders
;; =============================================================================

(reg-event-fx
 :order/delete
 (fn [{:keys [db]} [_ order-id]]
   {:db         (assoc db :orders/loading true)
    :http-xhrio {:method          :delete
                 :uri             (str "/api/orders/" order-id)
                 :format          (ajax/transit-request-format)
                 :response-format (ajax/transit-response-format)
                 :on-success      [:order.delete/success order-id]
                 :on-failure      [:order.delete/failure]}}))

(reg-event-fx
 :order.delete/success
 (fn [{:keys [db]} [_ order-id]]
   {:db       (-> (update db :orders/list #(remove (comp #{order-id} :id) %))
                  (assoc :orders/loading false))
    ;; TODO: Fetch a specific step instead
    :dispatch [:app/bootstrap {:show-loading false}]}))

(reg-event-fx
 :order.delete/failure
 (fn [{:keys [db]} [_ error]]
   (tb/error error)
   (ant/message-error {:content "Failed to remove order."})
   {:db (assoc db :orders/loading false)}))

;; =============================================================================
;; Finish/Credit Card
;; =============================================================================

(reg-event-db
 :finish.review.cc/toggle
 (fn [db [_ show]]
   (assoc db :finish.review.cc-modal/showing show)))

(defn- finish-req [& [token]]
  (let [req {:method          :post
             :uri             "/api/onboarding/finish"
             :format          (ajax/transit-request-format)
             :response-format (ajax/transit-response-format)
             :on-success      [:finish.review.submit/success]
             :on-failure      [:finish.review.submit/failure]}]
    (if token
      (assoc req :params {:token token})
      req)))

(reg-event-fx
 :finish.review/submit!
 (fn [{:keys [db]} [_ token]]
   {:db         (assoc db :finishing true)
    :http-xhrio (finish-req token)}))

(reg-event-fx
 :finish.review.submit/success
 (fn [{:keys [db]} [_ result]]
   (tb/log result)
   {:db       (assoc db :finishing false)
    :dispatch [::reload]}))

(reg-event-fx
 :finish.review.submit/failure
 (fn [{:keys [db]} [_ error]]
   (tb/error error)
   (ant/notification-error {:message     "Whoops!"
                            :description "Something went wrong. Please try again."})
   {:db (assoc db :finishing false)}))

(reg-event-fx
 ::reload
 (fn [_ _]
   (.reload js/window.location)))

;; =============================================================================
;; Stripe
;; =============================================================================

(reg-event-fx
 :stripe/load-scripts
 (fn [_ [_ version]]
   {:load-scripts [(str "https://js.stripe.com/" (or version "v2") "/")]}))
