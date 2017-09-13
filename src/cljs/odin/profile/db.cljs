(ns odin.profile.db
  (:require [odin.profile.membership.db]))
  ;;(:require [odin.profile.payments.sources.db :as sources]))


(def path ::profile)


(def default-value
   {path {:account nil
          :loading {:account/info false}}})
