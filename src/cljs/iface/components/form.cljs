(ns iface.components.form
  (:require [cljsjs.moment]
            [antizer.reagent :as ant]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]))


;; ==============================================================================
;; time picker component ========================================================
;; ==============================================================================


(defn number->time [n]
  (cond-> (js/moment)
    (= n (Math/floor n))    (.minute 0)
    (not= n (Math/floor n)) (.minute (* 60 (mod n 1)))
    true                    (.hour (Math/floor n))
    true                    (.second 0)))


(defn moment->hhmm
  [m]
  (.format m "h:mm A"))


(defn generate-time-range
  [start end interval]
  (let [time-range (range start end (float (/ interval 60)))]
    (map number->time time-range)))


(defn time-picker
  "time picker takes start and end times in military hours and interval in minutes
  if none are supplied it will default to 9am - 5pm at 30 mins intervals"
  [{:keys [on-change start end interval placeholder]
    :or   {on-change identity
           start     9
           end       17
           interval  30}
    :as   props}]
  (let [moments        (generate-time-range start end interval)
        fmts           (map moment->hhmm moments)
        lookup         (zipmap fmts moments)
        reverse-lookup (zipmap (map str moments) fmts)]
    [ant/select (-> (assoc props :on-change (comp on-change lookup))
                    (update :value #(when-let [x %]
                                      (reverse-lookup (str x)))))
     (doall
      (for [t fmts]
        ^{:key t} [ant/select-option {:value t} t]))]))


;; ==============================================================================
;; add credit card form =========================================================
;; ==============================================================================


(defn- handle-card-errors [container event]
  (if-let [error (.-error event)]
    (aset container "textContent" (.-message error))
    (aset container "textContent" "")))


(def card-style
  {:base    {:fontFamily "'Work Sans', Helvetica, sans-serif"}
   :invalid {:color "#ff3860" :iconColor "#ff3860"}})


(defn credit-card [{:keys [is-submitting on-add-card on-click]}]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [st         (js/Stripe (.-key js/stripe))
            elements   (.elements st)
            card       (.create elements "card" #js {:style (clj->js card-style)})
            errors     (.querySelector (r/dom-node this) "#card-errors")
            submit-btn (.querySelector (r/dom-node this) "#submit-btn")]
        (.mount card "#card-element")
        (.addEventListener card "change" (partial handle-card-errors errors))
        (->> (fn [_]
               (let [p (.createToken st card)]
                 (.then p (fn [result]
                            (if-let [error (.-error result)]
                              (aset errors "textContent" (.-message error))
                              (on-add-card (aget (aget result "token") "id")))))))
             (.addEventListener submit-btn "click"))))
    :reagent-render
    (fn []
      [:div
       [:div {:style {:background-color "#f7f8f9"
                      :padding          24
                      :border-radius    4
                      :border           "1px solid #eeeeee"}}
        [:label.label.is-small {:for "card-element"} "Credit or debit card"]
        [:div#card-element]
        [:p#card-errors.help.is-danger]]
       [:hr]
       [:div.align-right
        [ant/button
         {:on-click on-click
          :size     :large}
         "Cancel"]
        [ant/button
         {:type    :primary
          :size    :large
          :id      "submit-btn"
          :loading is-submitting}
         "Add Credit Card"]]])}))
