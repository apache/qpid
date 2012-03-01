The QMF console library provides an interface infrastructure for
broker management. This directory contains example applications that
use the QMF console library to interact with the broker.

cluster-qmon.cpp:
This example maintains connections to a number of brokers (assumed to
be running on localhost and at ports listed in the command line
arguments).  The program then periodically polls queue information
from a single operational broker.  This is a useful illustration of
how one might monitor statistics on a cluster of brokers.

console.cpp:
A simple application that attaches to a broker (assumed to be
available as "localhost:5672"), and extracts:
  * Management Schema (packages and classes)
  * Management state for all active Exchanges
  * Management state for all active Queues
It then invokes the broker's "echo" test method before detaching from
the broker.

ping.cpp:
Connects to a broker (assumed to be available as "localhost:5672"),
and invokes the "echo" test method five times.

printevents.cpp:
An example of a passive broker monitor.  This application connects to
a broker (assumed to be available as "localhost:5672"), and registers
a callback listener.  The listener's callbacks are invoked when
various management events are signalled by the broker.

queuestats.cpp:
An example of a passive broker monitor.  This application connects to
a broker (assumed to be available as "localhost:5672"), and registers
a callback listener for status updates only for Queue objects.  The
listener's callback is invoked whenever the status of a Queue
changes.



