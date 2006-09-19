Running the Broker
------------------

To run the broker, set the QPID_HOME environment variable to
distribution directory and add $QPID_HOME/bin to your PATH. Then run
the qpid-server shell script or qpid-server.bat batch file to start
the broker. By default, the broker will use $QPID_HOME/etc to find
the configuration files. You can supply a custom configuration using
the -c argument.

For example:

qpid-server -c ~/etc/config.xml

You can get a list of all command line arguments by using the -h argument.
