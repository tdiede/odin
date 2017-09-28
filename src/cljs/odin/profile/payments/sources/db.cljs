(ns odin.profile.payments.sources.db)


(def path ::sources)


(def add-path ::add-source)


(def default-value
  {path     {:sources        []
             :current        nil
             :autopay        {:on false :source nil}
             :loading        {:list false}}
   add-path {:type            :bank
             :card            {}
             :bank            {}
             :microdeposits   {:amount-1 nil :amount-2 nil}
             :available-types [:bank :card]}})
