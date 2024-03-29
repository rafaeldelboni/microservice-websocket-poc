(ns microservice-boilerplate.routes
  (:require [microservice-boilerplate.ports.http-in :as ports.http-in]
            [microservice-boilerplate.schemas.wire-in :as schemas.wire-in]
            [reitit.swagger :as swagger]))

(def routes
  [["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "btc-wallet"
                            :description "small sample using the microservice-boilerplate"}}
           :handler (swagger/create-swagger-handler)}}]

   ["/api"
    {:swagger {:tags ["mock"]}}

    ["/:client-id"
     ["/{*mock-path}"
      {:summary "get mocked api"
       :parameters {:path {:client-id :string
                           :mock-path :string}}
       :handler ports.http-in/mock-api}]]]

   ["/wallet"
    {:swagger {:tags ["wallet"]}}

    ["/history"
     {:get {:summary "get all wallet entries and current total"
            :responses {200 {:body schemas.wire-in/WalletHistory}
                        500 {:body :string}}
            :handler ports.http-in/get-history}}]
    ["/deposit"
     {:post {:summary "do a deposit in btc in the wallet"
             :parameters {:body schemas.wire-in/WalletDeposit}
             :responses {201 {:body schemas.wire-in/WalletEntry}
                         400 {:body :string}
                         500 {:body :string}}
             :handler ports.http-in/do-deposit!}}]

    ["/withdrawal"
     {:post {:summary "do a withdrawal in btc in the wallet if possible"
             :parameters {:body schemas.wire-in/WalletWithdrawal}
             :responses {201 {:body schemas.wire-in/WalletEntry}
                         400 {:body :string}
                         500 {:body :string}}
             :handler ports.http-in/do-withdrawal!}}]]])
