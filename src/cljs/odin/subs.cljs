(ns odin.subs
  (:require [odin.account.list.subs]
            [odin.account.entry.subs]
            [re-frame.core :refer [reg-sub]]))


;; =============================================================================
;; l10n
;; =============================================================================


(reg-sub
 :language
 (fn [db _]
   (get-in db [:lang])))


;; =============================================================================
;; Config
;; =============================================================================


(reg-sub
 ::config
 (fn [db _]
   (get db :config)))


(reg-sub
 :config/role
 :<- [::config]
 (fn [config _]
   (:role config)))


(reg-sub
 :config/features
 :<- [::config]
 (fn [config _]
   (-> config :features keys set)))


(reg-sub
 :config/error?
 (fn [db _]
   (:configure/error db)))


;; =============================================================================
;; Account
;; =============================================================================


(reg-sub
 :account
 :<- [::config]
 (fn [config _]
   (:account config)))


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
 :<- [:config/features]
 (fn [[menu features] _]
   (->> (:items menu)
        (filter (comp features :feature)))))


(reg-sub
 :menu/showing?
 :<- [::menu]
 (fn [menu _]
   (:showing menu)))


;; =============================================================================
;; Route
;; =============================================================================


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
 :route/root
 :<- [:route/path]
 (fn [path _]
   (first path)))
