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
  Basic.cpp					\
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
  Basic.h					\
  Connection.h					\
  Connector.h					\
  IncomingMessage.h				\
  MessageListener.h				\
  MethodBodyInstances.h				\
  ResponseHandler.h				\
  ReturnedMessageHandler.h
