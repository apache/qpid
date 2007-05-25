SUBDIRS = . tests

# Generated code
-include generate.mk

generate.mk $(generated_cpp) $(generated_h): generate.sh $(generator)
	$(srcdir)/generate.sh

# Empty rule in case a generator file is renamed/removed.
$(generator):

DISTCLEANFILES=generate.mk

clean-gen:
	rm -rf gen

maintainer-clean-local:
	clean-gen

EXTRA_DIST=generate.sh $(generated_cpp) $(generated_h) $(platform_dist)

AM_CXXFLAGS = $(WARNING_CFLAGS) $(APR_CXXFLAGS)
AM_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
INCLUDES = -I$(builddir)/gen -I$(srcdir)/gen

qpidd_LDADD =					\
  libqpidbroker.la				\
  libqpidcommon.la

sbin_PROGRAMS = qpidd
qpidd_SOURCES = qpidd.cpp

apr_netio_src = \
  qpid/sys/apr/APRAcceptor.cpp \
  qpid/sys/apr/APRBase.cpp \
  qpid/sys/apr/APRPool.cpp \
  qpid/sys/apr/APRSocket.cpp \
  qpid/sys/apr/LFProcessor.cpp \
  qpid/sys/apr/LFSessionContext.cpp

apr_netio_hdr = \
  qpid/sys/apr/APRBase.h \
  qpid/sys/apr/APRPool.h \
  qpid/sys/apr/APRSocket.h \
  qpid/sys/apr/LFProcessor.h \
  qpid/sys/apr/LFSessionContext.h	

apr_plat_src = \
  qpid/sys/apr/Socket.cpp \
  qpid/sys/apr/Time.cpp \
  qpid/sys/apr/Thread.cpp

apr_plat_hdr = \
  qpid/sys/apr/Condition.h \
  qpid/sys/apr/Module.h \
  qpid/sys/apr/Mutex.h \
  qpid/sys/apr/Socket.h \
  qpid/sys/apr/Thread.h

posix_netio_src = \
  qpid/sys/posix/EventChannel.cpp \
  qpid/sys/posix/EventChannelAcceptor.cpp \
  qpid/sys/posix/EventChannelConnection.cpp \
  qpid/sys/posix/EventChannelThreads.cpp

posix_netio_hdr = \
  qpid/sys/posix/EventChannel.h \
  qpid/sys/posix/EventChannelThreads.h

posix_plat_src = \
  qpid/sys/posix/check.cpp \
  qpid/sys/posix/Socket.cpp \
  qpid/sys/posix/Time.cpp \
  qpid/sys/posix/Thread.cpp

posix_plat_hdr = \
  qpid/sys/posix/check.h \
  qpid/sys/posix/Condition.h \
  qpid/sys/posix/Module.h \
  qpid/sys/posix/Mutex.h \
  qpid/sys/posix/Socket.h \
  qpid/sys/posix/Thread.h

if USE_APR_NETIO
 platform_dist=$(posix_netio_src) $(posix_netio_hdr)
 platform_src = $(apr_netio_src)
 platform_hdr = $(apr_netio_hdr)
else
 platform_dist=$(apr_netio_src) $(apr_netio_hdr)
 platform_src = $(posix_netio_src)
 platform_hdr = $(posix_netio_hdr)
endif

if USE_APR_PLATFORM
 platform_dist+=$(posix_plat_src) $(posix_plat_hdr)
 platform_src += $(apr_plat_src)
 platform_hdr += $(apr_plat_hdr)
else
 platform_dist+=$(apr_plat_src) $(apr_plat_hdr)
 platform_src += $(posix_plat_src)
 platform_hdr += $(posix_plat_hdr)
endif

lib_LTLIBRARIES = libqpidcommon.la libqpidbroker.la libqpidclient.la

libqpidcommon_la_LIBADD = \
  -lboost_program_options \
  $(APR_LIBS) \
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
  qpid/framing/Correlator.cpp \
  qpid/framing/Value.cpp \
  qpid/framing/Proxy.cpp \
  gen/qpid/framing/AMQP_ClientProxy.cpp \
  gen/qpid/framing/AMQP_HighestVersion.h \
  gen/qpid/framing/AMQP_MethodVersionMap.cpp \
  gen/qpid/framing/AMQP_ServerProxy.cpp \
  qpid/Exception.cpp \
  qpid/ExceptionHolder.cpp \
  qpid/QpidError.cpp \
  qpid/sys/Runnable.cpp \
  qpid/sys/ProducerConsumer.cpp \
  qpid/CommonOptions.cpp


libqpidbroker_la_LIBADD = libqpidcommon.la -ldaemon -lboost_filesystem
libqpidbroker_la_SOURCES = \
  qpid/broker/AccumulatedAck.cpp \
  qpid/broker/AutoDelete.cpp \
  qpid/broker/Broker.cpp \
  qpid/broker/BrokerAdapter.cpp \
  qpid/broker/BrokerSingleton.cpp \
  qpid/broker/BrokerChannel.cpp \
  qpid/broker/BrokerExchange.cpp \
  qpid/broker/BrokerMessage.cpp \
  qpid/broker/BrokerMessageMessage.cpp \
  qpid/broker/BrokerQueue.cpp \
  qpid/broker/Connection.cpp \
  qpid/broker/ConnectionFactory.cpp \
  qpid/broker/Daemon.cpp \
  qpid/broker/DeliverableMessage.cpp \
  qpid/broker/DeliveryRecord.cpp \
  qpid/broker/DirectExchange.cpp \
  qpid/broker/DtxBuffer.cpp \
  qpid/broker/DtxHandlerImpl.cpp \
  qpid/broker/DtxManager.cpp \
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
  qpid/broker/QueuePolicy.cpp \
  qpid/broker/QueueRegistry.cpp \
  qpid/broker/RecoveryManagerImpl.cpp \
  qpid/broker/RecoveredEnqueue.cpp \
  qpid/broker/RecoveredDequeue.cpp \
  qpid/broker/Reference.cpp \
  qpid/broker/TopicExchange.cpp \
  qpid/broker/TxAck.cpp \
  qpid/broker/TxBuffer.cpp \
  qpid/broker/TxPublish.cpp

libqpidclient_la_LIBADD = libqpidcommon.la
libqpidclient_la_SOURCES =			\
  qpid/client/ClientConnection.cpp				\
  qpid/client/ClientChannel.cpp				\
  qpid/client/ClientExchange.cpp				\
  qpid/client/ClientQueue.cpp				\
  qpid/client/BasicMessageChannel.cpp			\
  qpid/client/MessageMessageChannel.cpp			\
  qpid/client/Connector.cpp				\
  qpid/client/IncomingMessage.cpp				\
  qpid/client/MessageListener.cpp				\
  qpid/client/ResponseHandler.cpp				\
  qpid/client/ReturnedMessageHandler.cpp

nobase_include_HEADERS = \
  $(platform_hdr) \
  qpid/broker/AccumulatedAck.h \
  qpid/broker/AutoDelete.h \
  qpid/broker/BrokerChannel.h \
  qpid/broker/BrokerExchange.h \
  qpid/broker/BrokerMessage.h \
  qpid/broker/BrokerMessageBase.h \
  qpid/broker/BrokerQueue.h \
  qpid/broker/CompletionHandler.h \
  qpid/broker/Consumer.h \
  qpid/broker/Deliverable.h \
  qpid/broker/DeliverableMessage.h \
  qpid/broker/DirectExchange.h \
  qpid/broker/DtxBuffer.h \
  qpid/broker/DtxHandlerImpl.h \
  qpid/broker/DtxManager.h \
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
  qpid/broker/TopicExchange.h \
  qpid/broker/TransactionalStore.h \
  qpid/broker/TxAck.h \
  qpid/client/AckMode.h \
  qpid/client/BasicMessageChannel.h \
  qpid/client/ClientAdapter.h \
  qpid/client/ClientChannel.h \
  qpid/client/ClientExchange.h \
  qpid/client/ClientMessage.h \
  qpid/client/ClientQueue.h \
  qpid/client/Connection.h \
  qpid/client/Connector.h \
  qpid/client/IncomingMessage.h \
  qpid/client/MessageChannel.h \
  qpid/client/MessageListener.h \
  qpid/client/MessageMessageChannel.h \
  qpid/client/MethodBodyInstances.h \
  qpid/client/ResponseHandler.h \
  qpid/client/ReturnedMessageHandler.h \
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
  qpid/framing/MethodContext.h \
  qpid/framing/OutputHandler.h \
  qpid/framing/ProtocolInitiation.h \
  qpid/framing/ProtocolVersion.h \
  qpid/framing/ProtocolVersionException.h \
  qpid/framing/Proxy.h \
  qpid/framing/Requester.h \
  qpid/framing/Responder.h \
  qpid/framing/Value.h \
  qpid/framing/amqp_framing.h \
  qpid/framing/amqp_types.h \
  qpid/framing/amqp_types_full.h \
  qpid/sys/Acceptor.h \
  qpid/sys/AtomicCount.h \
  qpid/sys/Condition.h \
  qpid/sys/ConnectionInputHandler.h \
  qpid/sys/ConnectionInputHandlerFactory.h \
  qpid/sys/ConnectionOutputHandler.h \
  qpid/sys/Module.h \
  qpid/sys/Monitor.h \
  qpid/sys/Mutex.h \
  qpid/sys/ProducerConsumer.h \
  qpid/sys/Runnable.h \
  qpid/sys/ScopedIncrement.h \
  qpid/sys/ShutdownHandler.h \
  qpid/sys/Socket.h \
  qpid/sys/Thread.h \
  qpid/sys/ThreadSafeQueue.h \
  qpid/sys/Time.h \
  qpid/sys/TimeoutHandler.h \
  qpid/Exception.h \
  qpid/ExceptionHolder.h \
  qpid/QpidError.h \
  qpid/SharedObject.h \
  qpid/shared_ptr.h \
  qpid/CommonOptions.h

# Force build of qpidd during dist phase so help2man will work.
dist-hook: qpidd
