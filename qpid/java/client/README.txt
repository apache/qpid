Documentation
=============

You can access documentation for the client via our website at:
http://qpid.apache.org/documentation

and via our wiki at:
http://cwiki.apache.org/confluence/display/qpid/Qpid+Java+Documentation

The client uses the Java Message Service (JMS) 1.1 API, information on which is
widely available using your favoured search engine.


Running the Examples:
=====================

1. From the client Binary distribution:

From the <installation path>/qpid-client-<version> directory, there are examples
provided in source form in the example/src sub-directory. These are also
provided in binary form in the example/lib directory in order that they can be
run more easily.

E.g, in order to run the Hello example, you would add the client+example library
files to the java classpath and launch the example like follows:

java -cp "lib/qpid-all.jar:example/lib/qpid-client-example-<version>.jar" \
    org.apache.qpid.example.Hello

NOTE: The client uses the SL4FJ API for its logging. You must supply a logging 
implementation of your choice (eg Log4J) and its associated SLF4J binding, by 
also adding them to the Java classpath as well as the client libraries 
themselves. Failure to do so will result in a warning being output and use of
NoOp logging by the client.

More information on using SLF4J is available at http://www.slf4j.org/manual.html
which details some of the supported logging implementations and their
associated SLF4 bindings as available in the SLF4J distribution.



2. From the Source distribution / repository:

Run 'ant build' in the parent directory from where this file is stored, ie:
<installation path>/qpid/java

This will build the various Java modules, leaving binary .jar files output in:
<installation path>/qpid/java/build/lib

Taking the above the 'distribution directory', consult the README.txt file at:
<installation path>/qpid/java/client/example/src/main/java
