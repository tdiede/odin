(ns odin.profile.db
  (:require [odin.profile.membership.db]))
  ;;(:require [odin.profile.payments.sources.db :as sources]))


(def path ::profile)

(def ^:private default-info {:first_name ""
                             :last_name  ""
                             :phone      ""})

(def default-value
   {path {:account nil
          :contact {:personal {:current default-info
                               :new     default-info}
                    :emergency {:current default-info
                                :new     default-info}}
          :loading {:account/info false}}})
