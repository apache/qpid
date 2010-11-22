= GETTING STARTED =

1. Make sure the Qpid Python client libraries are on your PYTHONPATH:

$ export PYTHONPATH=/home/me/qpid-0.8/python

2. Make sure a broker is running

3. Run the 'hello' example from qpid-0.8/python/examples/api:

$ ./hello
Hello world!


= EXAMPLES =

The examples/api directory contains several examples. 

Read examples/README.txt for further details on these examples.


= RUNNING THE TESTS =

The "tests" directory contains a collection of unit tests for the
python client. The "tests_0-10", "tests_0-9", and "tests_0-8"
directories contain protocol level conformance tests for AMQP brokers
of the specified version.

The qpid-python-test script may be used to run these tests. It will by
default run the python unit tests and the 0-10 conformance tests:

  1. Run a broker on the default port

  2. ./qpid-python-test

If you wish to run the 0-8 or 0-9 conformence tests, they may be
selected as follows:

  1. Run a broker on the default port

  2. ./qpid-python-test tests_0-8.*

        -- or --

     ./qpid-python-test tests_0-9.*

See the qpid-python-test usage for for additional options:

  ./qpid-python-test -h
