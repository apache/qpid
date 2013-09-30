Qpid Dispatch
=============

A lightweight AMQP router for building scalable, available, and performant messaging
interconnect.

Building
========

From the dispatch directory:

$ mkdir build
$ cd build
$ cmake ..
$ make
$ make test # see below

Note:  Your PYTHONPATH _must_ include <dispatch>/python in its list of paths in order
to test and run Dispatch.

Running The Tests
=================

Prior to running the unit tests, you should source the file config.sh which is
found in the root directory.

$ . config.sh

The file sets up the environment so that the tests can find the Python
libraries, etc.
