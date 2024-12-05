(ns monkey.oci.vault.secrets
  (:require [martian.core :as mc]
            [monkey.oci.common
             [martian :as cm]
             [utils :as cu]]
            [monkey.oci.vault
             [http :as h]
             [kms :as kms]
             [schemas :as s]]))

(def vault-host (partial format (str "https://vaults.%s.oraclecloud.com/" h/api-version)))

(def secret-routes
  [(h/paged-route
    {:route-name :list-secrets
     :method :get
     :path-parts ["/secrets"]
     :query-schema s/ListSecretsQuery})

   (h/api-route
    {:route-name :create-secret
     :method :post
     :path-parts ["/secrets"]
     :body-schema {:secret s/CreateSecret}})
   
   (h/api-route
    {:route-name :update-secret
     :method :post
     :path-parts ["/secrets/" :secret-id]
     :path-schema {:secret-id s/Id}
     :body-schema {:secret s/UpdateSecret}})

   (h/api-route
    {:route-name :get-secret
     :method :get
     :path-parts ["/secrets/" :secret-id]
     :path-schema {:secret-id s/Id}})])

(cu/define-endpoints *ns* secret-routes mc/response-for)

(defn make-secret-client
  "Creates a client that can be used to manage secrets"
  [conf]
  (cm/make-context conf (comp vault-host :region) secret-routes))

(def secret-retrieval-routes
  [(h/api-route
    {:route-name :get-secret-bundle
     :method :get
     :path-parts ["/secretbundles/" :secret-id]
     :path-schema {:secret-id s/Id}
     :query-schema s/SecretRetrievalQuery})

   (h/api-route
    {:route-name :get-secret-bundle-by-name
     :method :get
     :path-parts ["/secretbundles/actions/getByName"]
     :query-schema s/SecretRetrievalByNameQuery})])

(cu/define-endpoints *ns* secret-retrieval-routes mc/response-for)

(def secret-host (partial format "https://secrets.%s.oraclecloud.com/20190301"))

(defn make-secret-retrieval-client
  "Creates a client that can be used to manage secrets"
  [conf]
  (cm/make-context conf (comp secret-host :region) secret-retrieval-routes))
