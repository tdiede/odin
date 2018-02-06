(ns onboarding.stripe
  (:require [re-frame.core :refer [reg-fx dispatch]]))


(reg-fx
 :stripe.bank-account/create-token
 (fn [{:keys [key on-success on-failure] :as args}]
   (.setPublishableKey js/Stripe key)
   (.createToken js/Stripe.bankAccount
                 #js {:country             (:country args)
                      :currency            (:currency args)
                      :routing_number      (:routing-number args)
                      :account_number      (:account-number args)
                      :account_holder_name (:account-holder-name args)
                      :account_holder_type (:account-holder-type args)}
                 (fn [status response]
                   (let [response (js->clj response :keywordize-keys true)]
                     (if-let [e (:error response)]
                       (dispatch (conj on-failure e))
                       (dispatch (conj on-success response))))))))
