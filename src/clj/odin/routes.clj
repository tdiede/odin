(ns odin.routes
  (:require [odin.routes.api :as api]
            [compojure.core :as compojure :refer [context defroutes GET]]
            [facade.core :as facade] [ring.util.response :as response]))


(defn show
  "Handler to render the CLJS app."
  [req]
  (let [render (partial apply str)]
    (-> (facade/app req "odin"
                    :fonts ["https://fonts.googleapis.com/css?family=Fira+Sans"]
                    :stylesheets [facade/font-awesome]
                    :css-bundles ["antd.css" "styles.css"])
        (render)
        (response/response)
        (response/content-type "text/html"))))


(defroutes routes
  (context "/api" [] api/routes)

  (compojure/routes (GET "*" [] show)))
