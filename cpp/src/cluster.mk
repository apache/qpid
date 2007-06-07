#-*-Makefile-*-
# Cluster library makefile fragment, to be included in Makefile.am
# 

lib_LTLIBRARIES += libqpidcluster.la

libqpidcluster_la_SOURCES = \
  qpid/cluster/Cpg.cpp \
  qpid/cluster/Cpg.h

libqpidcluster_la_LIBADD= -lcpg
