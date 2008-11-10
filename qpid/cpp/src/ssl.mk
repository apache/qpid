#
# Makefile fragment, conditionally included in Makefile.am
# 
libsslcommon_la_SOURCES = \
  qpid/sys/ssl/check.h \
  qpid/sys/ssl/check.cpp \
  qpid/sys/ssl/util.h \
  qpid/sys/ssl/util.cpp \
  qpid/sys/ssl/SslSocket.h \
  qpid/sys/ssl/SslSocket.cpp \
  qpid/sys/ssl/SslIo.h \
  qpid/sys/ssl/SslIo.cpp

libsslcommon_la_LIBADD= -lnss3 -lssl3 -lnspr4 libqpidcommon.la

libsslcommon_la_CXXFLAGS=$(AM_CXXFLAGS) $(SSL_CFLAGS)

lib_LTLIBRARIES +=  libsslcommon.la

ssl_la_SOURCES = \
  qpid/sys/SslPlugin.cpp \
  qpid/sys/ssl/SslHandler.h \
  qpid/sys/ssl/SslHandler.cpp

ssl_la_LIBADD= libqpidbroker.la libsslcommon.la

ssl_la_CXXFLAGS=$(AM_CXXFLAGS) $(SSL_CFLAGS)

ssl_la_LDFLAGS = $(PLUGINLDFLAGS)

dmodule_LTLIBRARIES += ssl.la


sslconnector_la_SOURCES = \
  qpid/client/SslConnector.cpp

sslconnector_la_LIBADD = \
  libqpidclient.la \
  libsslcommon.la

sslconnector_la_CXXFLAGS = $(AM_CXXFLAGS) -DCONF_FILE=\"$(confdir)/qpidc.conf\"

sslconnector_la_LDFLAGS = $(PLUGINLDFLAGS)

cmodule_LTLIBRARIES += \
  sslconnector.la
