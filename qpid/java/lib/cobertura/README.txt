Download the cobertura binary from the following location and expand it into
this directory.

http://cobertura.sourceforge.net/download.html

Alternatively run "ant download-cobertura" to do this automatically.
(to set a http proxy for ant use ANT_OPTS="-Dhttp.proxyHost=<host> -Dhttp.proxyPort=<port>")

Run "ant cover-test coverage-report" to generate coverage report.
