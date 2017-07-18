(ns admin.routes
  (:require [compojure.core :as compojure :refer [context defroutes GET]]
            [facade.core :as facade]
            [ring.util.response :as response]))

(defn- show-admin [req]
  (let [render (partial apply str)]
    (-> (facade/app req "admin"
                    :stylesheets [facade/font-awesome]
                    :css-bundles ["styles.css" "antd.css"])
        (render)
        (response/response)
        (response/content-type "text/html"))))


(defroutes routes
  (context "/" [] (compojure/routes (GET "*" [] show-admin))))
