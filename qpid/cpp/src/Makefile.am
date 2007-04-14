SUBDIRS = . tests

AM_CXXFLAGS = $(WARNING_CFLAGS) $(APR_CXXFLAGS)

gen_framing    = $(top_builddir)/gen/qpid/framing
framing= qpid/framing
broker = qpid/broker
client = qpid/client
apr = qpid/sys/apr
posix = qpid/sys/posix

# -I top_builddir for config.h
INCLUDES = -I$(top_builddir)  -I$(top_builddir)/gen 

qpidd_LDADD = \
  libqpidbroker.la \
  libqpidcommon.la

sbin_PROGRAMS = qpidd
qpidd_SOURCES = qpidd.cpp

apr_src = \
  $(apr)/APRAcceptor.cpp \
  $(apr)/APRBase.cpp \
  $(apr)/APRPool.cpp \
  $(apr)/APRSocket.cpp \
  $(apr)/LFProcessor.cpp \
  $(apr)/LFSessionContext.cpp \
  $(apr)/Socket.cpp \
  $(apr)/Thread.cpp

apr_hdr = \
  $(apr)/APRBase.h \
  $(apr)/APRPool.h \
  $(apr)/APRSocket.h \
  $(apr)/LFProcessor.h \
  $(apr)/LFSessionContext.h	

posix_src = \
  $(posix)/PosixAcceptor.cpp \
  $(posix)/Socket.cpp \
  $(posix)/Thread.cpp \
  $(posix)/check.cpp \
  $(posix)/EventChannel.cpp \
  $(posix)/EventChannelThreads.cpp

posix_hdr = \
  $(posix)/check.h \
  $(posix)/EventChannel.h \
  $(posix)/EventChannelThreads.h

EXTRA_DIST=$(posix_src) $(posix_hdr)
platform_src = $(apr_src)
platform_hdr = $(apr_hdr)

lib_LTLIBRARIES = libqpidcommon.la libqpidbroker.la libqpidclient.la

libqpidcommon_la_LIBADD = \
  $(APR_LIBS) \
  $(LIB_DLOPEN) \
  $(LIB_CLOCK_GETTIME)

libqpidcommon_la_LDFLAGS = \
  -version-info \
  $(LIBTOOL_VERSION_INFO_ARG)

libqpidcommon_la_SOURCES = \
  $(platform_src) \
  $(framing)/AMQBody.cpp \
  $(framing)/AMQRequestBody.cpp \
  $(framing)/AMQResponseBody.cpp \
  $(framing)/AMQContentBody.cpp \
  $(framing)/AMQFrame.cpp \
  $(framing)/AMQHeaderBody.cpp \
  $(framing)/AMQHeartbeatBody.cpp \
  $(framing)/AMQMethodBody.cpp \
  $(framing)/MethodContext.cpp \
  $(framing)/BasicHeaderProperties.cpp \
  $(framing)/BodyHandler.cpp \
  $(framing)/ChannelAdapter.cpp \
  $(framing)/Buffer.cpp \
  $(framing)/FieldTable.cpp \
  $(framing)/FramingContent.cpp \
  $(framing)/InitiationHandler.cpp \
  $(framing)/ProtocolInitiation.cpp \
  $(framing)/ProtocolVersion.cpp \
  $(framing)/ProtocolVersionException.cpp \
  $(framing)/Requester.cpp \
  $(framing)/Responder.cpp \
  $(framing)/Correlator.cpp \
  $(framing)/Value.cpp \
  $(framing)/Proxy.cpp \
  $(gen_framing)/AMQP_ClientProxy.cpp \
  $(gen_framing)/AMQP_HighestVersion.h \
  $(gen_framing)/AMQP_MethodVersionMap.cpp \
  $(gen_framing)/AMQP_ServerProxy.cpp \
  qpid/Exception.cpp \
  qpid/ExceptionHolder.cpp \
  qpid/QpidError.cpp \
  qpid/sys/Runnable.cpp \
  qpid/sys/Time.cpp \
  qpid/sys/ProducerConsumer.cpp

libqpidbroker_la_LIBADD = libqpidcommon.la
libqpidbroker_la_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
libqpidbroker_la_SOURCES = \
  $(broker)/AccumulatedAck.cpp \
  $(broker)/AutoDelete.cpp \
  $(broker)/Broker.cpp \
  $(broker)/BrokerAdapter.cpp \
  $(broker)/BrokerSingleton.cpp \
  $(broker)/BrokerChannel.cpp \
  $(broker)/BrokerMessage.cpp \
  $(broker)/BrokerMessageMessage.cpp \
  $(broker)/BrokerQueue.cpp \
  $(broker)/Configuration.cpp \
  $(broker)/Connection.cpp \
  $(broker)/ConnectionFactory.cpp \
  $(broker)/DeliverableMessage.cpp \
  $(broker)/DeliveryRecord.cpp \
  $(broker)/DirectExchange.cpp \
  $(broker)/ExchangeRegistry.cpp \
  $(broker)/FanOutExchange.cpp \
  $(broker)/HeadersExchange.cpp \
  $(broker)/InMemoryContent.cpp \
  $(broker)/LazyLoadedContent.cpp \
  $(broker)/MessageBuilder.cpp \
  $(broker)/MessageHandlerImpl.cpp \
  $(broker)/MessageStoreModule.cpp \
  $(broker)/NameGenerator.cpp \
  $(broker)/NullMessageStore.cpp \
  $(broker)/QueuePolicy.cpp \
  $(broker)/QueueRegistry.cpp \
  $(broker)/RecoveryManagerImpl.cpp \
  $(broker)/Reference.cpp \
  $(broker)/TopicExchange.cpp \
  $(broker)/TxAck.cpp \
  $(broker)/TxBuffer.cpp \
  $(broker)/TxPublish.cpp

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

nobase_include_HEADERS = \
  $(platform_hdr) \
  $(broker)/AccumulatedAck.h \
  $(broker)/AutoDelete.h \
  $(broker)/BrokerChannel.h \
  $(broker)/BrokerExchange.h \
  $(broker)/BrokerMessage.h \
  $(broker)/BrokerMessageBase.h \
  $(broker)/BrokerQueue.h \
  $(broker)/CompletionHandler.h \
  $(broker)/Configuration.h \
  $(broker)/Consumer.h \
  $(broker)/Deliverable.h \
  $(broker)/DeliverableMessage.h \
  $(broker)/DirectExchange.h \
  $(broker)/ExchangeRegistry.h \
  $(broker)/FanOutExchange.h \
  $(broker)/HandlerImpl.h \
  $(broker)/InMemoryContent.h \
  $(broker)/MessageBuilder.h \
  $(broker)/MessageHandlerImpl.h \
  $(broker)/MessageStoreModule.h \
  $(broker)/NameGenerator.h \
  $(broker)/NullMessageStore.h \
  $(broker)/Persistable.h \
  $(broker)/Prefetch.h \
  $(broker)/QueueRegistry.h \
  $(broker)/RecoverableMessage.h \
  $(broker)/RecoverableQueue.h \
  $(broker)/RecoveryManager.h \
  $(broker)/Reference.h \
  $(broker)/TxBuffer.h \
  $(broker)/TxOp.h \
  $(broker)/TxPublish.h \
  $(broker)/Broker.h \
  $(broker)/BrokerAdapter.h \
  $(broker)/BrokerMessageMessage.h \
  $(broker)/BrokerSingleton.h \
  $(broker)/Connection.h \
  $(broker)/ConnectionFactory.h \
  $(broker)/ConnectionToken.h \
  $(broker)/Content.h \
  $(broker)/DeliveryRecord.h \
  $(broker)/HeadersExchange.h \
  $(broker)/LazyLoadedContent.h \
  $(broker)/MessageStore.h \
  $(broker)/PersistableExchange.h \
  $(broker)/PersistableMessage.h \
  $(broker)/PersistableQueue.h \
  $(broker)/QueuePolicy.h \
  $(broker)/RecoveryManagerImpl.h \
  $(broker)/TopicExchange.h \
  $(broker)/TransactionalStore.h \
  $(broker)/TxAck.h \
  $(client)/AckMode.h \
  $(client)/BasicMessageChannel.h \
  $(client)/ClientAdapter.h \
  $(client)/ClientChannel.h \
  $(client)/ClientExchange.h \
  $(client)/ClientMessage.h \
  $(client)/ClientQueue.h \
  $(client)/Connection.h \
  $(client)/Connector.h \
  $(client)/IncomingMessage.h \
  $(client)/MessageChannel.h \
  $(client)/MessageListener.h \
  $(client)/MessageMessageChannel.h \
  $(client)/MethodBodyInstances.h \
  $(client)/ResponseHandler.h \
  $(client)/ReturnedMessageHandler.h \
  $(framing)/AMQBody.h \
  $(framing)/AMQContentBody.h \
  $(framing)/AMQDataBlock.h \
  $(framing)/AMQFrame.h \
  $(framing)/AMQHeaderBody.h \
  $(framing)/AMQHeartbeatBody.h \
  $(framing)/AMQMethodBody.h \
  $(framing)/AMQRequestBody.h \
  $(framing)/AMQResponseBody.h \
  $(framing)/BasicHeaderProperties.h \
  $(framing)/BodyHandler.h \
  $(framing)/Buffer.h \
  $(framing)/ChannelAdapter.h \
  $(framing)/Correlator.h \
  $(framing)/FieldTable.h \
  $(framing)/FramingContent.h \
  $(framing)/HeaderProperties.h \
  $(framing)/InitiationHandler.h \
  $(framing)/InputHandler.h \
  $(framing)/MethodContext.h \
  $(framing)/OutputHandler.h \
  $(framing)/ProtocolInitiation.h \
  $(framing)/ProtocolVersion.h \
  $(framing)/ProtocolVersionException.h \
  $(framing)/Proxy.h \
  $(framing)/Requester.h \
  $(framing)/Responder.h \
  $(framing)/Value.h \
  $(framing)/amqp_framing.h \
  $(framing)/amqp_types.h \
  $(framing)/amqp_types_full.h \
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
  qpid/shared_ptr.h 

# Force build of qpidd during dist phase so help2man will work.
dist-hook: qpidd
