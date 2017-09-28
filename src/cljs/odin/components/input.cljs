(ns odin.components.input
  (:require [antizer.reagent :as ant]
            [odin.l10n :as l10n]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [toolbelt.core :as tb]))

(defn ios-checkbox []
  (let [test-checked (r/atom false)]
   [:div.checkbox-group
    [:input.ios-toggle {:type      "checkbox"
                        :name      "autopay-toggle"
                        :id        "autopay-toggle"
                        :value     (if (= @test-checked true) "on" "off")
                        :on-change #(swap! test-checked not)}]
    [:label.checkbox-label {:for      "autopay-toggle"
                            :data-off ""
                            :data-on  ""}]]))



(defn pretty-select [options]
  [:div.select
   [:select
    (for [option options]
      (let [id (get option :id)]
        ^{:key id}
        [:option {:value id} (:name option)]))]])
