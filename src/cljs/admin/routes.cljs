(ns admin.routes
  (:require [accountant.core :as accountant]
            [bidi.bidi :as bidi]
            [re-frame.core :refer [dispatch reg-fx]]
            [toolbelt.core :as tb]))


(def app-routes
  ["/"
   [["/accounts"
     [["" :accounts]

      [["/" :account-id] [["" :account]
                          ["/application" :account/application]
                          ["/licenses" :account/licenses]
                          ["/notes" :account/notes]]]]]
    ["/properties"
     [["" :properties]
      [["/" :property-id] [["" :property]
                           ["/units"
                            [[["/" :unit-id] :unit]]]]]]]

    [true :home]]])
