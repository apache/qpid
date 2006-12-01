AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES = \
  -I$(top_srcdir)/gen \
  -I$(top_srcdir)/lib/common/sys \
  -I$(top_srcdir)/lib/common/framing

# This is a kludge to include the contents of the apr/ directory.
# For now, we don't use it.
EXTRA_DIST =			\
  sys/apr/APRAcceptor.cpp	\
  sys/apr/APRBase.cpp		\
  sys/apr/APRBase.h		\
  sys/apr/APRPool.cpp		\
  sys/apr/APRPool.h		\
  sys/apr/APRSocket.cpp		\
  sys/apr/APRSocket.h		\
  sys/apr/LFProcessor.cpp	\
  sys/apr/LFProcessor.h		\
  sys/apr/LFSessionContext.cpp	\
  sys/apr/LFSessionContext.h	\
  sys/apr/Socket.cpp		\
  sys/apr/Thread.cpp


framing = framing
posix   = sys/posix
gen     = $(srcdir)/../../gen

lib_LTLIBRARIES = libcommon.la
libcommon_la_LIBADD =		\
  $(LIB_DLOPEN)			\
  $(LIB_CLOCK_GETTIME)

libcommon_la_LDFLAGS =		\
  -version-info			\
  $(LIBTOOL_VERSION_INFO_ARG)

libcommon_la_SOURCES =				\
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
  $(posix)/PosixAcceptor.cpp			\
  $(posix)/Socket.cpp				\
  $(posix)/Thread.cpp				\
  $(posix)/check.cpp				\
  $(posix)/check.h				\
  $(posix)/EventChannel.cpp			\
  $(posix)/EventChannel.h			\
  $(posix)/EventChannelThreads.cpp		\
  $(posix)/EventChannelThreads.h		\
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
