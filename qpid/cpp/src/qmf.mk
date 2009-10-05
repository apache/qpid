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
lib_LTLIBRARIES +=				\
  libqmfcommon.la				\
  libqmfagent.la

# Public header files
nobase_include_HEADERS +=			\
  ../include/qpid/agent/ManagementAgent.h	\
  ../include/qpid/agent/QmfAgentImportExport.h	\
  ../include/qmf/Agent.h			\
  ../include/qmf/Connection.h			\
  ../include/qmf/QmfImportExport.h		\
  ../include/qmf/ConnectionSettings.h		\
  ../include/qmf/AgentObject.h

libqmfcommon_la_SOURCES =			\
  qmf/ConnectionSettingsImpl.cpp		\
  qmf/ConnectionSettingsImpl.h			\
  qmf/ConsoleEngine.h				\
  qmf/Event.h					\
  qmf/Message.h					\
  qmf/MessageImpl.cpp				\
  qmf/MessageImpl.h				\
  qmf/Object.h					\
  qmf/ObjectId.h				\
  qmf/ObjectIdImpl.cpp				\
  qmf/ObjectIdImpl.h				\
  qmf/ObjectImpl.cpp				\
  qmf/ObjectImpl.h				\
  qmf/Query.h					\
  qmf/QueryImpl.cpp				\
  qmf/QueryImpl.h				\
  qmf/ResilientConnection.cpp			\
  qmf/ResilientConnection.h			\
  qmf/Schema.h					\
  qmf/SchemaImpl.cpp				\
  qmf/SchemaImpl.h				\
  qmf/Typecode.h				\
  qmf/Value.h					\
  qmf/ValueImpl.cpp				\
  qmf/ValueImpl.h

libqmfagent_la_SOURCES =			\
  qmf/AgentEngine.cpp				\
  qmf/AgentEngine.h				\
  qpid/agent/ManagementAgentImpl.cpp		\
  qpid/agent/ManagementAgentImpl.h

libqmfagent_la_LIBADD = libqpidclient.la libqmfcommon.la
