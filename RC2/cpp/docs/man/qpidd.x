[NAME]
qpidd \- the Qpid AMQP broker daemon

[DESCRIPTION]

Start the AMQP broker. The broker options can be specified on the command line (e.g. --worker-threads 10), in an environment variable (e.g. export QPID_WORKER_THREADS=10), or as a line in a configuration file (e.g. worker-threads=10).

Command line options take precedence over environment variables, which
take precedence over the config file.







