SUBDIRS = . tests

## Generated code

# Note: generated soure and makefiles included in distribution so a
# distribution can be built without code generation tools and XML
# sources.

EXTRA_DIST= $(platform_dist) 

EXTRA_DIST += generate.mk generate.sh $(generated_cpp) $(generated_h) \
  rubygen.mk $(rgen_srcs) 

# AMQP_XML is defined in ../configure.ac
specs=@AMQP_XML@ $(top_srcdir)/xml/cluster.xml

# Makefiles are either generated or copied from $srcdir.
include generate.mk
include rubygen.mk

DISTCLEANFILES=generate.mk rubygen.mk

if GENERATE

# Java code generator.
# Must generate into a separate gen directory because otherwise
# there's no way to figure out which files are generated.

gentools_dir=$(top_srcdir)/gentools

generate.mk $(generated_cpp) $(generated_h): generate.timestamp
generate.timestamp: generate.sh $(specs) $(generator)
	env gentools_dir=$(gentools_dir) specs="$(specs)" $(srcdir)/generate.sh
	touch $@

# Empty rule in case a generator file is renamed/removed.
$(generator):

maintainer-clean-local:
	rm -rf gen

# Ruby generator.

rgen_dir=$(top_srcdir)/rubygen
rgen_tdir=$(rgen_dir)/templates
rgen_cmd=ruby -I $(rgen_dir) $(rgen_dir)/generate

rgen_templates=$(rgen_tdir)/method_variants.rb	\
	$(rgen_tdir)/frame_body_lists.rb	\
	$(rgen_tdir)/Session.rb			\
	$(rgen_tdir)/Proxy.rb

rgen_generator=$(rgen_dir)/generate		\
	$(rgen_dir)/amqpgen.rb			\
	$(rgen_dir)/cppgen.rb

rubygen.mk $(rgen_srcs): rubygen.timestamp
rubygen.timestamp: $(rgen_generator) $(specs) $(rgen_templates)
	{ echo -n "rgen_srcs= "; \
	for f in `$(rgen_cmd) . $(specs) $(rgen_templates) `; do echo "\\"; echo -n "	$$f "; done; \
	echo; } > rubygen.mk
	touch $@
else				# !GENERATE
# If not generating, copy generated makefiles from source dir to build dir.
generate.mk:
	cp $(srcdir)/$@ $@
rubygen.mk:
	cp $(srcdir)/$@ $@
endif				# GENERATE

## Compiler flags

AM_CXXFLAGS = $(WARNING_CFLAGS)
AM_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
INCLUDES = -Igen -I$(srcdir)/gen

## Automake macros to build libraries and executables.

qpidd_LDADD =					\
  libqpidbroker.la				\
  libqpidcommon.la

sbin_PROGRAMS = qpidd
qpidd_SOURCES = qpidd.cpp

posix_plat_src = \
  qpid/sys/epoll/EpollPoller.cpp \
  qpid/sys/posix/check.cpp \
  qpid/sys/posix/Socket.cpp \
  qpid/sys/posix/AsynchIO.cpp \
  qpid/sys/posix/Time.cpp \
  qpid/sys/posix/Thread.cpp \
  qpid/sys/posix/Shlib.cpp

posix_plat_hdr = \
  qpid/sys/posix/check.h \
  qpid/sys/posix/Condition.h \
  qpid/sys/posix/PrivatePosix.h \
  qpid/sys/posix/Mutex.h \
  qpid/sys/posix/Thread.h

platform_src = $(posix_plat_src)
platform_hdr = $(posix_plat_hdr)

lib_LTLIBRARIES = libqpidcommon.la libqpidbroker.la libqpidclient.la

include cluster.mk

# The logger library uses boost::date_time to format time.
# We have to disable the unused parameters warning to get around
# unused parameters in boost::date_time headers. So we build it
# in a convenience library to link into libqpid_common.
# 
noinst_LTLIBRARIES=libLogger.la
libLogger_la_SOURCES=qpid/log/Logger.cpp qpid/log/Logger.h  
libLogger_la_CXXFLAGS=$(AM_CXXFLAGS) -Wno-unused-parameter

libqpidcommon_la_LIBADD = \
  -lboost_program_options \
  -luuid \
  libLogger.la \
  $(LIB_DLOPEN) \
  $(LIB_CLOCK_GETTIME)

libqpidcommon_la_SOURCES = \
  $(platform_src) \
  qpid/framing/AMQBody.cpp \
  qpid/framing/AMQRequestBody.cpp \
  qpid/framing/AMQResponseBody.cpp \
  qpid/framing/AMQContentBody.cpp \
  qpid/framing/AMQFrame.cpp \
  qpid/framing/AMQHeaderBody.cpp \
  qpid/framing/AMQHeartbeatBody.cpp \
  qpid/framing/AMQMethodBody.cpp \
  qpid/framing/FrameHandler.h \
  qpid/framing/MethodContext.cpp \
  qpid/framing/BasicHeaderProperties.cpp \
  qpid/framing/BodyHandler.cpp \
  qpid/framing/ChannelAdapter.cpp \
  qpid/framing/Buffer.cpp \
  qpid/framing/FieldTable.cpp \
  qpid/framing/FramingContent.cpp \
  qpid/framing/InitiationHandler.cpp \
  qpid/framing/ProtocolInitiation.cpp \
  qpid/framing/ProtocolVersion.cpp \
  qpid/framing/ProtocolVersionException.cpp \
  qpid/framing/Requester.cpp \
  qpid/framing/Responder.cpp \
  qpid/framing/SequenceNumber.cpp \
  qpid/framing/SequenceNumberSet.cpp \
  qpid/framing/Correlator.cpp \
  qpid/framing/Value.cpp \
  qpid/framing/Proxy.cpp \
  qpid/framing/Uuid.cpp \
  qpid/framing/Handler.h \
  qpid/framing/FrameHandler.h \
  qpid/framing/HandlerUpdater.h \
  qpid/framing/AMQP_ClientProxy.cpp \
  qpid/framing/AMQP_ServerProxy.cpp \
  gen/qpid/framing/AMQP_HighestVersion.h \
  gen/qpid/framing/AMQP_MethodVersionMap.cpp \
  qpid/Exception.cpp \
  qpid/Plugin.h \
  qpid/Plugin.cpp \
  qpid/Url.h \
  qpid/Url.cpp \
  qpid/QpidError.cpp \
  qpid/sys/AsynchIOAcceptor.cpp \
  qpid/sys/Dispatcher.cpp \
  qpid/sys/Runnable.cpp \
  qpid/sys/Serializer.cpp \
  qpid/sys/Shlib.h \
  qpid/sys/Shlib.cpp \
  qpid/Options.cpp \
  qpid/Options.h \
  qpid/log/Options.cpp \
  qpid/log/Options.h \
  qpid/log/Selector.cpp \
  qpid/log/Selector.h \
  qpid/log/Statement.cpp \
  qpid/log/Statement.h \
  qpid/memory.h 

libqpidbroker_la_LIBADD = libqpidcommon.la -lboost_iostreams
libqpidbroker_la_SOURCES = \
  qpid/broker/AccumulatedAck.cpp \
  qpid/broker/Broker.cpp \
  qpid/broker/BrokerAdapter.cpp \
  qpid/broker/BrokerSingleton.cpp \
  qpid/broker/BrokerChannel.cpp \
  qpid/broker/BrokerExchange.cpp \
  qpid/broker/BrokerMessage.cpp \
  qpid/broker/BrokerMessageMessage.cpp \
  qpid/broker/BrokerQueue.cpp \
  qpid/broker/Connection.cpp \
  qpid/broker/ConnectionAdapter.cpp \
  qpid/broker/ConnectionFactory.cpp \
  qpid/broker/Daemon.cpp \
  qpid/broker/DeliverableMessage.cpp \
  qpid/broker/DeliveryRecord.cpp \
  qpid/broker/DirectExchange.cpp \
  qpid/broker/DtxAck.cpp \
  qpid/broker/DtxBuffer.cpp \
  qpid/broker/DtxHandlerImpl.cpp \
  qpid/broker/DtxManager.cpp \
  qpid/broker/DtxTimeout.cpp \
  qpid/broker/DtxWorkRecord.cpp \
  qpid/broker/ExchangeRegistry.cpp \
  qpid/broker/FanOutExchange.cpp \
  qpid/broker/HeadersExchange.cpp \
  qpid/broker/InMemoryContent.cpp \
  qpid/broker/LazyLoadedContent.cpp \
  qpid/broker/MessageBuilder.cpp \
  qpid/broker/MessageHandlerImpl.cpp \
  qpid/broker/MessageStoreModule.cpp \
  qpid/broker/NameGenerator.cpp \
  qpid/broker/NullMessageStore.cpp \
  qpid/broker/QueueBindings.cpp \
  qpid/broker/QueuePolicy.cpp \
  qpid/broker/QueueRegistry.cpp \
  qpid/broker/RecoveryManagerImpl.cpp \
  qpid/broker/RecoveredEnqueue.cpp \
  qpid/broker/RecoveredDequeue.cpp \
  qpid/broker/Reference.cpp \
  qpid/broker/SemanticHandler.cpp \
  qpid/broker/Timer.cpp \
  qpid/broker/TopicExchange.cpp \
  qpid/broker/TxAck.cpp \
  qpid/broker/TxBuffer.cpp \
  qpid/broker/TxPublish.cpp 

libqpidclient_la_LIBADD = libqpidcommon.la
libqpidclient_la_SOURCES =			\
  qpid/client/ClientConnection.cpp		\
  qpid/client/ClientChannel.cpp			\
  qpid/client/ClientExchange.cpp		\
  qpid/client/ClientQueue.cpp			\
  qpid/client/ConnectionImpl.cpp		\
  qpid/client/Connector.cpp			\
  qpid/client/MessageListener.cpp		\
  qpid/client/ResponseHandler.cpp		\
  qpid/client/ReturnedMessageHandler.cpp	\
  qpid/client/Correlator.cpp			\
  qpid/client/CompletionTracker.cpp		\
  qpid/client/ChannelHandler.cpp		\
  qpid/client/ConnectionHandler.cpp		\
  qpid/client/ExecutionHandler.cpp		\
  qpid/client/FutureCompletion.cpp		\
  qpid/client/FutureResponse.cpp		\
  qpid/client/FutureFactory.cpp			\
  qpid/client/ReceivedContent.cpp		\
  qpid/client/Session.cpp			\
  qpid/client/SessionCore.cpp			\
  qpid/client/StateManager.cpp


nobase_include_HEADERS = \
  $(platform_hdr) \
  qpid/broker/AccumulatedAck.h \
  qpid/broker/BrokerChannel.h \
  qpid/broker/BrokerExchange.h \
  qpid/broker/BrokerMessage.h \
  qpid/broker/BrokerMessageBase.h \
  qpid/broker/BrokerQueue.h \
  qpid/broker/CompletionHandler.h \
  qpid/broker/Consumer.h \
  qpid/broker/Deliverable.h \
  qpid/broker/DeliverableMessage.h \
  qpid/broker/DeliveryAdapter.h \
  qpid/broker/DeliveryToken.h \
  qpid/broker/DirectExchange.h \
  qpid/broker/DtxAck.h \
  qpid/broker/DtxBuffer.h \
  qpid/broker/DtxHandlerImpl.h \
  qpid/broker/DtxManager.h \
  qpid/broker/DtxTimeout.h \
  qpid/broker/DtxWorkRecord.h \
  qpid/broker/ExchangeRegistry.h \
  qpid/broker/FanOutExchange.h \
  qpid/broker/HandlerImpl.h \
  qpid/broker/InMemoryContent.h \
  qpid/broker/MessageBuilder.h \
  qpid/broker/MessageHandlerImpl.h \
  qpid/broker/MessageStoreModule.h \
  qpid/broker/NameGenerator.h \
  qpid/broker/NullMessageStore.h \
  qpid/broker/Persistable.h \
  qpid/broker/Prefetch.h \
  qpid/broker/QueueBindings.h \
  qpid/broker/QueueRegistry.h \
  qpid/broker/RecoverableExchange.h \
  qpid/broker/RecoverableMessage.h \
  qpid/broker/RecoverableQueue.h \
  qpid/broker/RecoverableTransaction.h \
  qpid/broker/RecoveryManager.h \
  qpid/broker/RecoveredEnqueue.h \
  qpid/broker/RecoveredDequeue.h \
  qpid/broker/Reference.h \
  qpid/broker/TxBuffer.h \
  qpid/broker/TxOp.h \
  qpid/broker/TxPublish.h \
  qpid/broker/Broker.h \
  qpid/broker/BrokerAdapter.h \
  qpid/broker/BrokerMessageMessage.h \
  qpid/broker/BrokerSingleton.h \
  qpid/broker/Connection.h \
  qpid/broker/ConnectionAdapter.h \
  qpid/broker/ConnectionFactory.h \
  qpid/broker/ConnectionToken.h \
  qpid/broker/Content.h \
  qpid/broker/Daemon.h \
  qpid/broker/DeliveryRecord.h \
  qpid/broker/HeadersExchange.h \
  qpid/broker/LazyLoadedContent.h \
  qpid/broker/MessageStore.h \
  qpid/broker/PersistableExchange.h \
  qpid/broker/PersistableMessage.h \
  qpid/broker/PersistableQueue.h \
  qpid/broker/QueuePolicy.h \
  qpid/broker/RecoveryManagerImpl.h \
  qpid/broker/SemanticHandler.h \
  qpid/broker/Timer.h \
  qpid/broker/TopicExchange.h \
  qpid/broker/TransactionalStore.h \
  qpid/broker/TxAck.h \
  qpid/client/AckMode.h \
  qpid/client/ClientChannel.h \
  qpid/client/ClientExchange.h \
  qpid/client/ClientMessage.h \
  qpid/client/ClientQueue.h \
  qpid/client/Connection.h \
  qpid/client/ConnectionImpl.h \
  qpid/client/Connector.h \
  qpid/client/MessageChannel.h \
  qpid/client/MessageListener.h \
  qpid/client/MethodBodyInstances.h \
  qpid/client/ResponseHandler.h \
  qpid/client/ReturnedMessageHandler.h \
  qpid/client/BlockingQueue.h \
  qpid/client/Correlator.h \
  qpid/client/CompletionTracker.h \
  qpid/client/ChannelHandler.h \
  qpid/client/ChainableFrameHandler.h	\
  qpid/client/ConnectionHandler.h \
  qpid/client/ExecutionHandler.h \
  qpid/client/FutureCompletion.h \
  qpid/client/FutureResponse.h \
  qpid/client/FutureFactory.h \
  qpid/client/ReceivedContent.h \
  qpid/client/Response.h \
  qpid/client/Session.h \
  qpid/client/SessionCore.h \
  qpid/client/StateManager.h \
  qpid/framing/AMQBody.h \
  qpid/framing/AMQContentBody.h \
  qpid/framing/AMQDataBlock.h \
  qpid/framing/AMQFrame.h \
  qpid/framing/AMQHeaderBody.h \
  qpid/framing/AMQHeartbeatBody.h \
  qpid/framing/AMQMethodBody.h \
  qpid/framing/AMQRequestBody.h \
  qpid/framing/AMQResponseBody.h \
  qpid/framing/BasicHeaderProperties.h \
  qpid/framing/BodyHandler.h \
  qpid/framing/Buffer.h \
  qpid/framing/ChannelAdapter.h \
  qpid/framing/Correlator.h \
  qpid/framing/FieldTable.h \
  qpid/framing/FramingContent.h \
  qpid/framing/HeaderProperties.h \
  qpid/framing/InitiationHandler.h \
  qpid/framing/InputHandler.h \
  qpid/framing/MethodContent.h \
  qpid/framing/MethodContext.h \
  qpid/framing/OutputHandler.h \
  qpid/framing/ProtocolInitiation.h \
  qpid/framing/ProtocolVersion.h \
  qpid/framing/ProtocolVersionException.h \
  qpid/framing/Proxy.h \
  qpid/framing/Requester.h \
  qpid/framing/Responder.h \
  qpid/framing/SerializeHandler.h \
  qpid/framing/SequenceNumber.h \
  qpid/framing/SequenceNumberSet.h \
  qpid/framing/Value.h \
  qpid/framing/Visitor.h \
  qpid/framing/Uuid.h \
  qpid/framing/amqp_framing.h \
  qpid/framing/amqp_types.h \
  qpid/framing/amqp_types_full.h \
  qpid/sys/Acceptor.h \
  qpid/sys/AsynchIO.h \
  qpid/sys/AtomicCount.h \
  qpid/sys/Dispatcher.h \
  qpid/sys/Condition.h \
  qpid/sys/ConnectionInputHandler.h \
  qpid/sys/ConnectionInputHandlerFactory.h \
  qpid/sys/ConnectionOutputHandler.h \
  qpid/sys/Module.h \
  qpid/sys/Monitor.h \
  qpid/sys/Mutex.h \
  qpid/sys/Poller.h \
  qpid/sys/Runnable.h \
  qpid/sys/ScopedIncrement.h \
  qpid/sys/ShutdownHandler.h \
  qpid/sys/Socket.h \
  qpid/sys/Thread.h \
  qpid/sys/ConcurrentQueue.h \
  qpid/sys/Serializer.h \
  qpid/sys/Time.h \
  qpid/sys/TimeoutHandler.h \
  qpid/Exception.h \
  qpid/ExceptionHolder.h \
  qpid/QpidError.h \
  qpid/SharedObject.h \
  qpid/shared_ptr.h

# Force build of qpidd during dist phase so help2man will work.
dist-hook: qpidd

