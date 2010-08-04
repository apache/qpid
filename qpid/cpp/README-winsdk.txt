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
    to demonstrate using this SDK in C++.

  \dotnet_examples
    A set of example source files written in C#, Visual Basic, and
    PowerShell.

  \management
    A python scripting code set for generating QMF data structures.
    
    For more information on Qpid QMF go to:
    https://cwiki.apache.org/qpid/qpid-management-framework.html


4. Building dotnet_examples
===========================

Each file in the \dotnet_examples directory is a stand-alone, main
console program that illustrates some facet of programming the Qpid 
Messaging API. Use the following steps to create a project that
builds and executes an example csharp program.

A. Assume that the WinSdk was downloaded to d:\winsdk.
B. Start Visual Studio
C. Add File->New->Project...
   1. Select C#, Console Application
   2. Name: csharp.direct.receiver
   3. Location: D:\winsdk\dotnet_examples
   4. Check: "Create directory for solution"
   5. Press OK
D. In Solution Explorer
   1. Delete program.cs
   2. Add->Existing Item. 
      Select d:\winsdk\dotnet_examples\csharp.direct.receiver.cs
   3. Add Reference to d:\winsdk\bin\org.apache.qpid.messaging.dll
      Note: In each source file a 'using' statement selects
      Org.Apache.Qpid.Messaging, Org.Apache.Qpid.Messaging.Sessionreceiver,
      or both. Resolve these statements with references to the files
      in \bin.
   4. Right-click the project and select Properties
      Select the Build tab.
      Select Configuration pulldown entry "All Configurations"
      Set the Output Path to "d:\winsdk\bin"
E. Right-click the solution and select Configuration Manager
   1. Select Configuration -> Debug
   2. Select Platform -> <new>
      pick x86, OK
F. In the standard toolbar verify that
   1. Configuration selects Debug
   2. Platform selects x86
G. Build the solution.
   1. The solution should build with no errors or warnings.
H. Verify that the solution placed the executables in the proper place:
   1. Directory d:\winsdk\bin has files csharp.direct.receiver.exe,  
      csharp.direct.receiver.pdb, and csharp.direct.receiver.vshost.exe.

The solution is now ready to execute. You may set breakpoints in the
Visual Studio source file or run the executable directly from 
d:\winsdk\bin.

This process may be repeated for each example csharp source file. A similar
process is followed for the Visual Basic example. The PowerShell example
is executed directly in a PowerShell window.

5. Notes
========
* The Qpid-Cpp binaries are produced for the 32-bit Win32 platform.

* Only the Release variant of Qpid code uses the redistributable 
  MSVC90 libraries in the /bin directory. Users who wish to link to 
  the Debug variant of Qpid code may do so under their own copy of 
  Visual Studio 2008 where the debug versions of MSVC90 runtime 
  libraries are available.
