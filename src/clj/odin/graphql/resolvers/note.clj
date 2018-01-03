(ns odin.graphql.resolvers.note
  (:require [odin.graphql.authorization :as authorization]
            [blueprints.models.account :as account]
            [blueprints.models.note :as note]
            [datomic.api :as d]
            [blueprints.models.source :as source]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]
            [blueprints.models.events :as events]))


(defn- get-account [note]
  (if-let [parent (note/parent note)]
    (get-account parent)
    (note/account note)))


(defn account [_ _ note]
  (get-account note))


(defn add-comment!
  [{:keys [conn requester]} {:keys [note text]} _]
  (let [parent  (d/entity (d/db conn) note)
        comment (note/create-comment requester text)]
    @(d/transact conn [(note/add-comment parent comment)
                       (events/note-comment-created note comment)
                       (source/create requester)])
    (note/by-uuid (d/db conn) (note/uuid comment))))


(defn create!
  [{:keys [conn requester]} {{:keys [account subject content notify]} :params} _]
  (let [note (note/create subject content :author requester)]
    @(d/transact conn (tb/conj-when
                       [{:db/id account :account/notes note}
                        (source/create requester)]
                       (when notify (events/note-created note))))
    (note/by-uuid (d/db conn) (note/uuid note))))


(defn delete!
  [{:keys [conn requester]} {:keys [note]} _]
  @(d/transact conn [[:db.fn/retractEntity note]
                     (source/create requester)])
  :ok)


(defn update!
  [{:keys [conn requester]} {{:keys [note subject content]} :params} _]
  (let [note (d/entity (d/db conn) note)]
    @(d/transact conn [(note/update note :subject subject :content content)])
    (d/entity (d/db conn) (:db/id note))))


(defmethod authorization/authorized? :note/create! [_ account _]
  (account/admin? account))


(defmethod authorization/authorized? :note/update! [{conn :conn} account params]
  (let [note (d/entity (d/db conn) (get-in params [:params :note]))]
    (and (account/admin? account) (= (:db/id account) (-> note :note/author :db/id)))))


(defmethod authorization/authorized? :note/delete! [{conn :conn} account params]
  (let [note (d/entity (d/db conn) (:note params))]
    (and (account/admin? account) (= (:db/id account) (-> note :note/author :db/id)))))


(defmethod authorization/authorized? :note/add-comment! [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :note/account      account
   ;; mutations
   :note/add-comment! add-comment!
   :note/create!      create!
   :note/delete!      delete!
   :note/update!      update!})
