#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES =				\
  -I$(top_srcdir)/gen			\
  -I$(top_srcdir)/lib/common		\
  -I$(top_srcdir)/lib/common/sys	\
  -I$(top_srcdir)/lib/common/framing	\
  $(APR_CXXFLAGS)

lib_LTLIBRARIES = libqpidbroker.la
libqpidbroker_la_LIBADD =			\
  ../common/libqpidcommon.la			\
  -lboost_iostreams

libqpidbroker_la_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
libqpidbroker_la_SOURCES =			\
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
  ConnectionToken.h				\
  Consumer.h					\
  Content.h					\
  Daemon.cpp					\
  Daemon.h					\
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


# Force build during dist phase so help2man will work.
dist-hook: $(lib_LTLIBRARIES)
