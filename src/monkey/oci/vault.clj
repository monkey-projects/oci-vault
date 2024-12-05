(ns monkey.oci.vault
  "Core namespace for vault management.  It provides a layer on top of the lower-level
   more specialized functions."
  (:require [martian.core :as mc]
            [monkey.oci.common.utils :as cu]
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

(defn- response-for
  "Creates an endpoint fn by retrieving a sub-client from the wrapper client
   and passing the call to it."
  [sub-client]
  (fn [client id params]
    (-> (mc/response-for (sub-client client) id params)
        deref
        (throw-on-error!)
        :body)))


(defn- resolve-client [maker prop ctx id params]
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
  (let [client (getter ctx id params)]
    ((response-for (constantly client)) ctx id params)))

;; Define all routes for kms
(cu/define-endpoints *ns* kms/kms-routes (response-for :kms))
;; Define mgmt and crypto endpoints
(cu/define-endpoints *ns* mgmt/mgmt-routes (partial lazy-init-call get-mgmt-client))
(cu/define-endpoints *ns* crypto/crypto-routes (partial lazy-init-call get-crypto-client))

;; Secrets endpoints
(cu/define-endpoints *ns* secrets/secret-routes (response-for :secrets))
(cu/define-endpoints *ns* secrets/secret-retrieval-routes (response-for :secret-retrieval))
