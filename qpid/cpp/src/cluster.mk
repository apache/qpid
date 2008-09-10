#
# Cluster library makefile fragment, to be included in Makefile.am
# 
lib_LTLIBRARIES += libqpidcluster.la

if CPG

libqpidcluster_la_SOURCES = \
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
  qpid/cluster/PollableCondition.h \
  qpid/cluster/PollableCondition.cpp \
  qpid/cluster/PollableQueue.h \
  qpid/cluster/WriteEstimate.h \
  qpid/cluster/WriteEstimate.cpp \
  qpid/cluster/OutputInterceptor.h \
  qpid/cluster/OutputInterceptor.cpp \
  qpid/cluster/ProxyInputHandler.h \
  qpid/cluster/Event.h \
  qpid/cluster/Event.cpp \
  qpid/cluster/DumpClient.h \
  qpid/cluster/DumpClient.cpp

libqpidcluster_la_LIBADD= -lcpg libqpidbroker.la libqpidclient.la

else
# Empty stub library to satisfy rpm spec file.
libqpidcluster_la_SOURCES = 

endif
