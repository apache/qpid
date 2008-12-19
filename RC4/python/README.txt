= INSTALLATION =

Extract the release archive into a directory of your choice and set
your PYTHONPATH accordingly:

  tar -xzf qpid-python-<version>.tar.gz -C <install-prefix>
  export PYTHONPATH=<install-prefix>/qpid-<version>/python

= GETTING STARTED =

The python client includes a simple hello-world example that publishes
and consumes a message:

  cp <install-prefix>/qpid-<version>/python/hello-world .
  ./hello-world

= EXAMPLES =

More comprehensive examples can be found here:

  cd <install-prefix>/qpid-<version>/python/examples

= RUNNING THE TESTS =

The "tests" directory contains a collection of unit tests for the
python client. The "tests_0-10", "tests_0-9", and "tests_0-8"
directories contain protocol level conformance tests for AMQP brokers
of the specified version.

Simplest way to run the tests:

  1. Run a broker on the default port

  2. ./run-tests -s <version>

Where <version> is one of "0-8", "0-9", or "0-10-errata".

See the run-tests usage for for additional options:

  ./run-tests -h

== Expected failures ==

Certain tests are expected to fail due to incomplete functionality or
unresolved interop issues. To skip expected failures for the C++ or
Java brokers:

  ./run-tests -I <file-name>

Where <file-name> is one of the following files:

  * cpp_failing_0-10.txt
  * cpp_failing_0-9.txt
  * cpp_failing_0-8.txt
  * java_failing_0-9.txt
  * java_failing_0-8.txt
