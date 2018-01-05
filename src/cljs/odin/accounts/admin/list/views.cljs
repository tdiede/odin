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
  (let [selected (subscribe [:admin.accounts.list/selected-view])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@selected]
               :on-click      #(dispatch [:admin.accounts.list/select-view (aget % "key")])}
     [ant/menu-item {:key "member"} "Members"]
     [ant/menu-item {:key "applicant"} "Applicants"]
     [ant/menu-item {:key "all"} "All"]]))


(def render-name
  (table/wrap-cljs
   (fn [_ {:keys [name id]}]
     [:a {:href (routes/path-for :accounts/entry :account-id id)} name])))


(def render-email
  (table/wrap-cljs
   (fn [email {id :id}]
     [:a {:href (routes/path-for :accounts/entry :account-id id)} email])))


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
   (fn [_ {{:keys [term starts ends]} :active_license}]
     [ant/tooltip {:title (str (format/date-short starts) "-"
                               (format/date-short ends))}
      [:div.has-text-centered term]])))

(def render-rent-status
  (table/wrap-cljs
   (fn [_ {license :active_license}]
     [:div.has-text-right (:rent_status license "N/A")])))


(defmulti columns (fn [role _] role))


(defmethod columns :member [_ query-params]
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    (table/maybe-render format/phone-number)}
   {:title     (table/sort-col-title query-params :property "Community" db/params->route)
    :dataIndex :property
    :render    render-property}
   {:title     (table/sort-col-title query-params :unit "Unit" db/params->route)
    :dataIndex :unit
    :render    render-unit}
   {:title     (table/sort-col-title query-params :license_term "Term (months)" db/params->route)
    :dataIndex :term
    :render    render-term}
   {:title     "Rent Status"
    :dataIndex :rent-status
    :render    render-rent-status}])


(def render-communities
  (table/wrap-cljs
   (fn [_ account]
     (->> (for [c (get-in account [:application :communities])]
            [:small.fs1 [:a {:href ""} (:name c)]])
          (interpose ", ")
          (into [:div.has-text-right])))))


(defmethod columns :applicant [_ query-params]
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    (table/maybe-render format/phone-number)}
   {:title     (table/sort-col-title query-params :move_in "Move-in" db/params->route)
    :dataIndex [:application :move_in]
    :render    (table/maybe-render format/date-short)}
   {:title     (table/sort-col-title query-params :created "Started" db/params->route)
    :dataIndex [:application :created]
    :render    (table/maybe-render format/date-short)}
   {:title     (table/sort-col-title query-params :updated "Last Activity" db/params->route)
    :dataIndex [:application :updated]
    :render    (table/maybe-render format/date-time-short)}
   {:title     (table/sort-col-title query-params :submitted "Submitted" db/params->route)
    :dataIndex [:application :submitted]
    :render    (table/maybe-render format/date-time-short)}
   {:title     "Communities"
    :dataIndex [:application :communities]
    :render    render-communities}])


(def render-role
  (table/wrap-cljs
   (fn [role {:keys [active_license] :as acct}]
     ;; TODO: Quick hack
     [:div.has-text-right
      (if (and (= role "member") (nil? active_license))
        (str (name role) " (inactive)")
        (name role))])))


(defmethod columns :all [_ query-params]
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    (table/maybe-render format/phone-number)}
   {:title     (table/sort-col-title query-params :created "Created" db/params->route)
    :dataIndex :created
    :render    format/date-time-short}
   {:title     "Role"
    :dataIndex :role
    :render    render-role}])


(defn accounts-search []
  (let [is-loading (subscribe [:loading? :accounts/query])]
    [ant/form-item
     [ant/input-search
      {:placeholder "Search by name or email"
       :style       {:width "100%"}
       :on-change   #(dispatch [:admin.accounts.list/search-accounts (.. % -target -value)])
       :prefix      (when @is-loading (r/as-element [ant/icon {:type "loading"}]))}]]))


(defn accounts-table []
  (let [selected   (subscribe [:admin.accounts.list/selected-view])
        accounts   (subscribe [:admin.accounts/list])
        params     (subscribe [:admin.accounts.list/query-params])
        is-loading (subscribe [:loading? :accounts/query])]
    [:div
     [:div.table-controls
      [:div.columns
       [:div.column.is-one-third
        [accounts-search]]]]
     [ant/spin
      (tb/assoc-when
       {:tip      "Fetching accounts..."
        :spinning @is-loading}
       :delay (when-not (empty? @accounts) 1000))
      [ant/table {:columns    (columns (keyword @selected) @params)
                  :dataSource (map-indexed #(assoc %2 :key %1) @accounts)}]]]))


(defn view []
  [:div
   (typography/view-header "People" "Manage members and applicants")
   [role-menu]
   [:div.mt2
    [accounts-table]]])
