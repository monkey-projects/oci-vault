(ns monkey.oci.vault.mgmt
  (:require [martian.core :as mc]
            [monkey.oci.common
             [martian :as cm]
             [utils :as cu]]
            [monkey.oci.vault
             [http :as h]
             [kms :as kms]
             [schemas :as s]]))

(def mgmt-routes
  [(h/paged-route
    {:route-name :list-keys
     :method :get
     :path-parts ["/keys"]
     :query-schema s/MgmtQuery})])

(defn make-mgmt-client
  "Creates a client that can be used to invoke vault management endpoints.  This 
   means first retrieving the management endpoint for the vault specified in the config,
   or using the one specified."
  [conf]
  (cm/make-context conf
                   #(str (or (:mgmt-endpoint %)
                             (kms/mgmt-endpoint (kms/make-client conf) (select-keys % [:vault-id])))
                         "/" h/api-version)
                   mgmt-routes))

(cu/define-endpoints *ns* mgmt-routes mc/response-for)
