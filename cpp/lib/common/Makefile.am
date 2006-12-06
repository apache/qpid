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
  $(apr)/APRBase.h		\
  $(apr)/APRPool.cpp		\
  $(apr)/APRPool.h		\
  $(apr)/APRSocket.cpp		\
  $(apr)/APRSocket.h		\
  $(apr)/LFProcessor.cpp	\
  $(apr)/LFProcessor.h		\
  $(apr)/LFSessionContext.cpp	\
  $(apr)/LFSessionContext.h	\
  $(apr)/Socket.cpp		\
  $(apr)/Thread.cpp

posix = sys/posix
posix_src =					\
  $(posix)/PosixAcceptor.cpp			\
  $(posix)/Socket.cpp				\
  $(posix)/Thread.cpp				\
  $(posix)/check.cpp				\
  $(posix)/check.h				\
  $(posix)/EventChannel.cpp			\
  $(posix)/EventChannel.h			\
  $(posix)/EventChannelThreads.cpp		\
  $(posix)/EventChannelThreads.h

EXTRA_DIST=$(posix_src)
platform_src = $(apr_src)

framing = framing
gen     = $(srcdir)/../../gen

lib_LTLIBRARIES = libcommon.la
libcommon_la_LIBADD =		\
  $(APR_LIBS)			\
  $(LIB_DLOPEN)			\
  $(LIB_CLOCK_GETTIME)

libcommon_la_LDFLAGS =		\
  -version-info			\
  $(LIBTOOL_VERSION_INFO_ARG)

libcommon_la_SOURCES =				\
  $(platform_src)				\
  $(framing)/AMQBody.cpp			\
  $(framing)/AMQBody.h				\
  $(framing)/AMQContentBody.cpp			\
  $(framing)/AMQContentBody.h			\
  $(framing)/AMQDataBlock.h			\
  $(framing)/AMQFrame.cpp			\
  $(framing)/AMQFrame.h				\
  $(framing)/AMQHeaderBody.cpp			\
  $(framing)/AMQHeaderBody.h			\
  $(framing)/AMQHeartbeatBody.cpp		\
  $(framing)/AMQHeartbeatBody.h			\
  $(framing)/AMQMethodBody.cpp			\
  $(framing)/AMQMethodBody.h			\
  $(framing)/BasicHeaderProperties.cpp		\
  $(framing)/BasicHeaderProperties.h		\
  $(framing)/BodyHandler.cpp			\
  $(framing)/BodyHandler.h			\
  $(framing)/Buffer.cpp				\
  $(framing)/Buffer.h				\
  $(framing)/FieldTable.cpp			\
  $(framing)/FieldTable.h			\
  $(framing)/HeaderProperties.h			\
  $(framing)/InitiationHandler.cpp		\
  $(framing)/InitiationHandler.h		\
  $(framing)/InputHandler.h			\
  $(framing)/OutputHandler.h			\
  $(framing)/ProtocolInitiation.cpp		\
  $(framing)/ProtocolInitiation.h		\
  $(framing)/ProtocolVersion.cpp		\
  $(framing)/ProtocolVersion.h			\
  $(framing)/ProtocolVersionException.cpp	\
  $(framing)/ProtocolVersionException.h		\
  $(framing)/Value.cpp				\
  $(framing)/Value.h				\
  $(framing)/amqp_framing.h			\
  $(framing)/amqp_types.h			\
  $(gen)/AMQP_ClientProxy.cpp			\
  $(gen)/AMQP_MethodVersionMap.cpp		\
  $(gen)/AMQP_ServerProxy.cpp			\
  Exception.cpp					\
  Exception.h					\
  ExceptionHolder.cpp				\
  ExceptionHolder.h				\
  QpidError.cpp					\
  QpidError.h					\
  SharedObject.h				\
  sys/Acceptor.h				\
  sys/AtomicCount.h				\
  sys/Module.h					\
  sys/Monitor.h					\
  sys/Mutex.h					\
  sys/Runnable.cpp				\
  sys/Runnable.h				\
  sys/SessionContext.h				\
  sys/SessionHandler.h				\
  sys/SessionHandlerFactory.h			\
  sys/ShutdownHandler.h				\
  sys/Socket.h					\
  sys/Thread.h					\
  sys/Time.cpp					\
  sys/Time.h					\
  sys/TimeoutHandler.h
