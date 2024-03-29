(ns microservice-boilerplate.components.websocket
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.http.jetty.websockets :as ws]
            [parenthesin.helpers.logs :as logs])
  (:import (org.eclipse.jetty.websocket.api Session)
           (org.eclipse.jetty.websocket.api Session WebSocketConnectionListener WebSocketListener)
           [org.eclipse.jetty.websocket.servlet ServletUpgradeRequest]))

(defn get-client-id [^ServletUpgradeRequest upgrade-request]
  (-> upgrade-request
      .getHeaders
      (get "Sec-WebSocket-Protocol")
      first))

(defn make-ws-listener
  "Given a map representing WebSocket actions
  (:on-connect, :on-close, :on-error, :on-text, :on-binary),
  return a WebSocketConnectionListener.
  Values for the map are functions with the same arity as the interface."
  [req _res ws-map]
  (let [client-id (get-client-id req)]
    (reify
      WebSocketConnectionListener
      (onWebSocketConnect [_this ws-session]
        (when-let [f (:on-connect ws-map)]
          (f ws-session)))

      (onWebSocketClose [_this status-code reason]
        (when-let [f (:on-close ws-map)]
          (f client-id status-code reason)))

      (onWebSocketError [_this cause]
        (when-let [f (:on-error ws-map)]
          (f client-id cause)))

      WebSocketListener
      (onWebSocketText [_this msg]
        (when-let [f (:on-text ws-map)]
          (f client-id msg)))

      (onWebSocketBinary [_this payload offset length]
        (when-let [f (:on-binary ws-map)]
          (f client-id payload offset length))))))

(defn build-ws-endpoints-fn [paths]
  (fn [arg]
    (ws/add-ws-endpoints arg paths {:listener-fn (fn [req res ws-map]
                                                   (make-ws-listener req res ws-map))})))

(defprotocol WebsocketProvider
  (connect-client!
    [self ws-session send-ch])
  (send-message!
    [self client-id message])
  (disconnect-client!
    [self client-id])
  (broadcast-message!
    [self message])
  (build-paths
    [self]))

(defrecord Websocket [websocket-clients]
  component/Lifecycle
  (start [this] this)
  (stop  [this] this)

  WebsocketProvider
  (connect-client! [_self ws-session send-ch]
    (.setIdleTimeout ws-session -1)
    (let [client-id (get-client-id (.getUpgradeRequest ws-session))]
      (logs/log :info :client-connected client-id)
      (swap! websocket-clients
             assoc client-id
             {:session ws-session
              :ch send-ch})))

  (send-message! [_self client-id message]
    (when-let [{:keys [_session ch]} (get @websocket-clients client-id)]
      (async/put! ch message)))

  (disconnect-client! [_self client-id]
    (let [[_client {:keys [_session ch]}] (get @websocket-clients client-id)]
      (async/put! ch "Disconnected by the server.")
      (async/close! ch)))

  (broadcast-message! [_self message]
    (doseq [[_client {:keys [^Session session ch]}] @websocket-clients]
      (when (.isOpen session)
        (async/put! ch message))))

  (build-paths [self]
    {"/ws" {:on-connect (ws/start-ws-connection (partial connect-client! self))
            :on-text (fn [client msg]
                       (logs/log :info
                                 :client client
                                 :msg (str "A client sent - " msg)))
            :on-binary (fn [client payload offset length]
                         (logs/log :info
                                   :client client
                                   :msg "Binary Message!"
                                   :offset offset
                                   :length length
                                   :bytes payload))
            :on-error (fn [client t]
                        (logs/log :error
                                  :client client
                                  :msg "WS Error happened"
                                  :exception t))
            :on-close (fn [client num-code reason-text]
                        (logs/log :info
                                  :client client
                                  :msg "WS Closed:"
                                  :num-code num-code
                                  :reason reason-text)
                        (swap! websocket-clients dissoc client))}}))

(defn new-websocket []
  (map->Websocket {:websocket-clients (atom {})}))
