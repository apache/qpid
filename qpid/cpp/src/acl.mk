#
# acl library makefile fragment, to be included in Makefile.am
# 
lib_LTLIBRARIES += libqpidacl.la

libqpidacl_la_SOURCES = \
  qpid/acl/Acl.cpp \
  qpid/acl/Acl.h \
  qpid/acl/AclData.cpp \
  qpid/acl/AclData.h \
  qpid/acl/AclPlugin.cpp \
  qpid/acl/AclReader.cpp \
  qpid/acl/AclReader.h

libqpidacl_la_LIBADD = libqpidbroker.la


