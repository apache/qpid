#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http:#www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

#
# qmf console library makefile fragment, to be included in Makefile.am
# 
lib_LTLIBRARIES += libqmfconsole.la

libqmfconsole_la_SOURCES = \
  qpid/console/Agent.h \
  qpid/console/Agent.cpp \
  qpid/console/Broker.h \
  qpid/console/Broker.cpp \
  qpid/console/ClassKey.h \
  qpid/console/ClassKey.cpp \
  qpid/console/ConsoleListener.h \
  qpid/console/Event.h \
  qpid/console/Object.h \
  qpid/console/Object.cpp \
  qpid/console/ObjectId.h \
  qpid/console/ObjectId.cpp \
  qpid/console/Package.h \
  qpid/console/Package.cpp \
  qpid/console/Schema.h \
  qpid/console/Schema.cpp \
  qpid/console/SequenceManager.h \
  qpid/console/SequenceManager.cpp \
  qpid/console/SessionManager.h \
  qpid/console/SessionManager.cpp \
  qpid/console/Value.h \
  qpid/console/Value.cpp

libqmfconsole_la_LIBADD = libqpidclient.la

