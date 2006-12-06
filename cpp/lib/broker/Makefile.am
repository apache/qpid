AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES =				\
  -I$(top_srcdir)/gen			\
  -I$(top_srcdir)/lib/common		\
  -I$(top_srcdir)/lib/common/sys	\
  -I$(top_srcdir)/lib/common/framing	\
  $(APR_CXXFLAGS)

lib_LTLIBRARIES = libbroker.la
libbroker_la_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
libbroker_la_SOURCES =				\
  AccumulatedAck.cpp				\
  AccumulatedAck.h				\
  AutoDelete.cpp				\
  AutoDelete.h					\
  Binding.h					\
  Broker.cpp					\
  Broker.h					\
  BrokerChannel.cpp				\
  BrokerChannel.h				\
  BrokerExchange.h				\
  BrokerMessage.cpp				\
  BrokerMessage.h				\
  BrokerQueue.cpp				\
  BrokerQueue.h					\
  Configuration.cpp				\
  Configuration.h				\
  ConnectionToken.h				\
  Consumer.h					\
  Content.h					\
  DeletingTxOp.cpp				\
  DeletingTxOp.h				\
  Deliverable.h					\
  DeliverableMessage.cpp			\
  DeliverableMessage.h				\
  DeliveryRecord.cpp				\
  DeliveryRecord.h				\
  DirectExchange.cpp				\
  DirectExchange.h				\
  ExchangeBinding.cpp				\
  ExchangeBinding.h				\
  ExchangeRegistry.cpp				\
  ExchangeRegistry.h				\
  FanOutExchange.cpp				\
  FanOutExchange.h				\
  HeadersExchange.cpp				\
  HeadersExchange.h				\
  InMemoryContent.cpp				\
  InMemoryContent.h				\
  LazyLoadedContent.cpp				\
  LazyLoadedContent.h				\
  MessageBuilder.cpp				\
  MessageBuilder.h				\
  MessageStore.h				\
  MessageStoreModule.cpp			\
  MessageStoreModule.h				\
  NameGenerator.cpp				\
  NameGenerator.h				\
  NullMessageStore.cpp				\
  NullMessageStore.h				\
  Prefetch.h					\
  QueuePolicy.cpp				\
  QueuePolicy.h					\
  QueueRegistry.cpp				\
  QueueRegistry.h				\
  RecoveryManager.cpp				\
  RecoveryManager.h				\
  SessionHandlerFactoryImpl.cpp			\
  SessionHandlerFactoryImpl.h			\
  SessionHandlerImpl.cpp			\
  SessionHandlerImpl.h				\
  TopicExchange.cpp				\
  TopicExchange.h				\
  TransactionalStore.h				\
  TxAck.cpp					\
  TxAck.h					\
  TxBuffer.cpp					\
  TxBuffer.h					\
  TxOp.h					\
  TxPublish.cpp					\
  TxPublish.h
