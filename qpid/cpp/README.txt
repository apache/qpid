            Qpid/C++
            ========

Table of Contents
=================
1. Introduction
2. Available Documentation
3. Quick start


1. Introduction
===============
Qpid/C++ is a C++ implementation of the AMQP protcol described at
http://amqp.org/

This release of Qpid/C++ implements the AMQP 0-10. 
It will not inter-operate with AMQP 0-8/0-9 implementations.

For additional software or information on the Qpid project go to:

   http://qpid.apache.org

For documentation, go to:

   http://qpid.apache.org/documentation


2. Available Documentation
==========================
  - INSTALL       - How to install Qpid/C++.
  - SSL           - How to setup SSL
  - RELEASE_NOTES - Release notes.
  - DESIGN        - Qpid/C++ implementation.
  - LICENSE       - Apache license.
  - NOTICE        - Corresponds to the section 4 d of 
                    the Apache License, Version 2.0.

3. Quick start
==============

In C++ distributions:

   ./configure && make - compiles all sources

   make check - runs tests

   make install - installs the client and daemon

In some distributions, no ./configure file is provided. To create the
./configure file, you must have autotools installed. Run ./bootstrap
from the qpid/cpp directory, then follow the above instructions.

The INSTALL notes contain more detailed information on compiling and
installing this software.

qpid/cpp/examples/README.txt describes the C++ client API examples.
