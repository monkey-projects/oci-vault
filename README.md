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

TODO

## License

Copyright (c) 2024 by [Monkey Projects BV](https://www.monkey-projects.be)

[MIT License](LICENSE)