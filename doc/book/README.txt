The documentation in this directory is written in DocBook 4.5. The
original content was taken from the Apache Qpid Wiki. 

1. Building the Documentation

To build the documentation, use ant:

$ ant

You need both ant and ant-trax, and xmllint (or another XInclude
processor - if you do not use xmllint, you must change build.xml). On
Fedora, you can get them using yum:

$ sudo yum install ant ant-trax xmllint

The other tools that you need are in subdirectories:

qpid/doc/book/docbook      - Docbook 4.5 DTDs
qpid/doc/book/docbook-xsl  - Docbook XSLT stylesheets
qpid/doc/book/lib/saxon    - Saxon 6.5.3 XSLT processor
qpid/doc/book/lib/fop-0.95 - Apache FOP

You will see quite a few error messages. Many of these are due to
unresolved links, and these should go away. Many are due to the
verbosity of Apache FOP, which generates many warnings.

2. Editing Tools

For Emacs, I like nxml-mode, especially if you learn how to use tag
completion, outlining, etc.  This is described in some detail in
http://www.dpawson.co.uk/relaxng/nxml/info.html. 

For vi, the macros described in this Linux Journal article may be
helpful: http://www.linuxjournal.com/article/7737.

Commercial XML editors provide good support for DocBook. On Windows, I
like Stylus Studio (http://www.stylusstudio.com/). On Linux, I like
Oxygen (http://www.oxygenxml.com/).

Here's a page on authoring tools for DocBook:
http://wiki.docbook.org/topic/DocBookAuthoringTools


3. File Structure

The source files are in qpid/doc/book/src.

The following XInclude tree shows the organization of files in the
document.

Book.xml
   Book-Info.xml
   Introduction.xml
   AMQP.xml
   Getting-Started.xml
   Download.xml
   AMQP-Messaging-Broker-CPP.xml
      Running-CPP-Broker.xml
      Cheat-Sheet-for-configuring-Queue-Options.xml
      Cheat-Sheet-for-configuring-Exchange-Options.xml
      Using-Broker-Federation.xml
      SSL.xml
      LVQ.xml
      queue-state-replication.xml
      Starting-a-cluster.xml
      ACL.xml
      Managing-CPP-Broker.xml
      QMan-Qpid-Management-bridge.xml
      Qpid-Management-Framework.xml
      Management-Design-notes.xml
      QMF-Python-Console-Tutorial.xml
   AMQP-Messaging-Broker-Java.xml
      Java-Broker-Feature-Guide.xml
      Qpid-Java-FAQ.xml
      Java-Environment-Variables.xml
      Qpid-Troubleshooting-Guide.xml
      Add-New-Users.xml
      Configure-ACLs.xml
      Configure-Java-Qpid-to-use-a-SSL-connection.xml
      Configure-Log4j-CompositeRolling-Appender.xml
      Configure-the-Broker-via-config.xml.xml
      Configure-the-Virtual-Hosts-via-virtualhosts.xml.xml
      Debug-using-log4j.xml
      How-to-Tune-M3-Java-Broker-Performance.xml
      Qpid-Java-Build-How-To.xml
      Use-Priority-Queues.xml
      Qpid-JMX-Management-Console.xml
         Configuring-Management-Users.xml
         Configuring-Qpid-JMX-Management-Console.xml
         Management-Console-Security.xml
         Qpid-JMX-Management-Console-FAQ.xml
         Qpid-JMX-Management-Console-User-Guide.xml
         Qpid-Management-Features.xml
      MessageStore-Tool.xml
      Qpid-Java-Broker-Management-CLI.xml
   AMQP-Java-JMS-Messaging-Client.xml
      System-Properties.xml
      Connection-URL-Format.xml
      Binding-URL-Format.xml
   AMQP-C++-Messaging-Client.xml
   AMQP-.NET-Messaging-Client.xml
      NET-User-Guide.xml
      Excel-AddIn.xml
      WCF.xml
   AMQP-Python-Messaging-Client.xml
      PythonBrokerTest.xml
   AMQP-Ruby-Messaging-Client.xml
   AMQP-Compatibility.xml
   Qpid-Interoperability-Documentation.xml
