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

   cmake .        # Generates code and makefiles
   make test      # Runs tests
   make install   # Installs the client and daemon

The INSTALL notes contain more detailed information on compiling and
installing this software.

qpid/cpp/examples/README.txt describes the C++ client API examples.
