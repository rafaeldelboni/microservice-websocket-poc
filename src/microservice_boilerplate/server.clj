(ns microservice-boilerplate.server
  (:require [com.stuartsierra.component :as component]
            [microservice-boilerplate.components.reitit-pedestal-jetty :as webserver]
            [microservice-boilerplate.components.router :as router]
            [microservice-boilerplate.components.websocket :as websocket]
            [microservice-boilerplate.routes :as routes]
            [parenthesin.components.config.aero :as config]
            [parenthesin.components.db.jdbc-hikari :as database]
            [parenthesin.components.http.clj-http :as http]
            [parenthesin.helpers.logs :as logs]
            [parenthesin.helpers.migrations :as migrations])
  (:gen-class))

(def system-atom (atom nil))

(defn- build-system-map []
  (component/system-map
   :config (config/new-config)
   :http (http/new-http)
   :router (router/new-router routes/routes)
   :websocket (websocket/new-websocket)
   :database (component/using (database/new-database) [:config])
   :webserver (component/using (webserver/new-webserver)
                               [:websocket :config :http :router :database])))

(defn start-system! [system-map]
  (logs/setup :info :auto)
  (migrations/migrate (migrations/configuration-with-db))
  (->> system-map
       component/start
       (reset! system-atom)))

#_{:clj-kondo/ignore [:unused-public-var]}
(defn stop-system! []
  (swap!
   system-atom
   (fn [s] (when s (component/stop s)))))

(defn -main
  "The entry-point for 'gen-class'"
  [& _args]
  (start-system! (build-system-map)))

(comment
  (start-system! (build-system-map))
  (stop-system!))
