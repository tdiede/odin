(ns odin.profile.contact.events
  (:require [odin.profile.db :as db]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [odin.routes :as routes]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :profile/contact [route]
  [[:profile/fetch-account (get-in route [:requester :id])]])


(reg-event-db
 :profile.contact.info/update!
 [(path db/path)]
 (fn [db [_ k v]]
   ;;(tb/log (:new-account db))
   (assoc-in db [:new-account k] v)))

(reg-event-db
 :profile.contact.info/update-emergency-contact!
 [(path db/path)]
 (fn [db [_ k v]]
   ;;(tb/log (:new-account db))
   (assoc-in db [:new-account :emergency_contact k] v)))
