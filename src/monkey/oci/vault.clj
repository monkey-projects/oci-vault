(ns monkey.oci.vault
  (:require [martian.core :as mc]
            [monkey.oci.common
             [martian :as cm]
             [pagination :as p]
             [utils :as cu]]
            [schema.core :as s])
  (:import java.util.Base64))

(def version "20180608")
(def kms-host (partial format (str "https://kms.%s.oraclecloud.com/" version)))
(def json ["application/json"])

(def ^:private opt s/optional-key)

(defn api-route [opts]
  (assoc opts :produces json))

(defn paged-route [opts]
  (p/paged-route (api-route opts)))

(defn- prop-matches? [k v]
  (comp (partial = v) k))

(def Id s/Str)

(s/defschema DefinedTags {s/Str {s/Str s/Str}})
(s/defschema FreeformTags {s/Str s/Str})

(def kms-routes
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

(s/defschema Algorithm (s/enum "AES" "RSA" "ECDSA"))

(s/defschema KeyShape
  {:algorithm Algorithm
   (opt :curve-id) (s/enum "NIST_P256" "NIST_P384" "NIST_P512")
   :length (s/enum 16 24 32 48 64 256 384 512)})

(s/defschema GenerateKeyDetails
  {(opt :associated-data) s/Str
   :include-plaintext-key s/Bool
   :key-id Id
   :key-shape KeyShape
   (opt :logging-context) {s/Str s/Str}})

(def algo-details
  {:key-id Id
   (opt :encryption-algorithm) (s/enum "AES_256_GCM"
                                                  "RSA_OAEP_SHA_1"
                                                  "RSA_OAEP_SHA_256")
   (opt :key-version-id) Id
   (opt :logging-context) {s/Str s/Str}})

(s/defschema EncryptionDetails
  (assoc algo-details :plaintext s/Str))

(s/defschema DecryptionDetails
  (assoc algo-details :ciphertext s/Str))

(def crypto-routes
  [(api-route
    {:route-name :encrypt
     :method :post
     :path-parts ["/encrypt"]
     :body-schema {:encrypt EncryptionDetails}
     :consumes json})

   (api-route
    {:route-name :decrypt
     :method :post
     :path-parts ["/decrypt"]
     :body-schema {:decrypt DecryptionDetails}
     :consumes json})

   (api-route
    {:route-name :generate-data-encryption-key
     :method :post
     :path-parts ["/generateDataEncryptionKey"]
     :body-schema {:key-details GenerateKeyDetails}
     :consumes json})])

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
                    (opt :sortBy) (s/enum "TIMECREATED" "DISPLAYNAME")
                    (opt :sortOrder) (s/enum "ASC" "DESC")
                    (opt :protectionMode) (s/enum "HSM" "SOFTWARE" "EXTERNAL")
                    (opt :algorithm) Algorithm
                    (opt :curveId) Id}})])

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

(def secret-host (partial format (str "https://vaults.%s.oraclecloud.com/" version)))

(def target-system-base
  {:target-system-type (s/enum "ADB" "FUNCTION")})

(s/defschema AdbTargetSystemDetails
  (assoc target-system-base
         :adb-id Id))

(s/defschema FunctionTargetSystemDetails
  (assoc target-system-base
         :function-id Id))

(s/defschema TargetSystemDetails
  (s/conditional (prop-matches? :target-system-type "ADB") AdbTargetSystemDetails
                 (prop-matches? :target-system-type "FUNCTION") FunctionTargetSystemDetails))

(s/defschema RotationConfig
  {:target-system-details TargetSystemDetails
   (opt :is-schedule-rotation-enabled) s/Bool
   (opt :rotation-interval) s/Str})

(s/defschema SecretContentDetails
  {:content-type (s/enum "BASE64")
   (opt :name) s/Str
   (opt :stage) (s/enum "CURRENT" "PENDING")
   (opt :content) s/Str})

(def generation-context-base
  {:generation-type (s/enum "PASSPHRASE" "SSH_KEY" "BYTES")
   (opt :secret-template) s/Str})

(s/defschema PassphraseGenerationContext
  (assoc generation-context-base
         :generation-template (s/enum "SECRETS_DEFAULT_PASSWORD" "DBAAS_DEFAULT_PASSWORD")
         (opt :passphrase-length) s/Int))

(s/defschema SshKeyGenerationContext
  (assoc generation-context-base
         :generation-template (s/enum "RSA_2048" "RSA_3072" "RSA_4096")))

(s/defschema BytesGenerationContext
  (assoc generation-context-base
         :generation-template (s/enum "BYTES_512" "BYTES_1024")))

(s/defschema SecretGenerationContext
  (s/conditional (prop-matches? :generation-type "PASSPHRASE") PassphraseGenerationContext
                 (prop-matches? :generation-type "SSH_KEY") SshKeyGenerationContext
                 (prop-matches? :generation-type "BYTES") BytesGenerationContext))

(def secret-rule-base
  {:rule-type (s/enum "SECRET_EXPIRY_RULE" "SECRET_REUSE_RULE")})

(s/defschema SecretExpiryRule
  (assoc secret-rule-base
         (opt :is-secret-content-retrieval-blocked-on-expiry) s/Bool
         (opt :secret-version-expiry-interval) s/Str
         (opt :time-of-absolute-expiry) s/Str))

(s/defschema SecretReuseRule
  (assoc secret-rule-base
         (opt :is-enforced-on-deleted-secret-versions) s/Bool))

(s/defschema SecretRule
  (s/conditional (prop-matches? :rule-type "SECRET_EXPIRY_RULE") SecretExpiryRule
                 (prop-matches? :rule-type "SECRET_REUSE_RULE") SecretReuseRule))

(def secret-base
  {(opt :description) s/Str
   (opt :defined-tags) DefinedTags
   (opt :freeform-tags) FreeformTags
   (opt :metadata) {s/Str s/Str}
   (opt :enable-auto-generation) s/Bool
   (opt :rotation-config) RotationConfig
   (opt :secret-content) SecretContentDetails
   (opt :secret-generation-context) SecretGenerationContext
   (opt :secret-rules) [SecretRule]})

(s/defschema CreateSecret
  (assoc secret-base
         :key-id Id
         :vault-id Id
         :compartment-id Id
         :secret-name s/Str))

(s/defschema UpdateSecret
  (assoc secret-base
         (opt :current-version-number) s/Int))

(def secret-routes
  [(api-route
    {:route-name :create-secret
     :method :post
     :path-parts ["/secrets"]
     :body-schema {:secret CreateSecret}})
   
   (api-route
    {:route-name :update-secret
     :method :post
     :path-parts ["/secrets/" :secret-id]
     :path-schema {:secret-id Id}
     :body-schema {:secret UpdateSecret}})

   (api-route
    {:route-name :get-secret
     :method :get
     :path-parts ["/secrets/" :secret-id]
     :path-schema {:secret-id Id}})])

(cu/define-endpoints *ns* secret-routes mc/response-for)

(defn make-secret-client
  "Creates a client that can be used to manage secrets"
  [conf]
  (cm/make-context conf (comp secret-host :region) secret-routes))

(defn ->b64
  "Converts the input string to base64"
  [x]
  (.encodeToString (Base64/getEncoder) (.getBytes x)))

(defn b64->
  "Decodes from base64, returns a byte array."
  [x]
  (.decode (Base64/getDecoder) x))
