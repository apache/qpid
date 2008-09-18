#
# Cluster library makefile fragment, to be included in Makefile.am
# 
dmodule_LTLIBRARIES += cluster.la

if CPG

cluster_la_SOURCES = \
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
  qpid/cluster/ClusterHandler.h \
  qpid/cluster/ClusterHandler.cpp \
  qpid/cluster/JoiningHandler.h \
  qpid/cluster/JoiningHandler.cpp \
  qpid/cluster/MemberHandler.h \
  qpid/cluster/MemberHandler.cpp

cluster_la_LIBADD= -lcpg libqpidbroker.la libqpidclient.la

else
# Empty stub library to satisfy rpm spec file.
cluster_la_SOURCES = 

endif

cluster_la_LDFLAGS = $(PLUGINLDFLAGS)
