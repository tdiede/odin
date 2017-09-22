(ns iface.tooltip
  (:require [antizer.reagent :as ant]))


(defn info
  ([text] (info-tooltip text "top"))
  ([text positioning]
   [:span.info-tooltip
    [ant/tooltip {:title     text
                  :placement positioning}
     [ant/icon {:type "question-circle-o"}]]]))
