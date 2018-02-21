(ns admin.services.catalogs.views
  (:require [admin.content :as content]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [admin.routes :as routes]))


;; ==============================================================================
;; subview ======================================================================
;; ==============================================================================

(defn subview []
  [:h1 "This is the catalogs subview!"])
