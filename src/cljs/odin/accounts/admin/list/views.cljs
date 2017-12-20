(ns odin.accounts.admin.list.views
  (:require [iface.typography :as typography]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [iface.table :as table]
            [odin.accounts.admin.list.db :as db]
            [odin.routes :as routes]
            [odin.utils.formatters :as format]
            [reagent.core :as r]))


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


(defn- members-columns [query-params]
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    format/phone-number}
   {:title     (table/sort-col-title query-params :property "Community" db/params->route)
    :dataIndex :property
    :render    render-property}
   {:title     (table/sort-col-title query-params :unit "Unit" db/params->route)
    :dataIndex :unit
    :render    render-unit}
   {:title     (table/sort-col-title query-params :license_term "Term (months)" db/params->route)
    :dataIndex :term
    :render    render-term}
   {:title     (table/sort-col-title query-params :license_end "License Ends" db/params->route)
    :dataIndex :term-end
    :render    render-term-end}
   {:title     "Rent Status"
    :dataIndex :rent-status
    :render    render-rent-status}])


(def render-communities
  (table/wrap-cljs
   (fn [_ account]
     (r/as-element
      (->> (for [c (get-in account [:application :communities])]
             [:small.fs1 [:a {:href ""} (:name c)]])
           (interpose ", ")
           (into [:div]))))))


(defn- applicant-columns [query-params]
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    format/phone-number}
   {:title     (table/sort-col-title query-params :move_in "Move-in" db/params->route)
    :dataIndex [:application :move_in]
    :render    (table/maybe-render format/date-time-short)}
   {:title     (table/sort-col-title query-params :created "Started" db/params->route)
    :dataIndex [:application :created]
    :render    (table/maybe-render format/date-time-short)}
   {:title     (table/sort-col-title query-params :updated "Last Activity" db/params->route)
    :dataIndex [:application :updated]
    :render    (table/maybe-render format/date-time-short)}
   {:title     (table/sort-col-title query-params :submitted "Submitted" db/params->route)
    :dataIndex [:application :submitted]
    :render    (table/maybe-render format/date-time-short)}
   {:title     "Communities"
    :dataIndex [:application :communities]
    :render    render-communities}])


(defn accounts-table []
  (let [selected   (subscribe [:admin.accounts.list/selected-role])
        accounts   (subscribe [:admin.accounts/list])
        params     (subscribe [:admin.accounts.list/query-params])
        is-loading (subscribe [:loading? :accounts/query])]
    [ant/spin
     (tb/assoc-when
      {:tip      "Fetching accounts..."
       :spinning @is-loading}
      :delay (when-not (empty? @accounts) 1000))
     [ant/table {:columns    (if (= @selected "member")
                               (members-columns @params)
                               (applicant-columns @params))
                 :dataSource (map-indexed #(assoc %2 :key %1) @accounts)}]]))


(defn view []
  [:div
   (typography/view-header "People" "Manage members and applicants")
   [role-menu]
   [:div.mt2
    [accounts-table]]])
