(ns odin.routes
  (:require [buddy.auth.accessrules :refer [restrict]]
            [odin.routes.api :as api]
            [odin.config :as config]
            [customs.auth :as auth]
            [customs.access :as access]
            [customs.role :as role]
            [compojure.core :as compojure :refer [context defroutes GET POST]]
            [facade.core :as facade]
            [ring.util.response :as response]
            [datomic.api :as d]))


;; =============================================================================
;; Login Handler (development only)
;; =============================================================================


(defn- login! [{:keys [params session deps] :as req}]
  (let [{:keys [email password]} params
        account                  (auth/authenticate (d/db (:conn deps)) email password)]
    (if (:account/activated account)
      (let [session (assoc session :identity account)]
        (-> (response/redirect "/")
            (assoc :session session)))
      (-> (response/response "Invalid credentials")
          (response/status 400)))))


(defn show
  "Handler to render the CLJS app."
  [{:keys [deps] :as req}]
  (let [render (partial apply str)]
    (-> (facade/app req "odin"
                    ;;:fonts ["https://fonts.googleapis.com/css?family=Fira+Sans"]
                    :fonts ["https://fonts.googleapis.com/css?family=Work+Sans|Fira+Sans"]
                    :json [["stripe" {:key (config/stripe-public-key (:config deps))}]]
                    :stylesheets [facade/font-awesome]
                    :css-bundles ["antd.css" "styles.css"])
        (render)
        (response/response)
        (response/content-type "text/html"))))


(def ^:private access-handler
  {:and [access/authenticated-user
         {:or [(access/user-isa role/admin)
               (access/user-isa role/member)]}]})


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
              ;; NOTE: Must assoc `nil` into the session for this to work. Seems weird
              ;; to have different behavior when a key has a value of `nil` than for
              ;; when a key is not present. Given what `nil` means, these should be the
              ;; same? Perhaps submit a PR?
              (assoc :session nil))))

  (context "/api" [] (restrict api/routes {:handler access-handler}))

  (context "/" [] (restrict (compojure/routes (GET "*" [] show)) {:handler access-handler})))
