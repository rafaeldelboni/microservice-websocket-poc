(ns integration.microservice-boilerplate.util
  (:require [com.stuartsierra.component :as component]
            [parenthesin.helpers.logs :as logs]
            [parenthesin.helpers.migrations :as migrations]
            [pg-embedded-clj.core :as pg-emb]))

(def db-test-config
  {:database {:dbtype "postgres"
              :dbname "postgres"
              :username "postgres"
              :password "postgres"
              :host "localhost"
              :port 5432}})

(defn start-system!
  [system-start-fn]
  (fn []
    (logs/setup :info :auto)
    (pg-emb/init-pg)
    (migrations/migrate (migrations/configuration-with-db db-test-config))
    (system-start-fn)))

(defn stop-system!
  [system]
  (component/stop-system system)
  (pg-emb/halt-pg!))
