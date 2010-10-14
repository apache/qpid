            Qpid-Cpp-Win-Sdk
            ================

Table of Contents
=================
1. Introduction
2. Prerequisites
3. Kit contents
4. Building dotnet_examples
5. Notes


1. Introduction
===============
Qpid-Cpp-Win-Sdk is a software development kit for users who wish
to write code using the Qpid-Cpp program libraries in a Windows
environment.

This kit is distributed as two zip files:
    qpid-cpp-x86-<version>.zip - projects and libraries for 32-bit
                                 x86 and Win32 development.
    qpid-cpp-x64-<version>.zip - projects and libraries for 64-bit
                                 x64 development.

For additional software or information on the Qpid project go to:
http://cwiki.apache.org/qpid/


2. Prerequisites
================
A. Visual Studio 2008. This kit was produced by Visual Studio 2008
   and example solutions and projects are in Visual Studio 2008
   format.
   
B. MSVC 9.0 runtime libraries. Copies of the MSVC90 redistributable
   runtime libraries and manifest are included in the /bin directory.
   
C. Boost version 1_39. The Boost libraries required by this SDK are
   included in the /bin directory. Both Debug and Release variants
   are present.


3. Kit contents
===============
The kit directories hold the content described here.

  \bin 
    The precompiled binary (.dll and .exe) files and
      the associated debug program database (.pdb) files.
    Boost library files.
    MSVC90 runtime library files.

  \include
    A directory tree of .h files.

  \lib
    The linker .lib files that correspond to files in /bin.

  \docs
    Apache Qpid C++ API Reference

  \examples
    A Visual Studio solution file and associated project files 
    to demonstrate using this SDK in unmanaged C++.

  \dotnet_examples
    A Visual Studio solution file and associated project files 
    to demonstrate using this SDK in C#.

  \management
    A python scripting code set for generating QMF data structures.
    
    For more information on Qpid QMF go to:
    https://cwiki.apache.org/qpid/qpid-management-framework.html

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


4. Building dotnet_examples
===========================

From the \dotnet_examples directory launch the winsdk_dotnet_examples.sln
solution file. In the platform pulldown list select "x86" or "x64" to
match the development kit you are using. Then build the solution in the
Debug configuration.

The resulting executable programs may be run from within Visual Studio
or stand-alone from the \bin directory.

To build the examples in the Release configuration please follow these steps:
  a. Exit from Visual Studio. Stop all executing example processes.
  b. Extract two org.apache.qpid.messaging DLL files from 
     bin/dotnet-binding-release.zip and place the files in the /bin 
     directory, overwriting the files arleady in /bin.
  c. Start winsdk_dotnet_examples.sln.
  d. In the Configuration pulldown, select Release.
  e. In the Platform pulldown, select x86 or x64 to match the SDK
     in use.
  f. For each project remove the Reference to org.apache.qpid.messagingd.dll
     and add a reference to bin/org.apache.qpid.messaging.dll.
  g. Build and run the project.

5. Notes
========
* Only the Release variant of Qpid code uses the redistributable 
  MSVC90 libraries in the /bin directory. Users who wish to link to 
  the Debug variant of Qpid code may do so under their own copy of 
  Visual Studio 2008 where the debug versions of MSVC90 runtime 
  libraries are available.
  