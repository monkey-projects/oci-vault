(ns monkey.oci.vault
  "Core namespace for vault management.  It provides a layer on top of the lower-level
   more specialized functions."
  (:require [martian.core :as mc]
            [monkey.oci.common
             [pagination :as cp]
             [utils :as cu]]
            [monkey.oci.vault
             [crypto :as crypto]
             [kms :as kms]
             [mgmt :as mgmt]
             [secrets :as secrets]]))

(defn make-client
  "Creates a common client, that can be used to invoke all defined endpoints.
   This is a wrapper object for the various specialized clients in each of the
   sub-namespaces."
  [config]
  {:kms (kms/make-client config)
   ;; Lazily initialize the management and crypto clients because it may fetch vault details
   ;; TODO Try to only retrieve vault details once
   :mgmt (atom {:config config})
   :crypto (atom {:config config})
   :secrets (secrets/make-secret-client config)
   :secret-retrieval (secrets/make-secret-retrieval-client config)})

(defn- throw-on-error! [{:keys [status] :as resp}]
  (when (or (nil? status) (>= status 400))
    (throw (ex-info "Vault request failed" resp)))
  resp)

(defn- checked-call [ctx id params]
  (-> (mc/response-for ctx id params)
      deref
      (throw-on-error!)))

(defn- response-for
  "Creates an endpoint fn by retrieving a sub-client from the wrapper client
   and passing the call to it."
  [sub-client]
  (fn [client id params]
    (checked-call (sub-client client) id params)))

(defn- body-response-for [sub-client]
  (comp :body (response-for sub-client)))

(defn- resolve-client
  "Lazily creates a client, if it has not been configured in the context yet."
  [maker prop ctx id params]
  (letfn [(init-client [a]
            (swap! a (fn [{:keys [config] :as c}]
                       (assoc c :client (-> config
                                            (merge (select-keys params [:vault-id]))
                                            (maker))))))]
    (if-let [c (-> ctx prop deref :client)]
      c
      ;; Not initialized yet, do it here
      (-> (prop ctx)
          (init-client)
          :client))))

(def ^:private get-mgmt-client (partial resolve-client #'mgmt/make-mgmt-client :mgmt))
(def ^:private get-crypto-client (partial resolve-client #'crypto/make-crypto-client :crypto))

(defn- lazy-init-call [getter ctx id params]
  (checked-call (getter ctx id params) id params))

(defn- endpoints [routes f]
  (cu/define-endpoints *ns* routes f))

;; Define all routes for kms
(endpoints kms/kms-routes (body-response-for :kms))
;; Define mgmt and crypto endpoints
(endpoints mgmt/mgmt-routes (comp :body (partial lazy-init-call get-mgmt-client)))
(endpoints crypto/crypto-routes (comp :body (partial lazy-init-call get-crypto-client)))

;; Secrets endpoints
(endpoints secrets/secret-routes (body-response-for :secrets))
(endpoints secrets/secret-retrieval-routes (body-response-for :secret-retrieval))

(defn- paged-endpoint [id endpoint-fn]
  (fn [client opts]
    (cp/paginate
     (cp/paged-request-sync
      (partial endpoint-fn client id)
      opts))))

;; Override some endpoint calls to include pagination
(def list-vaults (paged-endpoint :list-vaults (response-for :kms)))
(def list-keys (paged-endpoint :list-keys (partial lazy-init-call get-mgmt-client)))
(def list-secrets (paged-endpoint :list-secrets (response-for :secrets)))
