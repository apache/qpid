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

#
# qmf library makefile fragment, to be included in Makefile.am
# 
lib_LTLIBRARIES +=	\
  libqmf.la		\
  libqmfengine.la	\
  libqmf2.la

#
# Public headers for the QMF API
#
QMF_API =					\
  ../include/qpid/agent/ManagementAgent.h	\
  ../include/qpid/agent/QmfAgentImportExport.h

#
# Public headers for the QMF2 API
#
QMF2_API =				\
  ../include/qmf/AgentEvent.h		\
  ../include/qmf/Agent.h		\
  ../include/qmf/AgentSession.h		\
  ../include/qmf/ConsoleEvent.h		\
  ../include/qmf/ConsoleSession.h	\
  ../include/qmf/DataAddr.h		\
  ../include/qmf/Data.h			\
  ../include/qmf/posix/EventNotifier.h	\
  ../include/qmf/exceptions.h		\
  ../include/qmf/Handle.h		\
  ../include/qmf/ImportExport.h		\
  ../include/qmf/Query.h		\
  ../include/qmf/Schema.h		\
  ../include/qmf/SchemaId.h		\
  ../include/qmf/SchemaMethod.h		\
  ../include/qmf/SchemaProperty.h	\
  ../include/qmf/SchemaTypes.h		\
  ../include/qmf/Subscription.h


#
# Public headers for the QMF Engine API
#
QMF_ENGINE_API =				\
  ../include/qmf/engine/Agent.h			\
  ../include/qmf/engine/ConnectionSettings.h	\
  ../include/qmf/engine/Console.h		\
  ../include/qmf/engine/Event.h			\
  ../include/qmf/engine/Message.h		\
  ../include/qmf/engine/Object.h		\
  ../include/qmf/engine/ObjectId.h		\
  ../include/qmf/engine/QmfEngineImportExport.h	\
  ../include/qmf/engine/Query.h			\
  ../include/qmf/engine/ResilientConnection.h	\
  ../include/qmf/engine/Schema.h		\
  ../include/qmf/engine/Typecode.h		\
  ../include/qmf/engine/Value.h

# Public header files
nobase_include_HEADERS +=	\
  $(QMF_API)			\
  $(QMF_ENGINE_API)		\
  $(QMF2_API)

libqmf_la_SOURCES =			\
  $(QMF_API)				\
  qpid/agent/ManagementAgentImpl.cpp	\
  qpid/agent/ManagementAgentImpl.h

libqmf2_la_SOURCES = 		\
  $(QMF2_API)			\
  qmf/agentCapability.h		\
  qmf/Agent.cpp			\
  qmf/AgentEvent.cpp		\
  qmf/AgentEventImpl.h		\
  qmf/AgentImpl.h		\
  qmf/AgentSession.cpp		\
  qmf/AgentSessionImpl.h	\
  qmf/AgentSubscription.cpp	\
  qmf/AgentSubscription.h	\
  qmf/BrokerImportExport.h	\
  qmf/ConsoleEvent.cpp		\
  qmf/ConsoleEventImpl.h	\
  qmf/ConsoleSession.cpp	\
  qmf/ConsoleSessionImpl.h	\
  qmf/constants.cpp		\
  qmf/constants.h		\
  qmf/DataAddr.cpp		\
  qmf/DataAddrImpl.h		\
  qmf/Data.cpp			\
  qmf/DataImpl.h		\
  qmf/EventNotifierImpl.cpp	\
  qmf/EventNotifierImpl.h	\
  qmf/exceptions.cpp		\
  qmf/Expression.cpp		\
  qmf/Expression.h		\
  qmf/Hash.cpp			\
  qmf/Hash.h			\
  qmf/PosixEventNotifier.cpp	\
  qmf/PosixEventNotifierImpl.cpp \
  qmf/PosixEventNotifierImpl.h	\
  qmf/PrivateImplRef.h		\
  qmf/Query.cpp			\
  qmf/QueryImpl.h		\
  qmf/SchemaCache.cpp		\
  qmf/SchemaCache.h		\
  qmf/Schema.cpp		\
  qmf/SchemaId.cpp		\
  qmf/SchemaIdImpl.h		\
  qmf/SchemaImpl.h		\
  qmf/SchemaMethod.cpp		\
  qmf/SchemaMethodImpl.h	\
  qmf/SchemaProperty.cpp	\
  qmf/SchemaPropertyImpl.h	\
  qmf/Subscription.cpp		\
  qmf/SubscriptionImpl.h

libqmfengine_la_SOURCES =			\
  $(QMF_ENGINE_API)				\
  qmf/engine/Agent.cpp				\
  qmf/engine/BrokerProxyImpl.cpp		\
  qmf/engine/BrokerProxyImpl.h			\
  qmf/engine/ConnectionSettingsImpl.cpp		\
  qmf/engine/ConnectionSettingsImpl.h		\
  qmf/engine/ConsoleImpl.cpp			\
  qmf/engine/ConsoleImpl.h			\
  qmf/engine/EventImpl.cpp			\
  qmf/engine/EventImpl.h			\
  qmf/engine/MessageImpl.cpp			\
  qmf/engine/MessageImpl.h			\
  qmf/engine/ObjectIdImpl.cpp			\
  qmf/engine/ObjectIdImpl.h			\
  qmf/engine/ObjectImpl.cpp			\
  qmf/engine/ObjectImpl.h			\
  qmf/engine/Protocol.cpp			\
  qmf/engine/Protocol.h				\
  qmf/engine/QueryImpl.cpp			\
  qmf/engine/QueryImpl.h			\
  qmf/engine/ResilientConnection.cpp		\
  qmf/engine/SequenceManager.cpp		\
  qmf/engine/SequenceManager.h			\
  qmf/engine/SchemaImpl.cpp			\
  qmf/engine/SchemaImpl.h			\
  qmf/engine/ValueImpl.cpp			\
  qmf/engine/ValueImpl.h

libqmf_la_LIBADD = libqmfengine.la
libqmf2_la_LIBADD = libqpidmessaging.la libqpidtypes.la
libqmfengine_la_LIBADD = libqpidclient.la

QMF_VERSION_INFO = 1:0:0
QMF2_VERSION_INFO = 1:0:0
QMFENGINE_VERSION_INFO  = 1:1:0

libqmf_la_LDFLAGS = -version-info $(QMF_VERSION_INFO)
libqmf2_la_LDFLAGS = -version-info $(QMF2_VERSION_INFO)
libqmfengine_la_LDFLAGS = -version-info $(QMFENGINE_VERSION_INFO)

pkgconfig_DATA += qmf2.pc
