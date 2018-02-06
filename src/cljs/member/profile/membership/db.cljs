(ns member.profile.membership.db)


(def path ::membership)


(def default-value
  {path {:license nil
         ;; {:term     nil
         ;;  :rate     nil
         ;;  :starts   (js/moment.)
         ;;  :ends     (js/moment.)
         ;;  :property {:name            nil
         ;;             :code            nil
         ;;             :cover_image_url nil}
         ;;  :unit     {:number nil}
         ;;  :autopay  false}
         }})
