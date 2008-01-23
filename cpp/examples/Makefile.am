
# List all example files here
nobase_pkgdata_DATA=						\
	examples/Makefile					\
	examples/request-response/client.cpp			\
	examples/request-response/server.cpp			\
	examples/request-response/Makefile			\
	examples/fanout/Makefile				\
	examples/fanout/declare_queues.cpp			\
	examples/fanout/listener.cpp				\
	examples/fanout/fanout_producer.cpp			\
	examples/pub-sub/Makefile				\
	examples/pub-sub/topic_publisher.cpp			\
	examples/pub-sub/topic_listener.cpp			\
	examples/direct/Makefile				\
	examples/direct/direct_producer.cpp			\
	examples/direct/listener.cpp				\
	examples/direct/declare_queues.cpp

VERIFY_SCRIPT=verify \
	examples/request-response/verify.in \
	examples/fanout/verify.in \
	examples/pub-sub/verify.in \
	examples/direct/verify.in 

EXTRA_DIST=$(nobase_pkgdata_DATA) $(VERIFY_SCRIPT)

# Note: we don't use normal automake SUBDIRS because the example
# makefiles don't understand all the recursive automake targets.

clean-local:
	cd examples; $(MAKE) clean
	rm -f examples/*/*.out  examples/*/*.wait 

abs_top_builddir=@abs_top_builddir@
abs_top_srcdir=@abs_top_srcdir@

VERIFY=$(abs_top_srcdir)/examples/verify

# Build the examples in the source tree.
all-local:
	cd examples && $(MAKE) CXX="$(CXX)" CXXFLAGS="$(CXXFLAGS) -I../../$(top_srcdir)/src -I../../$(top_srcdir)/src/gen -I../../$(top_builddir)/src/gen -L../../$(top_builddir)/src/.libs -Wl,-rpath,$(abs_top_builddir)/src/.libs" all

# Verify the examples in the buid tree.
check-local: all-local
	cd examples && QPIDD=$(abs_top_builddir)/src/qpidd $(VERIFY)

# Build and verify the installed examples, then clean up to avoid rpmbuild warnings.
EXAMPLE_FLAGS=-I$(DESTDIR)$(includedir) -L$(DESTDIR)$(libdir) -Wl,-rpath,$(DESTDIR)$(libdir)
EXAMPLE_DIR=$(DESTDIR)$(pkgdatadir)/examples 
installcheck-local:
	cd $(EXAMPLE_DIR) && $(MAKE) CXX="$(CXX)" CXXFLAGS="$(EXAMPLE_FLAGS)" all
	cd $(EXAMPLE_DIR) && QPIDD=$(sbindir)/qpidd $(VERIFY)
	cd $(EXAMPLE_DIR) && $(MAKE) clean

# Tarball with verify script that can be untarred & run on
# installed examples.
verify.tar: $(VERIFY_SCRIPT)
	tar cf verify.tar $(VERIFY_SCRIPT)
