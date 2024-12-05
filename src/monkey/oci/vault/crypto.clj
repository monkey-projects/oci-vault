(ns monkey.oci.vault.crypto
  (:require [martian.core :as mc]
            [monkey.oci.common
             [martian :as cm]
             [utils :as cu]]
            [monkey.oci.vault
             [http :as h]
             [kms :as kms]
             [schemas :as s]]))

(def crypto-routes
  [(h/api-route
    {:route-name :encrypt
     :method :post
     :path-parts ["/encrypt"]
     :body-schema {:encrypt s/EncryptionDetails}
     :consumes h/json})

   (h/api-route
    {:route-name :decrypt
     :method :post
     :path-parts ["/decrypt"]
     :body-schema {:decrypt s/DecryptionDetails}
     :consumes h/json})

   (h/api-route
    {:route-name :generate-data-encryption-key
     :method :post
     :path-parts ["/generateDataEncryptionKey"]
     :body-schema {:key-details s/GenerateKeyDetails}
     :consumes h/json})])

(defn make-crypto-client
  "Creates a client that can be used to invoke crypto endpoints.  This means first
   retrieving the crypto endpoint for the vault specified in the config.  Alternatively,
   you can specify a `:crypto-endpoint` in the config."
  [conf]
  (cm/make-context conf
                   #(str (or (:crypto-endpoint %)
                             (kms/crypto-endpoint (kms/make-client conf) (select-keys % [:vault-id])))
                         "/" h/api-version)
                   crypto-routes))

(cu/define-endpoints *ns* crypto-routes mc/response-for)
