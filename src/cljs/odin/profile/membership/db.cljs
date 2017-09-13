(ns odin.profile.membership.db)


(def path ::membership)


(def default-value
   {path {:license {:term   0
                    :rate   0
                    :starts (js/moment.)
                    :ends   (js/moment.)}
          :loading {:member/license false}}})
