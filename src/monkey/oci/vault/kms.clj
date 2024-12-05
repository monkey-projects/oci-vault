(ns monkey.oci.vault.kms
  "Endpoint functions for invoking kms (key-management) api."
  (:require [martian.core :as mc]
            [monkey.oci.common
             [martian :as cm]
             [utils :as cu]]
            [monkey.oci.vault
             [http :as h]
             [schemas :as s]]))

(def kms-host (partial format (str "https://kms.%s.oraclecloud.com/" h/api-version)))

(def kms-routes
  [(h/api-route
    {:route-name :get-vault
     :method :get
     :path-parts ["/vaults/" :vault-id]
     :path-schema {:vault-id s/Id}})

   (h/paged-route
    {:route-name :list-vaults
     :method :get
     :path-parts ["/vaults"]
     :query-schema {:compartmentId s/Id}})])

(defn make-client
  "Creates a kms client, that can be used to look up vaults"
  [conf]
  (cm/make-context conf (comp kms-host :region) kms-routes))

(cu/define-endpoints *ns* kms-routes mc/response-for)

(def crypto-endpoint
  "Utility function that retrieves the crypto endpoint for a vault."
  (memoize (comp :crypto-endpoint :body deref get-vault)))

(def mgmt-endpoint
  "Utility function that retrieves the mgmt endpoint for a vault."
  (memoize (comp :management-endpoint :body deref get-vault)))
