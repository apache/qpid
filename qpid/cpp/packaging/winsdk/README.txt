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

            Qpid-Cpp-Win-Sdk
            ================

Table of Contents
=================
1. Introduction
2. Prerequisites
3. Kit contents
4. Building unmanaged C++ examples
5. Building dotnet_examples
6. Notes


1. Introduction
===============
Qpid-Cpp-Win-Sdk is a software development kit for users who wish
to write code using the Qpid-Cpp program libraries in a Windows
environment.

This kit is distributed as four zip files:
    qpid-cpp-x86-VS2008-<version>.zip - projects and libraries for 32-bit
                                        x86 and Win32 development using
                                        Visual Studio 2008.
    qpid-cpp-x64-VS2008-<version>.zip - projects and libraries for 64-bit
                                        x64 development using
                                        Visual Studio 2008.
    qpid-cpp-x86-VS2010-<version>.zip - projects and libraries for 32-bit
                                        x86 and Win32 development using
                                        Visual Studio 2010.
    qpid-cpp-x64-VS2010-<version>.zip - projects and libraries for 64-bit
                                        x64 development using
                                        Visual Studio 2010.
    qpid-cpp-x86-VS2012-<version>.zip - projects and libraries for 32-bit
                                        x86 and Win32 development using
                                        Visual Studio 2012.
    qpid-cpp-x64-VS2012-<version>.zip - projects and libraries for 64-bit
                                        x64 development using
                                        Visual Studio 2012.

For additional software or information on the Qpid project go to:
http://cwiki.apache.org/qpid/


2. Prerequisites
================
A. Visual Studio 2008, 2010, or 2012. The kits were produced with the
   corresponding version of Visual Studio and provide a matched
   set of link libraries for each tool chain.
   
B. MSVC runtime libraries. Copies of the MSVC redistributable runtime
   libraries and manifest are included in the \bin\release directories.
   
C. Boost version 1_55. The Boost libraries required by this SDK are
   included in the \bin\debug and \bin\release directories.
   
D. CMake version 2.8.11 or later, available for free from http://cmake.org/ 
   CMake generates custom Visual Studio solutions and projects for
   the unmanaged C++ examples.


3. Kit contents
===============
The kit directories hold the content described here.

  \bin 
    The precompiled binary (.dll and .exe) files and
      the associated debug program database (.pdb) files.
    Boost library files.
    MSVC runtime library files are in \bin\release.

  \include
    A directory tree of .h files.

  \lib
    The linker .lib files that correspond to files in /bin.

  \docs
    Apache Qpid C++ API Reference

  \examples
    Source files which demonstrate using this SDK in unmanaged C++.

  \dotnet_examples
    A Visual Studio solution file and associated project files 
    to demonstrate using this SDK in C#.

  \management
    A python scripting code set for generating QMF data structures.
    
    For more information on Qpid QMF go to:
    http://qpid.apache.org/components/qmf/index.html

The architectural relationships of the components in this SDK are 
illustrated here.

                      +----------------------------+
                      | \dotnet_examples           |
                      | Managed C#                 |
                      +------+---------------+-----+
                             |               |
                             V               |
        +---------------------------+        |
        | Managed Callback          |        |
        | org.apache.qpid.messaging.|        |
        | sessionreceiver.dll       |        |
        +----------------------+----+        |
                               |             |
managed                        V             V
(.NET)                 +-------------------------------+
:::::::::::::::::::::::| Qpid Interop Binding          |::::::::::::
unmanaged              | org.apache.qpid.messaging.dll |
(Native Win32/64)      +---------------+---------------+
                                       |
                                       |
      +----------------+               |
      | \examples      |               |
      | Unmanaged C++  |               |
      +--------+-------+               |
               |                       |
               V                       V
          +----------------------------------+
          |  Qpid C++ Messaging Libraries    |
          |  bin\qpid*.dll bin\qmf*.dll      |
          +--------+--------------+----------+
                   |              |
                   V              |
          +-----------------+     |
          | Boost Libraries |     |
          +--------+--------+     |
                   |              |
                   V              V
          +---------------------------------+
          | MSVC Runtime Libraries          |
          +---------------------------------+


4. Building unmanaged C++ examples
==================================

This version of Qpid-Cpp-Win-Sdk ships with no pre-built Visual Studio
solution or project files for the C++ examples. Instead this kit has
support for using CMake to generate the solution and project files.

A. Make sure that the CMake bin directory is defined in your path.
   You may check this from a command prompt by typing:
     > cmake --version
     cmake version 2.8.11
   
   If CMake is installed correctly it will respond with a version number.
   
B. Change directory to \examples\examples-cmake.

C. Execute run-cmake.bat batch file.

   Run-cmake.bat runs CMake and generates the Visual Studio solution and
   project files using the version of Visual Studio that matches this 
   Qpid-Cpp-Win-Sdk kit.

D. Execute the generated examples.sln Visual Studio solution file.


5. Building dotnet_examples
===========================

From the \dotnet_examples directory launch the winsdk_dotnet_examples.sln
solution file. In the platform pulldown list select "x86" or "x64" to
match the development kit you are using. Then build the solution in the
Debug or Release configuration.


6. Notes
========
* Only the Release variant of Qpid code uses the redistributable 
  MSVC libraries in the /bin/release directory. Users who wish to link to 
  the Debug variant of Qpid code may do so under their own copy of 
  Visual Studio where the debug versions of MSVC90, MSVC100, or MSVC110
  runtime libraries are available.
  
