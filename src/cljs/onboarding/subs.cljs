(ns onboarding.subs
  (:require [onboarding.db :as db]
            [onboarding.prompts.subs]
            [re-frame.core :refer [reg-sub]]))

(reg-sub
 :db
 (fn [db _]
   db))

(reg-sub
 :app/bootstrapping?
 (fn [db _]
   (:bootstrapping db)))

;; =============================================================================
;; Menu
;; =============================================================================

(reg-sub
 ::menu
 (fn [db _]
   (:menu db)))

(reg-sub
 :menu/items
 :<- [::menu]
 (fn [menu _]
   (:items menu)))

(reg-sub
 :menu/active
 :<- [::menu]
 (fn [menu _]
   (:active menu)))

(reg-sub
 :menu.items/complete
 :<- [::menu]
 (fn [menu _]
   (:complete menu)))
