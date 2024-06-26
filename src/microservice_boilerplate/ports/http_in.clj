(ns microservice-boilerplate.ports.http-in
  (:require [microservice-boilerplate.adapters :as adapters]
            [microservice-boilerplate.components.websocket :as websocket]
            [microservice-boilerplate.controllers :as controllers]
            [parenthesin.helpers.logs :as logs]))

(defn mock-api
  [{components :components
    {{:keys [client-id _mock-path]} :path} :parameters
    raw :raw}]
  (logs/log :info :client-id client-id :raw raw)

  ; send request to the client
  (let [websocket-component (:websocket components)]
    (websocket/send-message! websocket-component client-id (str raw)))

  {:status 200
   :body "nice"})

(defn get-history
  [{components :components}]
  (let [{:keys [entries usd-price]} (controllers/get-wallet components)]
    {:status 200
     :body (adapters/->wallet-history usd-price entries)}))

(defn do-deposit!
  [{{{:keys [btc]} :body} :parameters
    components :components}]
  (if (pos? btc)
    {:status 201
     :body (-> btc
               (controllers/do-deposit! components)
               adapters/db->wire-in)}
    {:status 400
     :body "btc deposit amount can't be negative."}))

(defn do-withdrawal!
  [{{{:keys [btc]} :body} :parameters
    components :components}]
  (if (neg? btc)
    (if-let [withdrawal (controllers/do-withdrawal! btc components)]
      {:status 201
       :body (adapters/db->wire-in withdrawal)}
      {:status 400
       :body "withdrawal amount bigger than the total in the wallet."})
    {:status 400
     :body "btc withdrawal amount can't be positive."}))
