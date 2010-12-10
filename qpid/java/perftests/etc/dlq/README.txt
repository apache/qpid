h1. Dead Letter Queue Performance Testing

It is important to be able to understand how the addition of Dead Letter Queue
capabilities (DLQs) affects the Qpid performance. This is because the main body
of the work to determine whether a message should be re-delivered or enqueued
on the DLQ takes places on the main message delivery path, and will therefore
be called during *every* mesage transit of the broker. This has the potential
to increase latency, and these tests are designed to allow developers to
understand how various DLQ parameters and options affect throughput and
latency times.

The results are generated as a series of CSV text files, based on running the
same DLQ performance test with every possible combination of a selection of
options. It is also possible to run a sequence of tests with a static set of
configuration options, or to run a test once, again using static options. The
test configuration is designed to be programmatically altered dynamically,
to allow suites of tests to be created as described above.

When a sequence of tests with a particular configuration is executed, there
are two CSV files created. The first, {{XXXX-series.csv}} contains the raw
results of each test run in the series. The second, {{XXXX-statistics.csv}}
has generated statistics based on the raw data, including the mean, standard
deviation, and 95% confidence interval limits. In these files, _XXXX_ is a
unique number, referenced in {{framework.csv}} which lists all the test ids
along with their dynamic configuration options. Finally, logging output
during the run shows the progress of the testing.

h1. Operation

The test framework classes are part of the {{org.apache.qpid.perftests.dlq}}
package. Under this, the {{test}} subpackage contains three classes, each with
a {{main()}} method, that can be run from the command line. Each of these takes
the name of a configuration property file as an argument. They are:

* {{PerformanceTest}} - runs a single test
* {{PerformanceStatistics}} - runs a test mutiple times and generates statistics
* {{PerformanceFramework}} - sets up configuration permutations to run many tests

The final framework class can also be run using the {{Framework.sh}} script
found in this directory. Additionally, a sample test configuration file,
{{config.properties}} illustrates all configuration options, and the {{log4j.xml}}
file can be customised to add debug logging if required.
