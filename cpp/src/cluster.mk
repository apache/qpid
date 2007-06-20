#-*-Makefile-*-
# Cluster library makefile fragment, to be included in Makefile.am
# 
lib_LTLIBRARIES += libqpidcluster.la

if CLUSTER

libqpidcluster_la_SOURCES = \
  qpid/cluster/Cpg.cpp \
  qpid/cluster/Cpg.h
libqpidcluster_la_LIBADD= -lcpg

else
# Empty stub library to satisfy rpm spec file.
libqpidcluster_la_SOURCES = 
endif
