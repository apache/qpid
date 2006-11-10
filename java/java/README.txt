All of our user documentation for the Qpid Java components can be accessed on our
wiki at:

http://cwiki.apache.org/confluence/display/qpid/Qpid+Java+Documentation

This includes a Getting Started Guide and FAQ as well as detailed developer documentation.

However, here's a VERY quick guide to running the installed Qpid broker, omce you have installed it somewhere !

Running the Broker
------------------

To run the broker, set the QPID_HOME environment variable to
distribution directory and add $QPID_HOME/bin to your PATH. 

Then run the qpid-server shell script or qpid-server.bat batch file to start
the broker. 

You can then start the broker:

qpid-server

By default, the broker will use $QPID_HOME/etc to find
the configuration files. You can supply a custom configuration using
the -c argument.

For example:

qpid-server -c ~/etc/config.xml

You can get a list of all command line arguments by using the -h argument.

Note that the Qpid broker needs JDK 1.5 or later.

