************************************************ The Tools ************************************************

A number of Java based tools are provided, and additionally a web based GUI with underlying REST API which
are described later, utilising the components from the core API.

There are executable shell scripts included in the tools bundle that should allow the tools to be run fairly
easily. To use them, open the bin/ directory.

The available tools are:
QpidConfig: Is a Java port of the standard Python based qpid-config tool. This exercises most of the QMF2 API
            and is probably a good bet to see how things work if you want to use the API in your own projects.
QpidCtrl: Is a Java port of the qpid-ctrl tool found in qpid/cpp/src/tests. This is a little known, but useful
          little tool that lets one send low-level QMF constructs from the command line. The JavaDoc is the
          best place to look for example usage (see earlier for build instructions).
QpidPrintEvents: Is a Java port of the Python qpid-printevents and illustrates the asynchronous delivery
                 of QMF2 notification events.
QpidQueueStats: Is a Java port of the Python qpid-queue-stats. This was written mainly to illustrate the use
                of the QMF2 "QuerySubscription" API that lets one specify how to be asynchronously notified
                of changes to QMF Management Objects matching a specified set of criteria.
ConnectionAudit: Is a tool that allows one to audit connections to one or more Qpid brokers. It uses QMF
                 Events to identify when connections have been made to a broker and if so it logs information
                 about the connection. A whitelist can be specified to flag connections that you don't
                 want to have logged (e.g. ones that you like).
ConnectionLogger: Is similar to ConnectionAudit but a bit simpler this tool just logs connections being made
                  the tool is mainly there to illustrate how to dereference the associations between the
                  various QMF Management Objects (Connection, Session, Subscription, Queue, Binding Exchange etc.)
QueueFuse: Is a tool that monitors QMF Events looking for a QueueThresholdExceeded, which occurs when a queue
           gets more than 80% full. When this Event occurs the tool sends a QMF method to "purge" 10% of the
           messages off the offending queue, i.e. it acts rather like a fuse. It's mainly a bit of a toy, but
           it's a pretty good illustration of how to trigger QMF method invocation from QMF Events. It would
           be pretty easy to modify this to redirect messages to a different queue if a particular queue fills.
QpidRestAPI: This is a Web Service that exposes QMF2 via a REST API, see "The GUI" section below for details.



************************************************* The GUI *************************************************

Included in the tools package, there is a fairly comprehensive Web based GUI available for Qpid that works
with the C++ Broker and also the Java Broker if the QMF management plugin has been installed (see the
related plugin README.txt for more details).

The GUI is in the form of a pure client side "single page" Web App written in JavaScript that uses the
QpidRestAPI to proxy the QMF API, and also serve up the GUI.

There is comprehensive JavaDoc for the QpidRestAPI (see source release for build instructions), where
the most useful classes to look at are:
QpidRestAPI: This describes the various command line options available.
QpidServer: This provides documentation for the actual REST API itself, in effect the REST mapping for QMF

QpidRestAPI provides a fairly complete REST mapping for QMF, it was primarily written as the back-end to
the GUI, but there's no reason why it couldn't be used in its own right.

To get started, after you have extracted the tools release as described earlier, the simplest and probably
most common use case can be kicked offby changing into the bin/ directory and firing up the REST API via:
./QpidRestAPI

This will bind the HTTP port to 8080 on the "wildcard" address (0.0.0.0). The QMF connection will default to
the host that QpidRestAPI is running on and use the default AMQP port 5672.

If you point a Browser to <host>:8080 the GUI should start up asking for a User Name and Password, the
defaults for those are the rather "traditional" admin admin.


If you have a non-trivial broker set-up you'll probably see "Failed to Connect", which is most likely due
to having authentication enabled (you can check this by firing up the C++ broker using qpidd --auth no)


There are a few ways to configure the Brokers that you can control via the GUI:
The first way is to specify the -a (or --broker-addr) command line option e.g.
./QpidRestAPI -a guest/guest@localhost

This option accepts the Broker Address syntax used by the standard Python tools and it also accepts the
Java ConnectionURL syntax specified here (though to be honest the syntax used by the Python tools is simpler)
http://qpid.apache.org/releases/qpid-0.24/programming/book/QpidJNDI.html#section-jms-connection-url


This way of specifying the AMQP address of the default broker that you want to manage is probably the best
approach, but it is possible to add as many QMF Console Connections as you like by clicking
"Add QMF Console Connection" on the GUI Settings page. The popup lets you specify the Address URL such as
"guest/guest@host:5672" - again it also accepts the JMS Connection URLs, though I only use them if I'm
doing a copy/paste of an existing Connection URL.
The Name is simply a "friendly name" that you want to use to identify a particular Broker.


Clearly if you want to be able to manage a number of brokers you'd probably prefer not to have to enter
them every time you fire up the GUI - particularly because the list gets wiped if you hit refresh :-)

The good news is that the initial set of Console Connections is configurable via the file:
bin/qpid-web/web/ui/config.js


This is a simple JSON file and it contains example Console Connection configuration including a fairly complex one

If you use this mechanism to configure the GUI you can quickly switch between however many Brokers
you'd like to be able to control.


As mentioned above the default User Name and Password are admin and admin, these are set in the file
bin/qpid-web/authentication/account.properties


It's worth pointing out that at the moment authentication is limited to basic uthentication. This is mainly
due to lack of time/energy/motivation to do anything fancier (I only tend to use it on a private network)
I also had a need to minimise dependencies, so the Web Server is actually based on the Java 1.6
com.sun.net.httpserver Web Server.


In practice though basic authentication shouldn't be as much of a restriction as it might sound especially
if you're only managing a single Broker.

When one fires up QpidRestAPI with the -a option the Broker connection information does not pass between the
GUI and the QpidRestAPI so it's ultimately no less secure than using say qpid-config in this case though
note that if one configures multiple Brokers via config.js the contents of that file get served to the GUI
when it gets loaded so you probably want to restrict use of the GUI to the same network you'd be happy to
run qpid-config from.



*********************************************** Important!! ***********************************************
*  If your Qpid C++ broker is older than 0.10 the QMF2 API won't work unless your setup is as follows:    *
*********************************************** Important!! ***********************************************

Note that if you are talking to a broker < Qpid 0.10
you need to set "--mgmt-qmf2 yes" when you start up qpidd if you want to get QMF2 Events and heartbeats pushed.
This is particularly important to note if you are using the Qpid GUI, as in default mode its updates are
triggered by the QMF2 heartbeats. If "--mgmt-qmf2 yes" isn't set on a 0.8 broker you'll see "Broker Disconnected"
flash briefly every 30 seconds or so as timeouts occur. Creating a QMF Console Connecton in the GUI with
"Disable Events" selected uses a timed poll rather than a heartbeat so it may be better to do that for cases
where access to the broker configuration is not available.

***********************************************************************************************************

Note 1: This uses QMF2 so requires that the "--mgmt-qmf2 yes" option is applied to the broker (this is
        the default from Qpid 0.10 onwards)

Note 2: In order to use QMF2 the app-id field needs to be set. This requires the Qpid 0.12+ Java client
