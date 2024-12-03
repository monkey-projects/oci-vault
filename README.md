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

;; The client can be used to do general calls
(def vault (:body @(v/get-vault client {:vault-id "vault-ocid"})))
```

The lib actually uses [Martian](https://github.com/oliyh/martian) to do the
HTTP calls.  This means all functions take an options map which is actually the
arguments expected by the Martian call. This can be a combination of path params,
query args and a request body.

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
;; This will look up the endpoint
(def crypto-client (v/make-crypto-client (assoc config :vault-id "vault-ocid")))
;; Alternatively you can add the endpoint directly
(def crypto-client (v/make-crypto-client (assoc config :crypto-endpoint "http://crypto")))

;; Encryption expects a base64 string.  There are utility functions for this.
(def enc (-> (v/encrypt crypto-client {:key-id "key-ocid"
                                       :key-version-id "version-ocid"
				       :plaintext (v/->b64 "very secret text to encrypt")})
	     deref
	     :body
	     :ciphertext))

;; Decryption returns base64 encoded plain text
(def dec (-> (v/decrypt crypto-client {:key-id "key-ocid"
                                       :key-version-id "version-ocid"
				       :ciphertext enc})
	     deref
	     :body
	     :plaintext
	     v/b64->
	     (String.)))
```

In a similar fashion you can create a management client using `make-mgmt-client`.
You can specify a `:key-id` or `:mgmt-endpoint`.

## Todo

 - Add more endpoints

## License

Copyright (c) 2024 by [Monkey Projects BV](https://www.monkey-projects.be)

[MIT License](LICENSE)