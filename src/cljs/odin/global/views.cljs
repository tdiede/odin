(ns odin.global.views
  (:require [odin.components.notifications :as notification :refer [banner-global]]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe
                                   dispatch]]
            [toolbelt.core :as tb]))


(defn messages
  []
  (let [messages (subscribe [:global/messages])]
    [:div
      (for [{:keys [text route level]} @messages]
        ^{:key text} [banner-global text level route])]))
