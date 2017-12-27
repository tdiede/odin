(ns odin.accounts.admin.entry.views.actions
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [antizer.reagent :as ant]))


(defmulti actions :role)


(defmethod actions :default [_] [:div])


(defmethod actions :applicant [_]
  [:div.columns
   [:div.column
    [ant/button {:size :large :type :primary}
     "Approve"]]])
