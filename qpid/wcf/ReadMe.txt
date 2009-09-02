1. WCF supported features
=========================

1.  WCF service model programming using one way contracts
2.  WCF channel model programming using IInputChannel and IOutputChannel based factories
3.  Programmatic access to AMQP message properties on WCF messages
4.  AMQP version 0-10 (as provided by the Qpid C++ native client library)
5.  Shared connections for multiple channels based on binding parameters
6.  WCF to WCF applications (using SOAP message encoders)
7.  WCF to non-WCF applications (using raw content encoders)
8.  Rudimentary AMQP type support for headers (Int and String)
9.  Channel functional tests using NUnit
10. Programming samples


2. Planned features (not yet available)
=======================================

1.  Full AMQP type support, including maps and arrays
2.  System.Transactions integration (local and distributed with dynamic escalation)
3.  Prefetch window for inbound messages
4.  Shared sessions
5.  Connection failover with AMQP broker clusters
6.  Temporary queues
7.  Broker management
8.  System logging and tracing
9.  CMake build system support
10. Transport and message based security


3. Prerequisites
================

1. Qpid C++ client and common libraries for Windows including BOOST
Ensure the location of the Boost library (e.g. %BOOST_ROOT%\lib) is
included in your PATH environment variable.

2. .NET Framework 3.5 SP1
Install the .NET Framework from http://www.microsoft.com/net/

3. Windows SDK
Install the Windows SDK for the version of Windows that you are using
from http://msdn.microsoft.com/en-us/windows/bb980924.aspx

4. NUnit
Install NUnit from http://www.nunit.org

NOTE: In the following instructions %QPID_ROOT% refers to the root of
qpid source code location e.g. C:\trunk\qpid

5. Build Qpid cpp
Run CMake and choose "%QPID_ROOT%\cpp\build" as the location for "Where to
build the binaries".  Build at least the "qpidd", "qpidclient" and
"qpidcommon" projects.


4. Building the solution file
=============================

Option 1: Using MSBuild

1. %systemroot%\Microsoft.NET\Framework\v3.5\MSBuild.exe %QPID_ROOT%\wcf\QpidWcf.sln
2. %systemroot%\Microsoft.NET\Framework\v3.5\MSBuild.exe %QPID_ROOT%\wcf\tools\QCreate\QCreate.sln


Option 2: Using Visual Studio 2008 (the Professional Edition, Team
System Development Edition, or Team System Team Suite SKU)

1. Open the solution file QpidWcf.sln in Visual Studio.
2. Make sure that the reference to 'nunit.framework.dll' by the 'FunctionalTests'
   project is appropriately resolved.
3. Select the Debug configuration.
3. Right-click the solution file in the Solution Explorer and select 'Build Solution'.
4. Follow the above steps to build %QPID_ROOT%\wcf\tools\QCreate.sln as well.


5. Executing tests
==================

1. Make sure that the batch file
   %QPID_ROOT%\wcf\test\Apache\Qpid\Test\Channel\Functional\RunTests.bat has the correct
   values for the nunit_exe, qpid_dll_location and configuration_name variables as per
   your installation.
2. Start the qpid broker from the qpid build folder e.g. %QPID_ROOT%\cpp\build\src\Debug.
3. Execute RunTests.bat from its location e.g. %QPID_ROOT%\wcf\test\Apache\Qpid\Test\Channel\Functional.


6. Building and executing samples
=================================

WCFToWCFDirect

1. Copy the dlls Apache.Qpid.Channel.dll and Apache.Qpid.Interop.dll that you built
   in step 2 to the %QPID_ROOT%\wcf\samples\Channel\WCFToWCFDirect folder.

2. Build the solution WCFToWCFDirect.sln.

3. Copy qpidclient.dll and qpidcommon.dll from the Qpid build folder
   e.g. %QPID_ROOT%\cpp\build\src\Debug to the same location as the exe files
   e.g. bin\Debug of each of the projects. These dlls are needed at runtime.

4. Copy qpidclient.dll and qpidcommon.dll to %QPID_ROOT%\wcf\tools\QCreate\Debug folder.

5. Start the qpid broker from the qpid build folder e.g. %QPID_ROOT%\cpp\build\src\Debug.

6. Create queue required using the QCreate tool located at
   %QPID_ROOT%\wcf\tools\QCreate\Debug. The syntax is QCreate %QPID_ROOT%. For
   this sample you should do 

      QCreate amq.direct routing_key message_queue

7. Start Service.exe from
      %QPID_ROOT%\wcf\samples\Channel\WCFToWCFDirect\Service\bin\Debug.

8. Start Client.exe from
      %QPID_ROOT%\wcf\samples\Channel\WCFToWCFDirect\Client\bin\Debug.


WCFToWCFPubSub

1. Copy the dlls Apache.Qpid.Channel.dll and Apache.Qpid.Interop.dll that you built
   in step 2 to the %QPID_ROOT%\wcf\samples\Channel\WCFToWCFPubSub folder.

2. Build the solution WCFToWCFPubSub.sln.

3. Copy qpidclient.dll and qpidcommon.dll from the Qpid build folder
   e.g. %QPID_ROOT%\cpp\build\src\Debug to the same location as the exe files
   e.g. bin\Debug of each of the projects. These dlls are needed at runtime.

4. Copy qpidclient.dll and qpidcommon.dll to %QPID_ROOT%\wcf\tools\QCreate\Debug folder.

5. Start the qpid broker from the qpid build folder e.g. %QPID_ROOT%\cpp\build\src\Debug.

6. Create queues required using the QCreate tool located at
   \wcf\tools\QCreate\Debug. The syntax is QCreate %QPID_ROOT%. For this sample you
   should do

	QCreate amq.topic usa.# usa
	QCreate amq.topic #.news news

7. Start Topic_Consumer.exe from
   %QPID_ROOT%\wcf\samples\Channel\WCFToWCFPubSub\Topic_Consumer\bin\Debug.

8. Start Another_Topic_Consumer.exe from
   %QPID_ROOT%\wcf\samples\Channel\WCFToWCFPubSub\Another_Topic_Consumer\bin\Debug.

9. Start Topic_Producer.exe from
   %QPID_ROOT%\wcf\samples\Channel\WCFToWCFPubSub\Topic_Producer\bin\Debug.


7. Known Issues
===============

1. The Release configuration of the build (specified using the
   /p:Configuration=Release switch with MSBuild) fails.

2. The AmqpChannelListener is limited to single threaded use and the async methods
   throw NotImplementedException.

3. The AmqpChannelListener can hang on close for 60 seconds.


