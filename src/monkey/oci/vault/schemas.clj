(ns monkey.oci.vault.schemas
  (:require [schema.core :as s]))

(def ^:private opt s/optional-key)

(defn- prop-matches? [k v]
  (comp (partial = v) k))

(def Id s/Str)

(defn sortable-query [sort-props]
  {(opt :sortBy) (apply s/enum sort-props)
   (opt :sortOrder) (s/enum "ASC" "DESC")})

(s/defschema DefinedTags {s/Str {s/Str s/Str}})
(s/defschema FreeformTags {s/Str s/Str})
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

(s/defschema MgmtQuery
  {:compartmentId Id
   (opt :sortBy) (s/enum "TIMECREATED" "DISPLAYNAME")
   (opt :sortOrder) (s/enum "ASC" "DESC")
   (opt :protectionMode) (s/enum "HSM" "SOFTWARE" "EXTERNAL")
   (opt :algorithm) Algorithm
   (opt :curveId) Id})

(s/defschema SecretLifecycleState
  (s/enum
   "CREATING"
   "ACTIVE"
   "UPDATING"
   "DELETING"
   "DELETED"
   "SCHEDULING_DELETION"
   "PENDING_DELETION"
   "CANCELLING_DELETION"
   "FAILED"))

(s/defschema ListSecretsQuery
  (merge
   (sortable-query ["NAME" "TIMECREATED"])
   {:compartmentId Id
    (opt :name) s/Str
    (opt :vaultId) Id
    (opt :lifecycleState) SecretLifecycleState}))

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

(s/defschema BundleStage (s/enum "CURRENT"
                                 "PENDING"
                                 "LATEST"
                                 "PREVIOUS"
                                 "DEPRECATED"))

(s/defschema SecretRetrievalQuery
  {(opt :versionNumber) s/Int
   (opt :secretVersionName) s/Str
   (opt :stage) BundleStage})
