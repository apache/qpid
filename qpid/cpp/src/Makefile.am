include gen/gen-src.mk

BUILT_SOURCES = $(generated_sources) $(generated_headers)

SUBDIRS = . tests

AM_CXXFLAGS = $(WARNING_CFLAGS)

INCLUDES =				\
  -I$(srcdir)/gen			\
  $(APR_CXXFLAGS)

qpidd_LDADD =                         \
  libqpidbroker.la        \
  libqpidcommon.la

sbin_PROGRAMS = qpidd
qpidd_SOURCES = qpidd.cpp

apr = sys/apr
apr_src =			\
  $(apr)/APRAcceptor.cpp	\
  $(apr)/APRBase.cpp		\
  $(apr)/APRPool.cpp		\
  $(apr)/APRSocket.cpp		\
  $(apr)/LFProcessor.cpp	\
  $(apr)/LFSessionContext.cpp	\
  $(apr)/Socket.cpp		\
  $(apr)/Thread.cpp

apr_hdr =			\
  $(apr)/APRBase.h		\
  $(apr)/APRPool.h		\
  $(apr)/APRSocket.h		\
  $(apr)/LFProcessor.h		\
  $(apr)/LFSessionContext.h	

posix = sys/posix
posix_src =				\
  $(posix)/PosixAcceptor.cpp		\
  $(posix)/Socket.cpp			\
  $(posix)/Thread.cpp			\
  $(posix)/check.cpp			\
  $(posix)/EventChannel.cpp		\
  $(posix)/EventChannelThreads.cpp

posix_hdr = 				\
  $(posix)/check.h			\
  $(posix)/EventChannel.h		\
  $(posix)/EventChannelThreads.h

EXTRA_DIST=$(posix_src) $(posix_hdr)
platform_src = $(apr_src)
platform_hdr = $(apr_hdr)

framing = framing
gen     = gen
broker = broker
client = client

lib_LTLIBRARIES = libqpidcommon.la libqpidbroker.la libqpidclient.la

libqpidcommon_la_LIBADD =		\
  $(APR_LIBS)			\
  $(LIB_DLOPEN)			\
  $(LIB_CLOCK_GETTIME)

libqpidcommon_la_LDFLAGS =		\
  -version-info			\
  $(LIBTOOL_VERSION_INFO_ARG)

libqpidcommon_la_SOURCES =			\
  $(platform_src)				\
  $(framing)/AMQBody.cpp			\
  $(framing)/AMQRequestBody.cpp			\
  $(framing)/AMQResponseBody.cpp		\
  $(framing)/AMQContentBody.cpp			\
  $(framing)/AMQFrame.cpp			\
  $(framing)/AMQHeaderBody.cpp			\
  $(framing)/AMQHeartbeatBody.cpp		\
  $(framing)/AMQMethodBody.cpp			\
  $(framing)/MethodContext.cpp			\
  $(framing)/BasicHeaderProperties.cpp		\
  $(framing)/BodyHandler.cpp			\
  $(framing)/ChannelAdapter.cpp			\
  $(framing)/Buffer.cpp				\
  $(framing)/FieldTable.cpp			\
  $(framing)/FramingContent.cpp			\
  $(framing)/InitiationHandler.cpp		\
  $(framing)/ProtocolInitiation.cpp		\
  $(framing)/ProtocolVersion.cpp		\
  $(framing)/ProtocolVersionException.cpp	\
  $(framing)/Requester.cpp			\
  $(framing)/Responder.cpp			\
  $(framing)/Value.cpp				\
  $(framing)/Proxy.cpp				\
  $(gen)/AMQP_ClientProxy.cpp			\
  $(gen)/AMQP_HighestVersion.h		\
  $(gen)/AMQP_MethodVersionMap.cpp		\
  $(gen)/AMQP_ServerProxy.cpp			\
  Exception.cpp					\
  ExceptionHolder.cpp				\
  QpidError.cpp					\
  sys/Runnable.cpp				\
  sys/Time.cpp					\
  sys/ProducerConsumer.cpp

libqpidbroker_la_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
libqpidbroker_la_SOURCES =			\
  $(broker)/AccumulatedAck.cpp				\
  $(broker)/AccumulatedAck.h				\
  $(broker)/AutoDelete.cpp				\
  $(broker)/AutoDelete.h					\
  $(broker)/Binding.h					\
  $(broker)/Broker.cpp					\
  $(broker)/Broker.h					\
  $(broker)/BrokerAdapter.cpp				\
  $(broker)/BrokerAdapter.h				\
  $(broker)/BrokerSingleton.cpp				\
  $(broker)/BrokerSingleton.h				\
  $(broker)/BrokerChannel.cpp				\
  $(broker)/BrokerChannel.h				\
  $(broker)/BrokerExchange.h				\
  $(broker)/BrokerMessage.cpp				\
  $(broker)/BrokerMessage.h				\
  $(broker)/BrokerMessageMessage.cpp				\
  $(broker)/BrokerMessageMessage.h				\
  $(broker)/BrokerQueue.cpp				\
  $(broker)/BrokerQueue.h					\
  $(broker)/Configuration.cpp				\
  $(broker)/Configuration.h				\
  $(broker)/Connection.cpp				\
  $(broker)/Connection.h					\
  $(broker)/ConnectionFactory.cpp				\
  $(broker)/ConnectionFactory.h				\
  $(broker)/ConnectionToken.h				\
  $(broker)/Consumer.h					\
  $(broker)/Content.h					\
  $(broker)/Deliverable.h					\
  $(broker)/DeliverableMessage.cpp			\
  $(broker)/DeliverableMessage.h				\
  $(broker)/DeliveryRecord.cpp				\
  $(broker)/DeliveryRecord.h				\
  $(broker)/DirectExchange.cpp				\
  $(broker)/DirectExchange.h				\
  $(broker)/ExchangeRegistry.cpp				\
  $(broker)/ExchangeRegistry.h				\
  $(broker)/FanOutExchange.cpp				\
  $(broker)/FanOutExchange.h				\
  $(broker)/HeadersExchange.cpp				\
  $(broker)/HeadersExchange.h				\
  $(broker)/InMemoryContent.cpp				\
  $(broker)/InMemoryContent.h				\
  $(broker)/LazyLoadedContent.cpp				\
  $(broker)/LazyLoadedContent.h				\
  $(broker)/MessageBuilder.cpp				\
  $(broker)/MessageBuilder.h				\
  $(broker)/MessageHandlerImpl.cpp			\
  $(broker)/MessageHandlerImpl.h				\
  $(broker)/MessageStore.h				\
  $(broker)/MessageStoreModule.cpp			\
  $(broker)/MessageStoreModule.h				\
  $(broker)/NameGenerator.cpp				\
  $(broker)/NameGenerator.h				\
  $(broker)/NullMessageStore.cpp				\
  $(broker)/NullMessageStore.h				\
  $(broker)/Persistable.h					\
  $(broker)/PersistableExchange.h				\
  $(broker)/PersistableMessage.h				\
  $(broker)/PersistableQueue.h				\
  $(broker)/Prefetch.h					\
  $(broker)/QueuePolicy.cpp				\
  $(broker)/QueuePolicy.h					\
  $(broker)/QueueRegistry.cpp				\
  $(broker)/QueueRegistry.h				\
  $(broker)/RecoverableMessage.h                          \
  $(broker)/RecoverableQueue.h                            \
  $(broker)/RecoveryManager.h				\
  $(broker)/RecoveryManagerImpl.cpp			\
  $(broker)/RecoveryManagerImpl.h				\
  $(broker)/Reference.cpp					\
  $(broker)/Reference.h					\
  $(broker)/TopicExchange.cpp				\
  $(broker)/TopicExchange.h				\
  $(broker)/TransactionalStore.h				\
  $(broker)/TxAck.cpp					\
  $(broker)/TxAck.h					\
  $(broker)/TxBuffer.cpp					\
  $(broker)/TxBuffer.h					\
  $(broker)/TxOp.h					\
  $(broker)/TxPublish.cpp					\
  $(broker)/TxPublish.h

libqpidclient_la_LIBADD = libqpidcommon.la
libqpidclient_la_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
libqpidclient_la_SOURCES =			\
  $(client)/ClientConnection.cpp				\
  $(client)/ClientChannel.cpp				\
  $(client)/ClientExchange.cpp				\
  $(client)/ClientQueue.cpp				\
  $(client)/BasicMessageChannel.cpp			\
  $(client)/MessageMessageChannel.cpp			\
  $(client)/Connector.cpp				\
  $(client)/IncomingMessage.cpp				\
  $(client)/MessageListener.cpp				\
  $(client)/ResponseHandler.cpp				\
  $(client)/ReturnedMessageHandler.cpp
  
nobase_pkginclude_HEADERS =			\
  $(gen)/AMQP_HighestVersion.h			\
  $(platform_hdr)				\
  $(framing)/AMQBody.h				\
  $(framing)/AMQContentBody.h			\
  $(framing)/AMQDataBlock.h			\
  $(framing)/AMQFrame.h				\
  $(framing)/AMQHeaderBody.h			\
  $(framing)/AMQHeartbeatBody.h			\
  $(framing)/AMQMethodBody.h			\
  $(framing)/MethodContext.h			\
  $(framing)/BasicHeaderProperties.h		\
  $(framing)/BodyHandler.h			\
  $(framing)/ChannelAdapter.h			\
  $(framing)/Buffer.h				\
  $(framing)/FieldTable.h			\
  $(framing)/FramingContent.h			\
  $(framing)/HeaderProperties.h			\
  $(framing)/InitiationHandler.h		\
  $(framing)/InputHandler.h			\
  $(framing)/OutputHandler.h			\
  $(framing)/ProtocolInitiation.h		\
  $(framing)/ProtocolVersion.h			\
  $(framing)/ProtocolVersionException.h		\
  $(framing)/Value.h				\
  $(framing)/amqp_framing.h			\
  $(framing)/amqp_types.h			\
  $(framing)/Proxy.h				\
  $(client)/AckMode.h					\
  $(client)/ClientChannel.h				\
  $(client)/ClientExchange.h				\
  $(client)/ClientMessage.h				\
  $(client)/ClientQueue.h					\
  $(client)/Connection.h					\
  $(client)/Connector.h					\
  $(client)/IncomingMessage.h				\
  $(client)/MessageChannel.h				\
  $(client)/BasicMessageChannel.h				\
  $(client)/MessageMessageChannel.h		\
  $(client)/MessageListener.h			\
  $(client)/MethodBodyInstances.h		\
  $(client)/ResponseHandler.h			\
  $(client)/ReturnedMessageHandler.h		\
  shared_ptr.h					\
  Exception.h					\
  ExceptionHolder.h				\
  QpidError.h					\
  SharedObject.h				\
  sys/Acceptor.h				\
  sys/AtomicCount.h				\
  sys/Module.h					\
  sys/Monitor.h					\
  sys/Mutex.h					\
  sys/Runnable.h				\
  sys/ConnectionOutputHandler.h			\
  sys/ConnectionInputHandler.h			\
  sys/ConnectionInputHandlerFactory.h		\
  sys/ShutdownHandler.h				\
  sys/Socket.h					\
  sys/Thread.h					\
  sys/Time.h					\
  sys/TimeoutHandler.h				\
  sys/ProducerConsumer.h

# This is gmake specific
nobase_pkginclude_HEADERS += $(generated_headers)

# Distribute the generated sources, at least for now, since
# the generator code is in java.
EXTRA_DIST += $(BUILT_SOURCES)
DISTCLEANFILES = $(BUILT_SOURCES) $(gen)/timestamp $(gen)/gen-src.mk

# Don't attempt to run the code generator unless configure has set
# CAN_GENERATE_CODE, indicating that the amqp.xml and tools needed
# to run the code generator are available.
#
if CAN_GENERATE_CODE

gentools_dir = $(srcdir)/../gentools
spec_dir = $(srcdir)/../../specs

# FIXME aconway 2007-01-04: Enabling Basic class until
# new messaging class is ready to replace it.
# spec = $(spec_dir)/amqp.0-9.xml $(spec_dir)/amqp-errata.0-9.xml $(spec_dir)/amqp-nogen.0-9.xml
spec = $(spec_dir)/amqp.0-9.xml $(spec_dir)/amqp-errata.0-9.xml

gentools_srcdir = $(gentools_dir)/src/org/apache/qpid/gentools

$(BUILT_SOURCES) $(gen)/timestamp: $(spec) $(java_sources) $(cxx_templates) Makefile.am
	rm -f $(BUILT_SOURCES)
	cd $(gentools_srcdir) && rm -f *.class && $(JAVAC) *.java
	$(JAVA) -cp $(gentools_dir)/src org.apache.qpid.gentools.Main \
	        -c -o $(gen) -t $(gentools_dir)/templ.cpp $(spec)
	touch $(gen)/timestamp

$(gen)/gen-src.mk: $(gen)/timestamp
	mkdir -p $(gen)
	./make-gen-src-mk.sh $(gentools_dir) $(gentools_srcdir) > $@-t
	mv $@-t $@
endif

# Force build during dist phase so help2man will work.
dist-hook: $(lib_LTLIBRARIES) $(sbin_PROGRAMS)
