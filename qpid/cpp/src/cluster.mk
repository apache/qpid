#
# Cluster library makefile fragment, to be included in Makefile.am
# 
lib_LTLIBRARIES += libqpidcluster.la

if CPG

libqpidcluster_la_SOURCES = \
  qpid/cluster/Cluster.cpp \
  qpid/cluster/Cluster.h \
  qpid/cluster/Cpg.cpp \
  qpid/cluster/Cpg.h \
  qpid/cluster/Dispatchable.h \
  qpid/cluster/ClusterPlugin.cpp \
  qpid/cluster/ClassifierHandler.h \
  qpid/cluster/ClassifierHandler.cpp 

libqpidcluster_la_LIBADD= -lcpg libqpidbroker.la

else
# Empty stub library to satisfy rpm spec file.
libqpidcluster_la_SOURCES = 

endif
