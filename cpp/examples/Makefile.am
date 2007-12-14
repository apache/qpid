
# List all example files here
nobase_pkgdata_DATA=						\
	examples/Makefile					\
	examples/request-response/client.cpp			\
	examples/request-response/server.cpp			\
	examples/request-response/Makefile			\
	examples/fanout/Makefile				\
	examples/fanout/declare_queues.cpp			\
	examples/fanout/listener.cpp				\
	examples/fanout/fanout_publisher.cpp			\
	examples/pub-sub/Makefile				\
	examples/pub-sub/topic_publisher.cpp			\
	examples/pub-sub/topic_listener.cpp			\
	examples/direct/Makefile				\
	examples/direct/direct_publisher.cpp			\
	examples/direct/direct_persistent_config_queues.cpp	\
	examples/direct/listener.cpp				\
	examples/direct/declare_queues.cpp			\
	examples/direct/direct_persistent_publisher.cpp

EXTRA_DIST=$(nobase_pkgdata_DATA)


# Note: we don't use normal automake SUBDIRS because the example
# makefiles don't understand all the recursive automake targets.

clean-local:
	cd examples; $(MAKE) clean
	rm -f examples/*/*.out  examples/*/*.wait 

abs_srcdir=@abs_srcdir@
abs_top_builddir=@abs_top_builddir@
abs_top_srcdir=@abs_top_srcdir@

# Build the examples in the source tree.
all-local:
	cd examples && $(MAKE) CXX="$(CXX)" CXXFLAGS="-I../../$(top_srcdir)/src -I../../$(top_srcdir)/src/gen -I../../$(top_builddir)/src/gen -L../../$(top_builddir)/src/.libs -Wl,-rpath,$(abs_top_builddir)/src/.libs" all

# Verify the examples in the buid tree.
check-local:
	cd examples && QPIDD=$(abs_top_builddir)/src/qpidd $(abs_top_srcdir)/examples/verify

# Build and verify the installed examples, then clean up to avoid rpmbuild warnings.
EXAMPLE_FLAGS=-I$(DESTDIR)$(includedir) -L$(DESTDIR)$(libdir) -Wl,-rpath,$(DESTDIR)$(libdir)
EXAMPLE_DIR=$(DESTDIR)$(pkgdatadir)/examples 
installcheck-local:
	cd $(EXAMPLE_DIR) && $(MAKE) CXX="$(CXX)" CXXFLAGS="$(EXAMPLE_FLAGS)" all
	cd $(EXAMPLE_DIR) && QPIDD=$(DESTDIR)$(sbindir)/qpidd $(abs_srcdir)/verify
	cd $(EXAMPLE_DIR) && $(MAKE) clean

