<!--

Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.

-->

# Qpid C++

## Introduction

Qpid C++ is a C++ implementation of the AMQP protocol described at
<http://amqp.org/>.

For additional software or information on the Qpid project go to:

> <http://qpid.apache.org>

For documentation, go to:

> <http://qpid.apache.org/documentation>

## Available documentation

 - INSTALL.txt - How to install Qpid C++
 - LICENSE.txt - The Apache license
 - NOTICE.txt  - Corresponds to the section 4 d of the Apache License,
   Version 2.0
 - docs/       - Feature and design notes, other documentation

## Quick start

In C++ distributions:

    mkdir BLD      # The recommended way to use cmake is in a separate
                   # build directory
    cd BLD
    cmake ..       # Generates code and makefiles
    make test      # Runs tests
    make install   # Installs the client and daemon

The INSTALL.txt notes contain more detailed information on compiling
and installing this software.

qpid/cpp/examples/README.txt describes the C++ client API examples.
