(ns onboarding.core
  (:require [antizer.reagent :as ant]
            [goog.dom :as gdom]
            [onboarding.events]
            [onboarding.fx]
            [onboarding.routes :as routes]
            [onboarding.subs]
            [onboarding.views :as views]
            [reagent.core :as r]
            [toolbelt.re-frame.fx]
            [re-frame.core :refer [dispatch-sync]]))

(enable-console-print!)


(defn render []
  (r/render
   [ant/locale-provider {:locale (ant/locales "en_US")}
    [views/app]]
   (gdom/getElement "onboarding")))


(defn ^:export run []
  (dispatch-sync [:app/init])
  (routes/hook-browser-navigation!)
  (render))
