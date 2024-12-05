(ns monkey.oci.vault.b64
  "Functions to convert to/from base64"
  (:import java.util.Base64))

(defn ->b64
  "Converts the input string to base64"
  [x]
  (.encodeToString (Base64/getEncoder) (.getBytes x)))

(defn b64->
  "Decodes from base64, returns a byte array."
  [x]
  (.decode (Base64/getDecoder) x))

(defn b64->str
  "Decodes base64 string to a string"
  [x]
  (String. (b64-> x)))
