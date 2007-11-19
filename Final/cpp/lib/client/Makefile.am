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

lib_LTLIBRARIES = libqpidclient.la
libqpidclient_la_LIBADD = ../common/libqpidcommon.la
libqpidclient_la_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
libqpidclient_la_SOURCES =			\
  ClientChannel.cpp				\
  ClientExchange.cpp				\
  ClientMessage.cpp				\
  ClientQueue.cpp				\
  Connection.cpp				\
  Connector.cpp					\
  IncomingMessage.cpp				\
  MessageListener.cpp				\
  ResponseHandler.cpp				\
  ReturnedMessageHandler.cpp
pkginclude_HEADERS =				\
  ClientChannel.h				\
  ClientExchange.h				\
  ClientMessage.h				\
  ClientQueue.h					\
  Connection.h					\
  Connector.h					\
  IncomingMessage.h				\
  MessageListener.h				\
  MethodBodyInstances.h				\
  ResponseHandler.h				\
  ReturnedMessageHandler.h
