(ns cards.iface.components.services
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]])
  (:require [devcards.core]
            [iface.components.form :as form]
            [iface.components.services :as services]
            [reagent.core :as r]))


(defcard-rg add-service-form
  "
## Default Add Service Form
"
  (fn [data _]
    [services/service-modal
     {:action "Add"
      }]))
