= RUNNING THE PYTHON TESTS =

The tests/ directory contains a collection of python unit tests to
exercise functions of a broker.

Simplest way to run the tests:

 * Run a broker on the default port

 * ./run-tests

For additional options: ./run-tests --help


== Expected failures ==

Until we complete functionality, tests may fail because the tested
functionality is missing in the broker. To skip expected failures
in the C++ or Java brokers:

 ./run-tests -I <file_name>

=== File List ===

1. cpp_failing_0-10.txt
2. cpp_failing_0-9.txt
3. cpp_failing_0-8.txt
4. java_failing_0-9.txt
5. java_failing_0-8.txt
6. cpp_failing_0-10_preview.txt  -- will be depricated soon.

If you fix a failure, please remove it from the corresponding list.
