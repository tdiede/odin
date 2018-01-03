(ns odin.accounts.admin.entry.db)


(def path ::account)


(def default-value
  {path {:units            []
         :tab              "overview"
         :notes            []
         :notes-pagination {:size 5
                            :page 1}
         :editing-notes    {}
         :create-form      {:subject ""
                            :content ""
                            :notify  true}}})
