# HttpClient 
### created from scratch

A barebones HttpClient I've written for homework assignment. Includes a reimplementation of HTTP/1.1 protocol. Depends on no external libraries.

Mostly-compatible with [MinimalJavaNetworking](https://github.com/luka-j/MinimalJavaNetworking) high-level API, but provides access to lower level controls as well, such as fine-tuning the TCP connection pool or caching policy. Supports asynchronous operations.

## Building
Unit tests are given as usage demonstration (no real unit testing exists).

Building jar: `./gradlew jar`

jar location: `build/libs/httpclient-0.9-BETA.jar`


Running tests: `./gradlew test`

Report location: `build/reports/tests/test/index.html`


Building javadoc: `./gradlew javadoc`

Docs location: `build/docs/javadoc/index.html`

On Windows, equivalent of `./gradlew` is `gradlew.bat`
