(ns odin.orders.admin.entry.views.progress
  (:require [odin.utils.formatters :as format]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [antizer.reagent :as ant]
            [clojure.string :as string]
            [toolbelt.core :as tb]
            [iface.loading :as loading]
            [odin.components.order :as order]))


;; =============================================================================
;; Helpers
;; =============================================================================


(defn- service-source [order]
  (get-in order [:account :service_source :id]))


(defn- status-history [history]
  (->> (group-by :v (:order/status history))
       (reduce
        (fn [acc [k v]]
          (assoc acc k (first (sort-by :t > v))))
        {})))


;; =============================================================================
;; Actions
;; =============================================================================


(defmulti step-action (fn [step-status order] step-status))

(defmethod step-action :default [step-status _]
  [:span "TODO: not implemented: " (name step-status)])


;; =============================================================================
;; Place Order


(defn- place-order-modal [order]
  (let [is-visible (subscribe [:modal/visible? :admin.order/place])
        is-loading (subscribe [:loading? :admin.order/place!])
        form       (r/atom {:projected-fulfillment nil
                            :send-notification     true})]
    (fn [order]
      [ant/modal
       {:title     "Place Order"
        :visible   @is-visible
        :on-cancel #(dispatch [:modal/hide :admin.order/place])
        :footer    [(r/as-element
                     ^{:key "cancel"}
                     [ant/button
                      {:on-click #(dispatch [:modal/hide :admin.order/place])
                       :size     :large}
                      "Cancel"])
                    (r/as-element
                     ^{:key "place"}
                     [ant/button
                      {:type     :primary
                       :size     :large
                       :loading  @is-loading
                       :on-click #(dispatch [:admin.order/place! order @form])}
                      "Place"])]}
       [:p "When the order is \"placed\" it can be considered " [:i "in-progress"]
        "; this means that the order can no longer be canceled by the member."]
       [ant/form-item {:label "Projected Fulfillment Date"
                       :help  "This is used to give the member an idea of when they can expect their order to be fulfilled."}
        [ant/date-picker
         {:style         {:width "100%"}
          :showTime      #js {:use12Hours          true
                              :disabledSeconds     (constantly (range 61))
                              :hideDisabledOptions true}
          :on-change     #(swap! form assoc :projected-fulfillment %)
          :disabled-date #(and % (< (.valueOf %) (.valueOf (.subtract (js/moment.) 1 "days"))))}]]
       [ant/checkbox {:checked   (:send-notification @form)
                      :on-change #(swap! form assoc :send-notification (.. % -target -checked))}
        "Should we notify the member that his/her order has been placed?"]])))


(defmethod step-action :placed [_ {:keys [status] :as order}]
  [:div
   [place-order-modal order]
   [:p.fs1.mb1
    "Indicates that the order is " [:i "in-progress"] ", and cannot be canceled by the member."]
   [ant/button
    {:size     :small
     :disabled (not= status :pending)
     :on-click #(dispatch [:modal/show :admin.order/place])}
    "Place"]])


;; =============================================================================
;; Fulfill Order


(defn- charge-tooltip [{:keys [status price] :as order}]
  (cond
    (nil? (service-source order))
    (format/format "%s has no payment method linked for services."
                   (get-in order [:account :name]))

    (nil? price)
    "This order has no price; cannot charge."))


(defn- can-charge? [{:keys [price] :as order}]
  (and (some? (service-source order)) (some? price)))


(defn- fulfill-order-modal [order form]
  (let [is-visible (subscribe [:modal/visible? :admin.order/fulfill])
        is-loading (subscribe [:loading? :admin.order/fulfill!])]
    (fn [order form]
      [ant/modal
       {:title     "Fulfill Order"
        :visible   @is-visible
        :on-cancel #(dispatch [:modal/hide :admin.order/fulfill])
        :footer    [(r/as-element
                     ^{:key "cancel"}
                     [ant/button
                      {:on-click #(dispatch [:modal/hide :admin.order/fulfill])
                       :size     :large}
                      "Cancel"])
                    (r/as-element
                     ^{:key "fulfill"}
                     [ant/button
                      {:type     :primary
                       :loading  @is-loading
                       :size     :large
                       :on-click #(dispatch [:admin.order/fulfill! order @form])}
                      (if (:process-charge @form)
                        [:span {:dangerouslySetInnerHTML {:__html "Fulfill & Charge"}}]
                        "Fulfill")])]}

       [ant/form-item {:label "Actual Fulfillment Date"
                       :help  "The date that this order was fulfilled."}
        [ant/date-picker
         {:style         {:width "100%"}
          :showTime      #js {:use12Hours          true
                              :disabledSeconds     (constantly (range 61))
                              :hideDisabledOptions true}
          :default-value (:actual-fulfillment @form)
          :on-change     #(swap! form assoc :actual-fulfillment %)
          :allow-clear   false}]]
       [ant/tooltip {:title (charge-tooltip order)}
        [ant/checkbox {:checked   (:process-charge @form)
                       :disabled  (not (can-charge? order))
                       :on-change #(swap! form assoc :process-charge (.. % -target -checked))}
         "Charge the order now."]]
       [ant/checkbox {:checked   (:send-notification @form)
                      :on-change #(swap! form assoc :send-notification (.. % -target -checked))}
        "Should we notify the member that his/her order has been fulfilled?"]])))


(defn- fulfill-action [{:keys [status price] :as order}]
  (let [form (r/atom {:actual-fulfillment (js/moment.)
                      :send-notification  true
                      :process-charge     (and (some? (service-source order))
                                               (some? price))})]
    (fn [{:keys [status] :as order}]
      [:div
       [fulfill-order-modal order form]
       [:p.fs1.mb1 "Indicates that the order has been fulfilled."]
       [ant/button
        {:size     :small
         :type     :primary
         :disabled (#{:charged :fulfilled :processing :canceled} status)
         :on-click #(dispatch [:modal/show :admin.order/fulfill])}
        "Fulfill"]])))


(defmethod step-action :fulfilled [_ order]
  [fulfill-action order])


;; =============================================================================
;; Charge Order


(defn- charged-modal [order]
  (let [is-visible (subscribe [:modal/visible? :admin.order/charge])
        is-loading (subscribe [:loading? :admin.order/charge!])]
    [ant/modal
     {:visible   @is-visible
      :on-cancel #(dispatch [:modal/hide :admin.order/charge])
      :class     "ant-confirm"
      :footer    nil}
     [:div.ant-confirm-body-wrapper
      [:div.ant-confirm-body
       [ant/icon {:type  "exclamation-circle"
                  :style {:color "#ffbf00"}}]
       [:span.ant-confirm-title "Are you sure?"]
       [:div.ant-confirm-content "This cannot be undone!"]
       [:div.ant-confirm-btns
        [ant/button
         {:on-click #(dispatch [:modal/hide :admin.order/charge])}
         "Cancel"]
        [ant/button
         {:type     :primary
          :loading  @is-loading
          :on-click #(dispatch [:admin.order/charge! order])}
         "Yes, process the charge"]]]]]))


(defn- charged-action [{:keys [status] :as order}]
  [:div
   [charged-modal order]
   [:p.fs1.mb1
    {:dangerouslySetInnerHTML
     {:__html "Charge the member for this order&mdash;order must be fulfilled."}}]
   [ant/tooltip
    {:title (when (= status :fulfilled) (charge-tooltip order))}
    [ant/button
     {:size     :small
      :loading  (= :processing status)
      :disabled (not (and (= status :fulfilled) (can-charge? order)))
      :on-click #(dispatch [:modal/show :admin.order/charge])}
     (if (= :processing (:status order)) "Processing..." "Charge")]]])


(defmethod step-action :charged [_ order]
  [charged-action order])


;; =============================================================================
;; Failed/Retry Order


;; (defmethod step-action :failed [_ order]
;;   [:div
;;    ])


;; =============================================================================
;; Cancel Order


(defn- cancel-order-modal [order]
  (let [is-visible (subscribe [:modal/visible? :admin.order/cancel])
        is-loading (subscribe [:loading? :admin.order/cancel!])
        form       (r/atom {:send-notification false})]
    (fn [order]
      [ant/modal
       {:title     "Cancel Order"
        :visible   @is-visible
        :on-cancel #(dispatch [:modal/hide :admin.order/cancel])
        :footer    [(r/as-element
                     ^{:key "cancel"}
                     [ant/button
                      {:on-click #(dispatch [:modal/hide :admin.order/cancel])
                       :size     :large}
                      "Cancel"])
                    (r/as-element
                     ^{:key "cancel-order"}
                     [ant/button
                      {:type     :danger
                       :size     :large
                       :loading  @is-loading
                       :on-click #(dispatch [:admin.order/cancel! order @form])}
                      "Cancel Order"])]}

       [ant/checkbox {:checked   (:send-notification @form)
                      :on-change #(swap! form assoc :send-notification (.. % -target -checked))}
        "Should we notify the member that his/her order has been canceled?"]])))


(defmethod step-action :canceled [_ order]
  [:div
   [cancel-order-modal order]
   [:p.fs1.mb1
    {:dangerouslySetInnerHTML
     {:__html "Cancel this order&mdash;order must be pending or placed."}}]
   [ant/button
    {:size     :small
     :type     :danger
     :disabled (#{:fulfilled :charged} (:status order))
     :on-click #(dispatch [:modal/show :admin.order/cancel])}
    "Cancel"]])


;; =============================================================================
;; Progress Helpers
;; =============================================================================


(defn- status-idx [status]
  (case status
    :pending   0
    :placed    1
    :fulfilled 2
    :charged   3
    :canceled  4
    0))


(defn- steps-status [order-status]
  (case order-status
    :charged  :finish
    :canceled :error
    :process))


(defn- full-status [status]
  (keyword "order.status" (name status)))


(defn- completed-desc [status history]
  (when-let [{:keys [t account]} (get (status-history history) (full-status status))]
    (when (some? t)
      (conj
       [:span "set at " [:b (format/date-time-short t)] " by "]
       (if-some [{n :name} account] [:a n] "system")))))


(defn- step-desc
  [step-status order history]
  (if-let [desc (completed-desc step-status history)]
    desc
    [step-action step-status order]))


(defn- step-status
  "Given the `status` of the rendered step and the order, determine the ant
  `status` it should have."
  [status order history]
  (let [history (status-history history)]
    (cond
      (and (= status :canceled) (= (:status order) :canceled))
      "error"

      (some? (get-in history [(full-status status) :t]))
      "finish"

      :otherwise
      "wait")))


(defn progress [{:keys [id status] :as order}]
  (let [statuses        [:pending :placed :fulfilled :charged #_:failed :canceled]
        history         (subscribe [:history id])
        order-loading   (subscribe [:loading? :order/fetch])
        history-loading (subscribe [:loading? :history/fetch])]
    [:div
     [ant/spin (tb/assoc-when
                {:tip      "Fetching progress..."
                 :spinning (or @order-loading @history-loading)}
                :delay (when (some? order) 1000))
      [ant/steps {:current   (status-idx status)
                  :status    (steps-status status)
                  :direction "vertical"}
       (doall
        (for [status statuses]
          ^{:key status}
          [ant/steps-step {:title       (-> status name string/capitalize)
                           :icon        (order/status-icon status)
                           :description (r/as-component [step-desc status order @history])
                           :status      (step-status status order @history)}]))]]]))
