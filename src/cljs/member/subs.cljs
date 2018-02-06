(ns member.subs
  (:require [member.profile.subs]
            [member.services.subs]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


;; l10n =========================================================================


(reg-sub
 :language
 (fn [db _]
   (get-in db [:lang])))


;; route ========================================================================


(reg-sub
 :route/current
 (fn [db _]
   (:route db)))


(reg-sub
 :route/path
 :<- [:route/current]
 (fn [{path :path} _]
   path))


(reg-sub
 :route/params
 :<- [:route/current]
 (fn [{params :params} _]
   params))


(reg-sub
 :route/root
 :<- [:route/path]
 (fn [path _]
   (first path)))


;; user =========================================================================


(reg-sub
 :user
 (fn [db _]
   (:account db)))


;; menu =========================================================================


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
 :layout.mobile-menu/showing?
 :<- [::menu]
 (fn [menu _]
   (:showing menu)))
