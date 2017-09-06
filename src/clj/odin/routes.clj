(ns odin.routes
  (:require [odin.routes.api :as api]
            [odin.config :as config]
            [compojure.core :as compojure :refer [context defroutes GET]]
            [facade.core :as facade] [ring.util.response :as response]))


(defn show
  "Handler to render the CLJS app."
  [{:keys [deps] :as req}]
  (let [render (partial apply str)]
    (-> (facade/app req "odin"
                    :fonts ["https://fonts.googleapis.com/css?family=Fira+Sans"]
                    :json [["stripe" {:key (config/stripe-public-key (:config deps))}]]
                    :stylesheets [facade/font-awesome]
                    :css-bundles ["antd.css" "styles.css"])
        (render)
        (response/response)
        (response/content-type "text/html"))))


(defroutes routes
  (context "/api" [] api/routes)

  (GET "*" [] show))
