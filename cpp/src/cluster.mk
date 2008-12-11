#
# Cluster library makefile fragment, to be included in Makefile.am
# 

# Optional CMAN support

# Distribute all sources.
EXTRA_DIST += qpid/cluster/Quorum_cman.h qpid/cluster/Quorum_cman.cpp qpid/cluster/Quorum_null.h

if HAVE_LIBCMAN
CMAN_SOURCES = qpid/cluster/Quorum_cman.h qpid/cluster/Quorum_cman.cpp
libcman = -lcman
else
CMAN_SOURCES = qpid/cluster/Quorum_null.h
endif

if HAVE_LIBCPG

dmodule_LTLIBRARIES += cluster.la

cluster_la_SOURCES = \
  $(CMAN_SOURCES) \
  qpid/cluster/types.h \
  qpid/cluster/Cluster.cpp \
  qpid/cluster/Cluster.h \
  qpid/cluster/Cpg.cpp \
  qpid/cluster/Cpg.h \
  qpid/cluster/Dispatchable.h \
  qpid/cluster/ClusterPlugin.cpp \
  qpid/cluster/ConnectionCodec.h \
  qpid/cluster/ConnectionCodec.cpp \
  qpid/cluster/Connection.h \
  qpid/cluster/Connection.cpp \
  qpid/cluster/ConnectionMap.h \
  qpid/cluster/NoOpConnectionOutputHandler.h \
  qpid/cluster/WriteEstimate.h \
  qpid/cluster/WriteEstimate.cpp \
  qpid/cluster/OutputInterceptor.h \
  qpid/cluster/OutputInterceptor.cpp \
  qpid/cluster/ProxyInputHandler.h \
  qpid/cluster/Event.h \
  qpid/cluster/Event.cpp \
  qpid/cluster/DumpClient.h \
  qpid/cluster/DumpClient.cpp \
  qpid/cluster/ClusterMap.h \
  qpid/cluster/ClusterMap.cpp \
  qpid/cluster/FailoverExchange.h \
  qpid/cluster/FailoverExchange.cpp \
  qpid/cluster/Multicaster.h \
  qpid/cluster/Multicaster.cpp \
  qpid/cluster/ClusterLeaveException.h \
  qpid/cluster/Quorum.h

cluster_la_LIBADD=  -lcpg $(libcman) libqpidbroker.la libqpidclient.la
cluster_la_LDFLAGS = $(PLUGINLDFLAGS)

endif				# HAVE_LIBCPG
