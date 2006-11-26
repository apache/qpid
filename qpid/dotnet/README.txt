Info
====

AMQP version currently 0.8 (see /Qpid.Common/amqp.xml)


Setup
=====

Install:
  Microsoft Visual Studio 2005 (VS2005)
  MsBee 1.0 (Visual Studio plugin for targetting .NET 1.1) - only required if you want to build .NET 1.1. binaries.
  Ant 1.6.5
  Cygwin (or alternatively build via cmd but alter instructions below accordingly)

Set up PATH to include MSBuild.exe:

  $ PATH=/cygdrive/c/WINDOWS/Microsoft.NET/Framework/v2.0.50727:$PATH

Set up PATH to include ant:

  $ PATH=$ANT_HOME/bin:$PATH


Building
========

Generate framing from /Qpid.Common/amqp.xml specificiation file:

  $ build-framing

To build .NET 2.0 executables (to bin/Release):

  $ build

To build .NET 1.1 executables via MsBee (to bin/FX_1_1/Debug):

  $ build-dotnet11

To build for Mono on Linux (to build/mono20):

  $ build-mono20


Releasing
=========

For .NET 1.1

  $ release dotnet11 1.0M1

Generates ./build/release/Qpid.NET-1.0M1-dotnet11.zip

For .NET 2.0

  $ release dotnet20 1.0M1

Generates ./build/release/Qpid.NET-1.0M1-dotnet20.zip

For Mono

  $ release mono20 1.0M1

Generates ./build/release/Qpid.NET-1.0M1-mono20.zip

