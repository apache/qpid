= RUNNING THE PYTHON TESTS =

The tests/ directory contains a collection of python unit tests to
exercise functions of a broker.

Simplest way to run the tests:

 * Run a broker on the default port

 * ./run_tests

For additional options: ./run_tests --help


== Expected failures ==

Until we complete functionality, tests may fail because the tested
functionality is missing in the broker. To skip expected failures
in the C++ or Java brokers:

 ./run_tests -I cpp_failing.txt
 ./run_tests -I java_failing.txt

If you fix a failure, please remove it from the corresponding list.
