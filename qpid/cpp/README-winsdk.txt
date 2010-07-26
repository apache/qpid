			Qpid-Cpp-Win-Sdk
			================

Table of Contents
=================
1. Introduction
2. Prerequisites
3. Kit contents
4. Notes


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
		
  \management
    A python scripting code set for generating QMF data structures.
    
    For more information on Qpid QMF go to:
    https://cwiki.apache.org/qpid/qpid-management-framework.html

	
4. Notes
========
* The Qpid-Cpp binaries are produced for the 32-bit Win32 platform.

* Only the Release variant of Qpid code uses the redistributable 
  MSVC90 libraries in the /bin directory. Users who wish to link to 
  the Debug variant of Qpid code may do so under their own copy of 
  Visual Studio 2008 where the debug versions of MSVC90 runtime 
  libraries are available.
