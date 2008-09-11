#
# acl library makefile fragment, to be included in Makefile.am
# 
dmodule_LTLIBRARIES += acl.la

acl_la_SOURCES = \
  qpid/acl/Acl.cpp \
  qpid/acl/Acl.h \
  qpid/acl/AclData.cpp \
  qpid/acl/AclData.h \
  qpid/acl/AclPlugin.cpp \
  qpid/acl/AclReader.cpp \
  qpid/acl/AclReader.h

acl_la_LIBADD = libqpidbroker.la
acl_la_LDFLAGS = $(PLUGINLDFLAGS)
