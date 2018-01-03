(ns odin.accounts.admin.entry.db)


(def path ::account)


(def default-value
  {path {:units            []
         :tab              nil
         :notes            []
         :notes-pagination {:size 5
                            :page 1}
         :editing-notes    {}
         :commenting-notes {}
         :create-form      {:subject ""
                            :content ""
                            :notify  true}}})


(defn allowed?
  "Is `role` allowed to navigate to `tab`?"
  [role tab]
  (boolean
   ((get {:member     #{"membership" "application" "notes"}
          :applicant  #{"application" "notes"}
          :onboarding #{"application" "notes"}}
         role #{"notes"})
    tab)))
