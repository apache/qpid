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
# qmf agent library makefile fragment, to be included in Makefile.am
# 
lib_LTLIBRARIES += \
  libqmfcommon.la \
  libqmfagent.la

module_hdr += \
  qpid/agent/ManagementAgent.h \
  qpid/agent/ManagementAgentImpl.h \
  qpid/agent/QmfAgentImportExport.h \
  qmf/Agent.h \
  qmf/Console.h \
  qmf/Event.h \
  qmf/Message.h \
  qmf/MessageImpl.h \
  qmf/Object.h \
  qmf/ObjectId.h \
  qmf/ObjectIdImpl.h \
  qmf/ObjectImpl.h \
  qmf/Query.h \
  qmf/QueryImpl.h \
  qmf/ResilientConnection.h \
  qmf/Schema.h \
  qmf/SchemaImpl.h \
  qmf/Typecode.h \
  qmf/Value.h \
  qmf/ValueImpl.h

libqmfcommon_la_SOURCES = \
  qmf/Agent.cpp \
  qmf/ResilientConnection.cpp \
  qmf/MessageImpl.cpp \
  qmf/SchemaImpl.cpp \
  qmf/ValueImpl.cpp \
  qmf/ObjectIdImpl.cpp \
  qmf/ObjectImpl.cpp \
  qmf/QueryImpl.cpp

libqmfagent_la_SOURCES = \
  qpid/agent/ManagementAgent.h \
  qpid/agent/ManagementAgentImpl.cpp \
  qpid/agent/ManagementAgentImpl.h \
  qmf/Agent.cpp

libqmfagent_la_LIBADD = libqpidclient.la
