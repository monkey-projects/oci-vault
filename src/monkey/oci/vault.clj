(ns monkey.oci.vault
  (:require [martian.core :as mc]
            [monkey.oci.common
             [martian :as cm]
             [pagination :as p]
             [utils :as cu]]
            [schema.core :as s]))

(def version "20180608")
(def host (partial format (str "https://kms.%s.oraclecloud.com/" version)))
(def json ["application/json"])

(defn api-route [opts]
  (assoc opts :produces json))

(defn paged-route [opts]
  (p/paged-route (api-route opts)))

(def Id s/Str)

(def routes
  [(api-route
    {:route-name :get-vault
     :method :get
     :path-parts ["/vaults/" :vault-id]
     :path-schema {:vault-id Id}})

   (paged-route
    {:route-name :list-vaults
     :method :get
     :path-parts ["/vaults"]
     :query-schema {:compartmentId Id}})])

(defn make-client [conf]
  (cm/make-context conf (comp host :region) routes))

(cu/define-endpoints *ns* routes mc/response-for)

(def crypto-endpoint
  "Utility function that retrieves the crypto endpoint for a vault."
  (memoize (comp :crypto-endpoint :body deref get-vault)))

(def mgmt-endpoint
  "Utility function that retrieves the mgmt endpoint for a vault."
  (memoize (comp :management-endpoint :body deref get-vault)))

(def crypto-routes
  [(api-route
    {:route-name :encrypt
     :method :post
     :path-parts ["/encrypt"]
     :body-schema {:encrypt {:key-id Id
                             :plaintext s/Str
                             (s/optional-key :encryption-algorithm) s/Str
                             (s/optional-key :key-version-id) Id}}})

   (api-route
    {:route-name :decrypt
     :method :post
     :path-parts ["/decrypt"]
     :body-schema {:decrypt {:key-id Id
                             :ciphertext s/Str
                             (s/optional-key :encryption-algorithm) s/Str
                             (s/optional-key :key-version-id) Id}}})])

(defn make-crypto-client
  "Creates a client that can be used to invoke crypto endpoints.  This means first
   retrieving the crypto endpoint for the vault specified in the config.  Alternatively,
   you can specify a `:crypto-endpoint` in the config."
  [conf]
  (cm/make-context conf
                   #(str (or (:crypto-endpoint %)
                             (crypto-endpoint (make-client conf) (select-keys % [:vault-id])))
                         "/" version)
                   crypto-routes))

(cu/define-endpoints *ns* crypto-routes mc/response-for)

(def mgmt-routes
  [(paged-route
    {:route-name :list-keys
     :method :get
     :path-parts ["/keys"]
     :query-schema {:compartmentId Id
                    (s/optional-key :sortBy) (s/enum "TIMECREATED" "DISPLAYNAME")
                    (s/optional-key :sortOrder) (s/enum "ASC" "DESC")
                    (s/optional-key :protectionMode) (s/enum "HSM" "SOFTWARE" "EXTERNAL")
                    (s/optional-key :algorithm) (s/enum "AES" "RSA" "ECDSA")
                    (s/optional-key :curveId) Id}})])

(defn make-mgmt-client
  "Creates a client that can be used to invoke vault management endpoints.  This 
   means first retrieving the management endpoint for the vault specified in the config,
   or using the one specified."
  [conf]
  (cm/make-context conf
                   #(str (or (:mgmt-endpoint %)
                             (mgmt-endpoint (make-client conf) (select-keys % [:vault-id])))
                         "/" version)
                   mgmt-routes))

(cu/define-endpoints *ns* mgmt-routes mc/response-for)
