(ns admin.profile.db
  (:require [admin.profile.payments.db :as payments]
            [admin.profile.membership.db :as membership]))

(def path ::profile)

(def ^:private default-info {:first_name ""
                             :last_name  ""
                             :phone      ""})

(def default-value
  (merge
   {path {:account nil
          :contact {:personal  {:current default-info
                                :new     default-info}
                    :emergency {:current default-info
                                :new     default-info}}}}
   payments/default-value
   membership/default-value))
