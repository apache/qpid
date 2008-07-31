#
# acl library makefile fragment, to be included in Makefile.am
# 
lib_LTLIBRARIES += libqpidacl.la

libqpidacl_la_SOURCES = \
  qpid/acl/Acl.cpp \
  qpid/acl/Acl.h \
  qpid/acl/AclPlugin.cpp 

libqpidacl_la_LIBADD= -lacl libqpidbroker.la


