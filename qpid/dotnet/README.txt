Info
====

There are two seperate .Net clients.

One implements the AMQP 0-10 protocol and is located in the client-010
directory, this can communicate with the C++ broker.

Instructions for building and installing the 0-10 client are located in client-010/README.txt.


The other, located in the root of the dotnet directory implements the
0-8 protocol, which can communicate with the Java broker.

Instructions for building and installing the 0-9 client are below.



NOTE: The rest of this file is 0-8 specific 
      For 0-10 go to client-010/README.txt

Setup
=====

Install:
  Microsoft Visual Studio 2005 (VS2005) or Mono 2.0
  NAnt 0.85 - only required for builds outside VS2005 (.net 1.1, .net 2.0, mono 2.0)
  Ant 1.6.5
  Cygwin (or alternatively build via cmd but alter instructions below accordingly)

Set up PATH to include Nant.exe:

  $ PATH=/cygdrive/c/WINDOWS/Microsoft.NET/Framework/v2.0.50727:$PATH

Set up PATH to include ant:

  $ PATH=$ANT_HOME/bin:$PATH


Building
========

Generate framing from Qpid.Common/amqp.xml specificiation file:

  $ build-framing

Alternatively, just switch to /Qpid.Common and run "ant" there.

You can build from Visual Studio 2005 normally. Alternatively, you
can build debug releases for any supported framework from the 
command line using Nant:

To build .NET 2.0 executables (to bin/net-2.0):

  $ build-dotnet20

To build .NET 1.1 executables (to bin/net-1.1):

  $ build-dotnet11

To build for Mono on Linux (to bin/mono-2.0):

  $ build-mono


Releasing
=========

For .NET 1.1

  $ release net-1.1

Generates ./bin/net-1.1/release/Qpid.NET-net-1.1-yyyyMMdd.zip

For .NET 2.0

  $ release net-2.0

Generates ./bin/net-2.0/release/Qpid.NET-net-2.0-yyyyMMdd.zip

For Mono

  $ release mono-2.0

Generates ./bin/mono-2.0/release/Qpid.NET-mono-2.0-yyyyMMdd.zip
