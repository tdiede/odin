(ns odin.accounts.admin.entry.events
  (:require [odin.accounts.admin.entry.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-fx
                                   reg-event-db
                                   path]]
            [toolbelt.core :as tb]
            [odin.utils.formatters :as format]))


(defmethod routes/dispatches :admin.accounts/entry [route]
  (let [account-id (tb/str->int (get-in route [:params :account-id]))]
    [[:account/fetch account-id]
     [:payments/fetch account-id]
     [:payment-sources/fetch account-id]
     [:admin.accounts.entry/fetch-notes account-id]]))


(reg-event-db
 :admin.accounts.entry/select-tab
 [(path db/path)]
 (fn [db [_ tab]]
   (assoc db :tab tab)))


;; fetch units ==================================================================


(reg-event-fx
 :admin.accounts.entry.approval/fetch-units
 (fn [_ [k property-id]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:units {:params {:property (tb/str->int property-id)}}
                 [:id :code :number
                  [:occupant [:name
                              [:active_license [:ends]]]]]]]
               :on-success [::fetch-units-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-units-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:db       (assoc db :units (get-in response [:data :units]))
    :dispatch [:loading k false]}))


;; approve ======================================================================


(reg-event-fx
 :admin.accounts.entry/approve
 [(path db/path)]
 (fn [{db :db} [k application-id {:keys [move-in unit term]}]]
   {:dispatch [:loading k true]
    :graphql  {:mutation
               [[:approve_application {:application application-id
                                       :params      {:move_in (.toISOString move-in)
                                                     :unit    (tb/str->int unit)
                                                     :term    (tb/str->int term)}}
                 [:id [:account [:id]]]]]
               :on-success [::approve-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::approve-success
 [(path db/path)]
 (fn [_ [_ k response]]
   (tb/log response)
   {:dispatch-n
    [[:loading k false]
     [:modal/hide :admin.accounts.approval/modal]
     [:account/fetch (get-in response [:data :approve_application :account :id])]]}))


;; notes ========================================================================


(reg-event-db
 :admin.accounts.entry.create-note/update
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:create-form k] v)))


(reg-event-fx
 :admin.accounts.entry/create-note!
 [(path db/path)]
 (fn [_ [k account-id {:keys [subject content notify]}]]
   {:dispatch [:loading k true]
    :graphql  {:mutation
               [[:create_note {:params {:account account-id
                                        :subject subject
                                        :content (format/escape-newlines content)
                                        :notify  notify}}
                 [:id]]]
               :on-success [::create-note-success k account-id]
               :on-failure [:graphql/failure k]}}))

(reg-event-fx
 ::create-note-success
 [(path db/path)]
 (fn [_ [_ k account-id response]]
   {:dispatch-n [[:loading k false]
                 [:admin.accounts.entry/fetch-notes account-id]
                 [:admin.accounts.entry.create-note/update :subject ""]
                 [:admin.accounts.entry.create-note/update :content ""]
                 [:admin.accounts.entry.create-note/update :notify true]]}))


(reg-event-db
 :admin.accounts.entry.notes/change-pagination
 [(path db/path)]
 (fn [db [_ page size]]
   (assoc db :notes-pagination {:page page :size size})))


(reg-event-fx
 :admin.accounts.entry/fetch-notes
 [(path db/path)]
 (fn [_ [k account-id]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:account {:id account-id}
                 [:id [:notes [:id :subject :content :created :updated
                               [:author [:id :name]]]]]]]
               :on-success [::fetch-notes-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-notes-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:dispatch [:loading k false]
    :db       (->> (get-in response [:data :account :notes])
                   (sort-by :created)
                   (reverse)
                   (assoc db :notes))}))


(reg-event-fx
 :admin.accounts.entry/update-note!
 [(path db/path)]
 (fn [_ [k note-id {:keys [subject content]}]]
   {:dispatch [:loading k true]
    :graphql  {:mutation
               [[:update_note {:params {:note    note-id
                                        :subject subject
                                        :content (format/escape-newlines content)}}
                 [:id [:account [:id]]]]]
               :on-success [::update-note-success k]
               :on-failure [:graphql/failure k]}}))

(reg-event-fx
 ::update-note-success
 [(path db/path)]
 (fn [_ [_ k response]]
   {:dispatch-n [[:loading k false]
                 [:admin.accounts.entry.note/toggle-editing
                  (get-in response [:data :update_note :id])]
                 [:admin.accounts.entry/fetch-notes
                  (get-in response [:data :update_note :account :id])]]}))


(reg-event-db
 :admin.accounts.entry.note/toggle-editing
 [(path db/path)]
 (fn [db [_ note-id]]
   (update-in db [:editing-notes note-id] not)))


(reg-event-fx
 :admin.accounts.entry.note/delete!
 [(path db/path)]
 (fn [_ [k note-id]]
   {:dispatch [:loading k true]
    :graphql  {:mutation   [[:delete_note {:note note-id}]]
               :on-success [::delete-note-success k note-id]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::delete-note-success
 [(path db/path)]
 (fn [{db :db} [_ k note-id _]]
   {:dispatch [:loading k false]
    :db       (update db :notes (fn [notes]
                                  (remove #(= note-id (:id %)) notes)))}))
