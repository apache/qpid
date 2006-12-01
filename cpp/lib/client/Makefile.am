AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES = \
  -I$(top_srcdir)/gen \
  -I$(top_srcdir)/lib/common \
  -I$(top_srcdir)/lib/common/sys \
  -I$(top_srcdir)/lib/common/framing

lib_LTLIBRARIES = libclient.la
libclient_la_LDFLAGS = -version-info $(LIBTOOL_VERSION_INFO_ARG)
libclient_la_SOURCES =				\
  ClientChannel.cpp				\
  ClientChannel.h				\
  ClientExchange.cpp				\
  ClientExchange.h				\
  ClientMessage.cpp				\
  ClientMessage.h				\
  ClientQueue.cpp				\
  ClientQueue.h					\
  Connection.cpp				\
  Connection.h					\
  Connector.cpp					\
  Connector.h					\
  IncomingMessage.cpp				\
  IncomingMessage.h				\
  MessageListener.cpp				\
  MessageListener.h				\
  MethodBodyInstances.h				\
  ResponseHandler.cpp				\
  ResponseHandler.h				\
  ReturnedMessageHandler.cpp			\
  ReturnedMessageHandler.h
