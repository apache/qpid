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
  -I$(top_srcdir)/lib/common/sys	\
  -I$(top_srcdir)/lib/common/framing	\
  $(APR_CXXFLAGS)

apr = sys/apr
apr_src =			\
  $(apr)/APRAcceptor.cpp	\
  $(apr)/APRBase.cpp		\
  $(apr)/APRPool.cpp		\
  $(apr)/APRSocket.cpp		\
  $(apr)/LFProcessor.cpp	\
  $(apr)/LFSessionContext.cpp	\
  $(apr)/Socket.cpp		\
  $(apr)/Thread.cpp
apr_hdr =			\
  $(apr)/APRBase.h		\
  $(apr)/APRPool.h		\
  $(apr)/APRSocket.h		\
  $(apr)/LFProcessor.h		\
  $(apr)/LFSessionContext.h	

posix = sys/posix
posix_src =				\
  $(posix)/PosixAcceptor.cpp		\
  $(posix)/Socket.cpp			\
  $(posix)/Thread.cpp			\
  $(posix)/check.cpp			\
  $(posix)/EventChannel.cpp		\
  $(posix)/EventChannelThreads.cpp		
posix_hdr = 				\
  $(posix)/check.h			\
  $(posix)/EventChannel.h		\
  $(posix)/EventChannelThreads.h

EXTRA_DIST=$(posix_src) $(posix_hdr)
platform_src = $(apr_src)
platform_hdr = $(apr_hdr)

framing = framing
gen     = $(srcdir)/../../gen

lib_LTLIBRARIES = libqpidcommon.la
libqpidcommon_la_LIBADD =			\
  $(APR_LIBS)					\
  $(LIB_DLOPEN)					\
  $(LIB_CLOCK_GETTIME)				\
  -lboost_program_options

libqpidcommon_la_LDFLAGS =			\
  -version-info					\
  $(LIBTOOL_VERSION_INFO_ARG)

libqpidcommon_la_SOURCES =			\
  $(platform_src)				\
  $(framing)/AMQBody.cpp			\
  $(framing)/AMQContentBody.cpp			\
  $(framing)/AMQFrame.cpp			\
  $(framing)/AMQHeaderBody.cpp			\
  $(framing)/AMQHeartbeatBody.cpp		\
  $(framing)/AMQMethodBody.cpp			\
  $(framing)/BasicHeaderProperties.cpp		\
  $(framing)/BodyHandler.cpp			\
  $(framing)/Buffer.cpp				\
  $(framing)/FieldTable.cpp			\
  $(framing)/FramingContent.cpp			\
  $(framing)/InitiationHandler.cpp		\
  $(framing)/ProtocolInitiation.cpp		\
  $(framing)/ProtocolVersion.cpp		\
  $(framing)/ProtocolVersionException.cpp	\
  $(framing)/Value.cpp				\
  $(gen)/AMQP_ClientProxy.cpp			\
  $(gen)/AMQP_HighestVersion.h			\
  $(gen)/AMQP_MethodVersionMap.cpp		\
  $(gen)/AMQP_ServerProxy.cpp			\
  Exception.cpp					\
  ExceptionHolder.cpp				\
  QpidError.cpp					\
  CommonOptions.cpp				\
  sys/Runnable.cpp				\
  sys/Time.cpp

nobase_pkginclude_HEADERS =			\
  $(platform_hdr)				\
  $(framing)/AMQBody.h				\
  $(framing)/AMQContentBody.h			\
  $(framing)/AMQDataBlock.h			\
  $(framing)/AMQFrame.h				\
  $(framing)/AMQHeaderBody.h			\
  $(framing)/AMQHeartbeatBody.h			\
  $(framing)/AMQMethodBody.h			\
  $(framing)/BasicHeaderProperties.h		\
  $(framing)/BodyHandler.h			\
  $(framing)/Buffer.h				\
  $(framing)/FieldTable.h			\
  $(framing)/FramingContent.h			\
  $(framing)/HeaderProperties.h			\
  $(framing)/InitiationHandler.h		\
  $(framing)/InputHandler.h			\
  $(framing)/OutputHandler.h			\
  $(framing)/ProtocolInitiation.h		\
  $(framing)/ProtocolVersion.h			\
  $(framing)/ProtocolVersionException.h		\
  $(framing)/Value.h				\
  $(framing)/amqp_framing.h			\
  $(framing)/amqp_types.h			\
  $(posix_hdr)					\
  Exception.h					\
  ExceptionHolder.h				\
  QpidError.h					\
  SharedObject.h				\
  CommonOptions.h				\
  sys/Acceptor.h				\
  sys/AtomicCount.h				\
  sys/Module.h					\
  sys/Monitor.h					\
  sys/Mutex.h					\
  sys/Runnable.h				\
  sys/SessionContext.h				\
  sys/SessionHandler.h				\
  sys/SessionHandlerFactory.h			\
  sys/ShutdownHandler.h				\
  sys/Socket.h					\
  sys/Thread.h					\
  sys/Time.h					\
  sys/TimeoutHandler.h


# Force build during dist phase so help2man will work.
dist-hook: $(lib_LTLIBRARIES)
