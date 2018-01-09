(ns odin.accounts.admin.entry.views.notes
  (:require [re-frame.core :refer [subscribe dispatch]]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [odin.utils.formatters :as format]
            [clojure.string :as string]))


(defn- note-form
  [{:keys [subject content notify on-change is-loading disabled on-submit on-cancel
           button-text is-comment]
    :or   {on-change identity, on-submit identity, button-text "Save"}}]
  [:form {:on-submit #(do
                        (.preventDefault %)
                        (on-submit))}
   (when-not is-comment
     [ant/form-item {:label "Subject"}
      [ant/input {:type          :text
                  :placeholder   "Note subject"
                  :default-value subject
                  :on-change     #(on-change :subject (.. % -target -value))}]])
   [ant/form-item (when-not is-comment {:label "Content"})
    [ant/input {:type          :textarea
                :rows          5
                :placeholder   "Note content"
                :default-value content
                :on-change     #(on-change :content (.. % -target -value))}]]
   (when (some? notify)
     [ant/form-item
      [ant/checkbox {:checked   notify
                     :on-change #(on-change :notify (.. % -target -checked))}
       "Send Slack notification"]])
   [ant/form-item
    [ant/button
     {:type      "primary"
      :html-type :submit
      :loading   is-loading
      :disabled  disabled}
     button-text]

    (when (some? on-cancel)
      [ant/button {:on-click on-cancel} "Cancel"])]])


(defn new-note-form [account]
  (let [form        (subscribe [:admin.accounts.entry.create-note/form-data (:id account)])
        can-create  (subscribe [:admin.accounts.entry/can-create-note? (:id account)])
        is-creating (subscribe [:loading? :admin.accounts.entry/create-note!])
        is-showing  (subscribe [:admin.accounts.entry.create-note/showing?])]
    (fn [account]
      (if @is-showing
        [ant/card {:title "New Note"}
         [note-form
          {:subject     (:subject @form)
           :content     (:content @form)
           :notify      (:notify @form)
           :on-cancel   #(do (dispatch [:admin.accounts.entry.create-note/toggle])
                             (dispatch [:admin.accounts.entry.create-note/clear (:id account)]))
           :on-change   (fn [k v] (dispatch [:admin.accounts.entry.create-note/update (:id account) k v]))
           :is-loading  @is-creating
           :disabled    (not @can-create)
           :on-submit   #(dispatch [:admin.accounts.entry/create-note! (:id account) @form])
           :button-text "Create"}]]
        [ant/button
         {:type     :primary
          :size     :large
          :icon     :plus
          :on-click #(dispatch [:admin.accounts.entry.create-note/toggle])}
         "Create Note"]))))


(defn- edit-note-form
  ([note]
   [edit-note-form note false])
  ([note is-comment]
   (let [form      (r/atom (select-keys note [:subject :content]))
         is-saving (subscribe [:loading? :admin.accounts.entry/update-note!])]
     (fn [note is-comment]
       [note-form
        {:subject    (:subject @form)
         :content    (:content @form)
         :is-comment is-comment
         :is-loading @is-saving
         :on-change  (fn [k v] (swap! form assoc k v))
         :on-cancel  #(dispatch [:admin.accounts.entry.note/toggle-editing (:id note)])
         :on-submit  #(dispatch [:admin.accounts.entry/update-note! (:id note) @form])}]))))


(defn note-action [props text]
  [:a (merge {:href ""} props)
   text])


(defn- note-actions
  ([note]
   [note-actions note false])
  ([{:keys [id subject content created updated author] :as note} is-comment]
   (let [is-deleting   (subscribe [:loading? :admin.accounts.entry.note/delete])
         is-commenting (subscribe [:admin.accounts.entry.note/comment-form-shown? id])
         account       (subscribe [:auth])
         is-author     (= (:id author) (:id @account))]
     [:div.actions
      (when-not is-comment
        [note-action
         {:on-click #(dispatch [:admin.accounts.entry.note/toggle-comment-form id])}
         [:span
          [ant/icon {:type (if @is-commenting "up" "down")}]
          " Comment"]])
      (when is-author
        [note-action
         {:on-click #(dispatch [:admin.accounts.entry.note/toggle-editing id])}
         "Edit"])
      (when is-author
        [note-action
         {:class    "text-red"
          :on-click (fn []
                      (ant/modal-confirm
                       {:title   "Are you sure?"
                        :content "This cannot be undone!"
                        :on-ok   #(dispatch [:admin.accounts.entry.note/delete! id])}))}
         "Delete"])])))


(defn- note-byline [{:keys [created updated author]}]
  (let [updated (when (not= created updated) updated)]
    [:p.byline
     (str "by " (:name author) " at "
          (format/date-time-short created)
          (when-some [d updated]
            (str " (updated at " (format/date-time-short d) ")")))]))


(defn- comment-form [note]
  (let [text          (subscribe [:admin.accounts.entry.note/comment-text (:id note)])
        is-commenting (subscribe [:loading? :admin.accounts.entry.note/add-comment!])]
    [:article.media
     [:figure.media-left [ant/icon {:type "message"}]]
     [:div.media-content
      [:form
       {:on-submit
        #(do
           (.preventDefault %)
           (dispatch [:admin.accounts.entry.note/add-comment! (:id note) @text]))}
       [ant/form-item
        [ant/input
         {:type          :textarea
          :cols          3
          :placeholder   "Enter your comment here."
          :default-value @text
          :on-change     #(dispatch [:admin.accounts.entry.note.comment/update
                                     (:id note) (.. % -target -value)])}]]
       [ant/form-item
        [ant/button
         {:type      :primary
          :html-type :submit
          :disabled  (string/blank? @text)
          :loading   @is-commenting}
         "Comment"]]]]]))


(declare note-content)


(defn- note-body
  ([note]
   [note-body note false])
  ([{:keys [id subject content comments] :as note} is-comment]
   (let [is-commenting (subscribe [:admin.accounts.entry.note/comment-form-shown? id])]
     [:article.media
      (when is-comment
        [:figure.media-left [ant/icon {:type "message"}]])
      [:div.media-content
       (when (some? subject) [:p.subject subject])
       [:p.body {:dangerouslySetInnerHTML {:__html (format/newlines->line-breaks content)}}]
       (note-byline note)
       [note-actions note is-comment]
       (when @is-commenting
         [comment-form note])
       (map-indexed
        #(with-meta [note-content %2 true] {:key %1})
        comments)]])))


(defn- note-content
  ([note]
   [note-content note false])
  ([note is-comment]
   (let [is-editing (subscribe [:admin.accounts.entry.note/editing (:id note)])]
     (if-not @is-editing
       ;; not editing
       [note-body note is-comment]
       ;; is editing
       [edit-note-form note is-comment]))))


(defn note-card [note]
  [ant/card {:class "note"}
   [note-content note]])


(defn pagination []
  (let [pagination (subscribe [:admin.accounts.entry.notes/pagination])]
    [:div.mt3
     [ant/pagination
      {:show-size-changer   true
       :on-show-size-change #(dispatch [:admin.accounts.entry.notes/change-pagination %1 %2])
       :default-current     (:page @pagination)
       :total               (:total @pagination)
       :show-total          (fn [total range]
                              (format/format "%s-%s of %s notes"
                                             (first range) (second range) total))
       :page-size-options   ["5" "10" "15" "20"]
       :default-page-size   (:size @pagination)
       :on-change           #(dispatch [:admin.accounts.entry.notes/change-pagination %1 %2])}]]))
