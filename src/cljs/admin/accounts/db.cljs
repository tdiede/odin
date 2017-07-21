(ns admin.accounts.db)


(def path :accounts)

(def default-value
  {path {:accounts []
         :loading  {:accounts/list false}}})
