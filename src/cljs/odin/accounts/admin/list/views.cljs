(ns odin.accounts.admin.list.views
  (:require [iface.typography :as typography]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [iface.table :as table]
            [odin.routes :as routes]
            [odin.utils.formatters :as format]))


(defn role-menu []
  (let [selected (subscribe [:admin.accounts.list/selected-role])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@selected]
               :on-click      #(dispatch [:admin.accounts.list/select-role (aget % "key")])}
     [ant/menu-item {:key "member"} "Members"]
     [ant/menu-item {:key "applicant"} "Applicants"]]))


(def render-name
  (table/wrap-cljs
   (fn [_ {:keys [name id]}]
     [:a {:href (routes/path-for :accounts/entry :account-id id)} name])))


(def render-email
  (table/wrap-cljs
   (fn [email _]
     [:a {:href (str "mailto:" email)} email])))


(def render-property
  (table/wrap-cljs
   (fn [_ {property :property}]
     [:a {:href ""} (:name property)])))


(def render-unit
  (table/wrap-cljs
   (fn [_ {license :active_license}]
     (get-in license [:unit :code]))))


(def render-term
  (table/wrap-cljs
   (fn [_ {license :active_license}]
     (get-in license [:term]))))


(def render-term-end
  (table/wrap-cljs
   (fn [_ {license :active_license}]
     (format/date-short (get-in license [:ends])))))


(def render-rent-status
  (table/wrap-cljs
   (fn [_ {license :active_license}]
     (:rent_status license))))


(defn- members-columns []
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    format/phone-number}
   {:title     "Community"
    :dataIndex :property
    :render    render-property}
   {:title     "Unit"
    :dataIndex :unit
    :render    render-unit}
   {:title     "Term (months)"
    :dataIndex :term
    :render    render-term}
   {:title     "License Ends"
    :dataIndex :term-end
    :render    render-term-end}
   {:title     "Rent Status"
    :dataIndex :rent-status
    :render    render-rent-status}])


(defn accounts-table []
  (let [selected   (subscribe [:admin.accounts.list/selected-role])
        accounts   (subscribe [:admin.accounts/list])
        is-loading (subscribe [:loading? :accounts/query])]
    [ant/spin
     (tb/assoc-when
      {:tip      "Fetching accounts..."
       :spinning @is-loading}
      :delay (when-not (empty? @accounts) 1000))
     [ant/table {:columns    (if (= @selected "member") (members-columns) [])
                 :dataSource (map-indexed #(assoc %2 :key %1) @accounts)}]]))


(defn view []
  [:div
   (typography/view-header "People" "Manage members and applicants")
   [role-menu]
   [:div.mt2
    [accounts-table]]])
