Qpid.cpp.bindings.qpid.dotnet binding package.

1. Features
===========

A. This binding package provides a .NET Interop wrapper around the C++ 
   Qpid Messaging interface. It exposes the Messaging interface through 
   a series of managed code classes that may be used by any .NET language.

B. A sessionreceiver assembly provides session callback functionality
   above the C++ layer.

2. Prerequisites
================

A. A build of the Qpid C++ libraries is available. 

B. Refer to this library using environment variable QPID_BUILD_ROOT.

   for example: SET QPID_BUILD_ROOT=D:\users\submitter\svn\qpid\cpp

3. Building the solution
========================

A. The solution is cpp\bindings\qpid\dotnet\org.apache.qpid.messaging.sln

B. Build the solution (Debug only - Release is not set up yet).

C. Project output goes to %QPID_BUILD_ROOT%\src\Debug. This puts all the
   solution artifacts is the same directory as the C++ DLLs.
   

4. Running the examples
======================

A. csharp.direct.receiver
B. csharp.direct.sender
C. csharp.map.receiver
D. csharp.map.sender
E. csharp.map.callback.receiver
F. csharp.map.callback.sender


5. Running the tests
====================

A. messaging.test

