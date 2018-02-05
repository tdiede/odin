(ns odin.routes
  (:require [blueprints.models.account :as account]
            [blueprints.models.approval :as approval]
            [blueprints.models.property :as property]
            [blueprints.models.security-deposit :as deposit]
            [buddy.auth.accessrules :refer [restrict]]
            [compojure.core :as compojure :refer [context defroutes GET POST]]
            [customs.access :as access]
            [customs.auth :as auth]
            [customs.role :as role]
            [datomic.api :as d]
            [net.cgrand.enlive-html :as html]
            [facade.core :as facade]
            [facade.snippets :as snippets]
            [odin.config :as config]
            [odin.routes.api :as api]
            [odin.routes.util :refer :all]
            [ring.util.response :as response]
            [toolbelt.datomic :as td]))


;; dev only =====================================================================


(defn- login! [{:keys [params session deps] :as req}]
  (let [{:keys [email password]} params
        account                  (auth/authenticate (d/db (:conn deps)) email password)]
    (cond
      (empty? account)             (-> (response/response "No account on file.")
                                       (response/status 400))
      (:account/activated account) (let [session (assoc session :identity account)]
                                     (-> (response/redirect "/")
                                         (assoc :session session)))
      :otherwise                   (-> (response/response "Invalid credentials")
                                       (response/status 400)))))


;; page for role ================================================================


(html/defsnippet onboarding-navbar "templates/onboarding/navbar.html" [:nav] [])


(html/defsnippet onboarding-content "templates/onboarding.html" [:section] []
  [:section] (html/append (snippets/loading-fullscreen)))


(defmulti page
  (fn [req]
    (let [account (->requester req)]
      (:account/role account))))


(defmethod page :account.role/admin [req]
  (let [account (->requester req)]
    (facade/app req "admin"
               :title "Admin Dashboard"
               :scripts ["https://code.highcharts.com/highcharts.js"
                         "https://code.highcharts.com/modules/exporting.js"
                         "https://code.highcharts.com/modules/drilldown.js"]
               :fonts ["https://fonts.googleapis.com/css?family=Work+Sans"]
               :json [["account" {:id    (td/id account)
                                  :name  (account/short-name account)
                                  :email (account/email account)}]]
               :stylesheets [facade/font-awesome]
               :css-bundles ["antd.css" "styles.css"])))


(defmethod page :account.role/member [req]
  (let [account (->requester req)]
    (facade/app req "member"
               :title "Member Dashboard"
               :fonts ["https://fonts.googleapis.com/css?family=Work+Sans"]
               :json [["stripe"  {:key (config/stripe-public-key (->config req))}]
                      ["account" {:id    (td/id account)
                                  :name  (account/short-name account)
                                  :email (account/email account)}]]
               :stylesheets [facade/font-awesome]
               :css-bundles ["antd.css" "styles.css"])))


(defmethod page :account.role/onboarding [req]
  (let [account (->requester req)
        data    {:move-in      (-> account approval/by-account approval/move-in)
                 :full-deposit (-> account deposit/by-account deposit/amount)
                 :llc          (-> account approval/by-account approval/property property/llc)
                 :name         (account/short-name account)
                 :email        (account/email account)}]
    (facade/app req "onboarding"
                :title "Starcity Onboarding"
                :navbar (onboarding-navbar)
                :content (onboarding-content)
                :json [["stripe"  {:key (config/stripe-public-key (->config req))}]
                       ["account" data]]
                :stylesheets [facade/font-awesome]
                :css-bundles ["antd.css" "styles.css"]
                :chatlio? true)))


;; ==============================================================================
;; api ==========================================================================
;; ==============================================================================


(defn show
  "Handler to render the CLJS app."
  [req]
  (let [render (partial apply str)]
    (-> (page req)
        (render)
        (response/response)
        (response/content-type "text/html"))))


(def ^:private access-handler
  {:and [access/authenticated-user
         {:or [(access/user-isa role/admin)
               (access/user-isa role/member)
               (access/user-isa role/onboarding)]}]})


(defroutes routes

  (GET "/login" []
       (fn [{:keys [deps] :as req}]
         (let [config (:config deps)]
           (if-not (config/development? config)
             (response/redirect (format "%s/login" (config/root-domain config)))
             (-> (response/resource-response "public/login.html")
                 (response/content-type "text/html"))))))

  (POST "/login" [] login!)

  (GET  "/logout" []
        (fn [_]
          (-> (response/redirect "/login")
              (assoc :session nil))))

  (context "/api" [] (restrict api/routes {:handler access-handler}))

  (context "/" [] (restrict (compojure/routes (GET "*" [] show)) {:handler access-handler})))
