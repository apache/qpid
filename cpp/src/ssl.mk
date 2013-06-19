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
# Makefile fragment, conditionally included in Makefile.am
#
libqpidcommon_la_SOURCES += \
  qpid/sys/ssl/check.h \
  qpid/sys/ssl/check.cpp \
  qpid/sys/ssl/util.h \
  qpid/sys/ssl/util.cpp \
  qpid/sys/ssl/SslSocket.h \
  qpid/sys/ssl/SslSocket.cpp

libqpidcommon_la_LIBADD += -lnss3 -lssl3 -lnspr4
libqpidcommon_la_CXXFLAGS += $(SSL_CFLAGS)

libqpidbroker_la_SOURCES += \
  qpid/sys/SslPlugin.cpp

libqpidbroker_la_CXXFLAGS += $(SSL_CFLAGS)

libqpidclient_la_SOURCES += \
  qpid/client/SslConnector.cpp

libqpidclient_la_CXXFLAGS += $(SSL_CFLAGS)

if HAVE_PROTON
libqpidclient_la_SOURCES += \
  qpid/messaging/amqp/SslTransport.cpp \
  qpid/messaging/amqp/SslTransport.h
endif #HAVE_PROTON
