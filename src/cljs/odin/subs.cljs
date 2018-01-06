(ns odin.subs
  (:require [odin.global.subs]
            [odin.accounts.subs]
            [odin.history.subs]
            [odin.kami.subs]
            [odin.metrics.subs]
            [odin.orders.subs]
            [odin.payments.subs]
            [odin.payment-sources.subs]
            [odin.profile.subs]
            [odin.properties.subs]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


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
;; Authenticated Account
;; =============================================================================


(reg-sub
 :auth
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
 :<- [:config/role]
 (fn [[menu features role] _]
   (if (= role :member)
     []                                 ; NOTE: Only for first release
     (->> (:items menu) (filter (comp features :feature))))))


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
 :route/params
 :<- [:route/current]
 (fn [{params :params} _]
   params))


(reg-sub
 :route/root
 :<- [:route/path]
 (fn [path _]
   (first path)))



;; =============================================================================
;; Loading
;; =============================================================================


(reg-sub
 :loading?
 (fn [db [_ k]]
   (get-in db [:loading k])))
