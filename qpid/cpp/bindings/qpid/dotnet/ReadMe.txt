#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

Qpid.cpp.bindings.qpid.dotnet binding package.

1. Features
===========

A. This binding package provides a .NET Interop wrapper around the 
   Qpid C++ Messaging interface. It exposes the Messaging interface through 
   a series of managed code classes that may be used by any .NET language.

B. A sessionreceiver assembly provides session message callback functionality.

2. Prerequisites
================

A. From a fresh check-out of Qpid sources, execute an in-source CMake.
   This command puts the CMake output files in the same directories
   as the Qpid source files.
  
   > cd cpp
   > cmake -i


B. Build the qpid-cpp solution.

   > qpid-cpp.sln
     Select Configuration Debug
     Select Platform Win32
     Compile the ALL_BUILD project

3. Building the Dotnet Binding solution
=======================================

A. Open solution file cpp\bindings\qpid\dotnet\org.apache.qpid.messaging.sln
   Select Configuration Debug
   Select Platform x86
   Compile the solution


4. Running the examples
=======================

A. csharp.direct.receiver
B. csharp.direct.sender

C. csharp.map.receiver
D. csharp.map.sender

E. csharp.map.callback.receiver
F. csharp.map.callback.sender

G. csharp.example.server
H. visualbasic.example.server
I. csharp.example.client

J. csharp.example.drain
K. csharp.example.spout
L. csharp.example.declare_queues

M. csharp.example.helloworld
N. powershell.example.helloworld


5. Running the tests
====================

A. TBD


6. Notes
========

TBD   
