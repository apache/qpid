
if HAVE_RUBY_DEVEL

INCLUDES = -I$(top_srcdir)/src/qmf -I$(top_srcdir)/src -I$(top_builddir)/src/gen

EXTRA_DIST = ruby.i

generated_file_list = \
	qmfengine.cpp

rubylibdir = $(RUBY_LIB)

dist_rubylib_DATA = qmf.rb

$(generated_file_list): $(srcdir)/ruby.i $(srcdir)/../qmfengine.i
	swig -ruby -c++ -Wall -I/usr/include $(INCLUDES) $(QPID_CXXFLAGS) -o qmfengine.cpp $(srcdir)/ruby.i

AM_CPPFLAGS = $(QPID_CXXFLAGS) $(INCLUDES) -I$(srcdir) -I$(RUBY_INC) -I$(RUBY_INC_ARCH)
AM_CXXFLAGS = $(QPID_CXXFLAGS)

rubylibarchdir = $(RUBY_LIB_ARCH)
rubylibarch_LTLIBRARIES = qmfengine.la

qmfengine_la_LDFLAGS = -avoid-version -module -shrext ".$(RUBY_DLEXT)"
qmfengine_la_LIBADD = $(RUBY_LIBS) -L$(top_builddir)/src/.libs -lqpidclient $(top_builddir)/src/libqmfcommon.la

qmfengine_la_SOURCES = \
	qmfengine.cpp

endif # HAVE_RUBY_DEVEL
