(ns odin.profile.payments.sources.db)


(def path ::sources)


(def add-path ::add-source)


(def default-value
  {path     {:sources               []
             :current               nil
             :loading               {:list false}
             :new-account-info-bank {}}
   add-path {:type            :bank
             :available-types [:bank :card]}})
