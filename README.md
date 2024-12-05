# OCI Vault API

This is a Clojure lib to natively access the [OCI Vault
API](https://docs.oracle.com/en-us/iaas/Content/KeyManagement/home.htm).

## Why?

You could of course use the Oracle-provided Java libs, but I find it cumbersome
to convert to and from Java POJO's all the time.  Also, they pull in lots of
transitive dependencies which I want to avoid.  This library is more Clojure-esque
and uses [HttpKit](https://github.com/http-kit/http-kit) instead for a lower
footprint.

## Usage

First include the lib in your project:
```clojure
;; Leiningen
:dependencies [[com.monkeyprojects/oci-vault "<version>"]]
```
Or with `deps.edn`:
```clojure
{:deps
 {com.monkeyprojects/oci-vault {:mvn/version "<version>"}}}
```

The functionality can be found in `monkey.oci.vault` ns.  First create a client,
using your OCI configuration, then you can start invoking endpoints.
```clojure
(require '[monkey.oci.vault :as v])

(def config
  {:tenancy-ocid "your-tenancy-ocid"
   :user-ocid "your-user-ocid"
   :private-key <private-key>
   :key-fingerprint "key-fingerprint"
   :region "oci-region"})

;; Create a client
(def client (v/make-client config))
(def cid "<compartment-ocid>")

;; The client can be used to do general calls
(def vault (v/get-vault client {:vault-id "vault-ocid" :compartment-id cid}))
(def vault-keys (v/list-keys client {:vault-id (:id vault) :compartment-id cid}))

;; Encryption
(def key-id (-> vault-keys first :id))

;; Note that the text must be base64-encoded
(require '[monkey.oci.vault.b64 :as b])
(def enc (v/encrypt client {:key-id key-id :plaintext (b/->b64 "secret message")}))
;; => {:ciphertext "<base64-encoded cipher>" ...}

;; Decrypt it back
(def msg (-> (v/decrypt client {:key-id key-id :ciphertext (:ciphertext enc)})
             :plaintext
	     (b/b64->str)))  ; Make sure to decode back from base64
;; => "secret message"
```

The lib actually uses [Martian](https://github.com/oliyh/martian) to do the
HTTP calls.  This means all functions take an options map which is actually the
arguments expected by the Martian call. This can be a combination of path params,
query args and a request body.  Check the [Oracle API docs](https://docs.oracle.com/en-us/iaas/api/]
for more on which request requires which parameters.

The high-level calls declared in the `vault` ns hide the complexities of working
with the Martian responses.  Instead, they automatically check if the response is
successful, and if so, they unwrap the body of the response.  Otherwise an exception
is thrown.  If you need more control over how the requests are handled, check the
low-level calls below.

## Available calls

Currently, these calls are exposed by the vault library:

 - `get-vault`
 - `list-vaults`
 - `list-keys`
 - `encrypt`
 - `decrypt`
 - `generate-data-encryption-key`
 - `create-secret`
 - `update-secret`
 - `get-secret`
 - `get-secret-bundle`

More will be added as needed, or you can add some yourself.  PR's are welcome!

## Low-level calls

If you need more control over how the calls are processed, including using
them in an async fashion, you can invoke the lower-level calls directly.  These
reside in several namespaces, depending on which kind of category you need.

The raw response of each call is returned, as a `future`.  This is intended
to allow the maximum flexibility when using the lib.  You can inspect the http
status code yourself.  The body is automatically parsed from `JSON`, depending
on the `Content-Type` header.

### Encryption/Decryption

In order to do vault specific calls, you need to use the crypto endpoint of the
vault itself.  The same goes for key-specific calls, where you need the management
endpoint.  For this you have to create a special client, using either the vault id,
or the endpoint itself.  If the vault id is specified, the vault details will be
fetched first in order to obtain the actual http endpoint.

```clojure
(require '[monkey.oci.vault.crypto :as vc])
(require '[monkey.oci.vault.b64 :as b])

;; This will look up the endpoint
(def crypto-client (vc/make-crypto-client (assoc config :vault-id "vault-ocid")))
;; Alternatively you can add the endpoint directly
(def crypto-client (vc/make-crypto-client (assoc config :crypto-endpoint "http://crypto")))

;; Encryption expects a base64 string.  There are utility functions for this.
(def enc (-> (vc/encrypt crypto-client {:key-id "key-ocid"
                                        :key-version-id "version-ocid"
		  		        :plaintext (b/->b64 "very secret text to encrypt")})
	     deref
	     :body
	     :ciphertext))

;; Decryption returns base64 encoded plain text
(def dec (-> (vc/decrypt crypto-client {:key-id "key-ocid"
                                        :key-version-id "version-ocid"
				        :ciphertext enc})
	     deref
	     :body
	     :plaintext
	     u/b64->
	     (String.)))
```

In a similar fashion you can create a management client using `make-mgmt-client`.
You can specify a `:key-id` or `:mgmt-endpoint`.  See the `monkey.oci.vault.mgmt`
namespace for this.

You can also generate a data encryption key.  The [Oracle documentation](https://docs.oracle.com/en-us/iaas/Content/KeyManagement/Tasks/usingkeys_topic-To_generate_a_data_encryption_key_from_your_Vault_master_encryption_key.htm)
is a bit sparse about this, but it returns an AES key that can be used to do encryption/decryption
without having to go to the Vault API for every call.  For instance, using
[buddy.core](https://funcool.github.io/buddy-core/latest/index.html):

```clojure
;; Generate key
(def key (-> (vc/generate-data-encryption-key client
                                              {:key-details
					       {:key-id "key-ocid"
					        :include-plaintext-key true
					        :key-shape
					        {:algorithm "AES"
					         :length 32}}}) ; Must be 16, 24 or 32
	     (deref)
	     :body
	     :plaintext))

(require '[buddy.core.codecs :as codecs])
(require '[buddy.core.crypto :as bcc])
(require '[buddy.core.nonce :as bcn])

;; Generate initialization vector, must be 16 bytes long
(def iv (bcn/random-nonce 16))
;; Convert key to bytes
(def key-bytes (codecs/b64->bytes key))
;; Do some encryption
(def enc (bcc/encrypt (codecs/str->bytes "my secret message")
                      key-bytes
		      iv
		      {:algo :aes-256-gcm})) ; Algo depends on your AES key size
```

### Secrets

You can also create, update or retrieve secrets stored in the vault.  To this end, you first
need to create the appropriate client.  Then you can invoke the appropriate functions: `create-secret`,
`update-secret` and `get-secret`.

```clojure
(require '[monkey.oci.vault.secrets :as vs])

(def client (vs/make-secret-client config))

;; Create new secret
(def s (-> (vs/create-secret client
                             {:secret
			      {:compartment-id "compartment-ocid"
                               :vault-id "vault-ocid"
			       :key-id "key-ocid"
			       :secret-name "my-test-secret"
			       :secret-content
			       {:content-type "BASE64"
			        :content "<some base64 string>"}}})
	   (deref)
	   :body)

;; Update it
@(vs/update-secret client {:secret-id (:id s)
                           :secret {:description "updated description"}})

;; Retrieve it
(-> (vs/get-secret client {:secret-id (:id s)})
    (deref)
    :body)
```

Note that `get-secret` **does not return the secret contents**!  For this you need to
create *another* client (why, Oracle?) using `make-secret-retrieval-client`.  Then you
can use `get-secret-bundle` to fetch the contents.

```clojure
(require '[monkey.oci.vault.secrets :as vs])

(def client (vs/make-secret-retrieval-client config))

(-> (vs/get-secret-bundle client
                          {:secret-id "secret-ocid"
			   :stage "CURRENT"})
    (deref)
    :body)
```

## Todo

 - Add more endpoints.
 - Add more documentation, especially on the required parameters for each call.

## License

Copyright (c) 2024 by [Monkey Projects BV](https://www.monkey-projects.be)

[MIT License](LICENSE)