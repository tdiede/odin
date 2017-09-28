(ns odin.account.db)


(def path :accounts)

(def default-value
  {path {:accounts {:list  []
                    :norms {}}
         :loading  {:accounts/list false}}})
