(ns admin.services.events
  (:require [admin.services.db :as db]
            [admin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]
            [iface.utils.norms :as norms]))

(reg-event-fx
 :services/query
 [(path db/path)]
 (fn [_ _]
   (js/console.log "hey it is the services route")))


(defmethod routes/dispatches :services/list
  [route]
  [[:services/query]])
