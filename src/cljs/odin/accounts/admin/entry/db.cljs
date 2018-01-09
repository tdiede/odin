(ns odin.accounts.admin.entry.db)


(def path ::account)


(def reassign-modal-key
  ::reassign)


(def default-value
  {path {:units            []
         :tab              nil
         :notes            []
         :notes-pagination {:size 5
                            :page 1}
         :editing-notes    {}
         :commenting-notes {}
         :create-form      {}
         :reassign-form    {}}})


(defn allowed?
  "Is `role` allowed to navigate to `tab`?"
  [role tab]
  (boolean
   ((get {:member     #{"membership" "payments" "application" "notes"}
          :applicant  #{"application" "notes"}
          :onboarding #{"application" "payments ""notes"}}
         role #{"notes"})
    tab)))
