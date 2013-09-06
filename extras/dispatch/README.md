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
$ make test

Note:  Your PYTHONPATH _must_ include <dispatch>/python in its list of paths in order
to test and run Dispatch.

