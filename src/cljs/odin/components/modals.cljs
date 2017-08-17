(ns odin.components.modals
  (:require [odin.l10n :as l10n]
            [toolbelt.core :as tb]
            [antizer.reagent :as ant]
            [reagent.core :as r]))


;; TODO: Make this a generalized modal that can be invoked as a component
(defn modal []
  (let [modal-visible (r/atom false)]
    (fn []
      [ant/modal {:title "Modal title"
                  :visible @modal-visible
                  :on-ok #(reset! modal-visible false)
                  :on-cancel #(reset! modal-visible false)}
       (r/as-element [:p "Modal contents."])])))


(defn close-modal
  "Hides the modal with a given className."
  [className]
  (let [modalElement (aget (.querySelectorAll js/document (str "." className)) 0)]
    (.setTimeout js/window #(.remove (.-classList modalElement) "is-active") 0.2)))


(defn show-modal
  "Reveals the modal with a given className."
  [className]
  (let [modalElement (aget (.querySelectorAll js/document (str "." className)) 0)]
    (.setTimeout js/window #(.add (.-classList modalElement) "is-active") 0.2)))


(defn build
  "Constructs a modal and populates it with the provided view content."
  [view className]
  [:div.modal {:class className}
   [:div.modal-background {:on-click #(close-modal className)}]
   [:div.modal-card
    [:section.modal-card-body
     (view)]]
   [:button.modal-close.is-large {:on-click #(close-modal className)}]])
