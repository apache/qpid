
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

VERIFY_FILES= verify verify_all				\
	examples/request-response/verify		\
	examples/request-response/verify.in		\
	examples/request-response/verify_cpp_python	\
	examples/request-response/verify_cpp_python.in	\
	examples/request-response/verify_python_cpp	\
	examples/request-response/verify_python_cpp.in	\
	examples/fanout/verify				\
	examples/fanout/verify.in			\
	examples/fanout/verify_cpp_python		\
	examples/fanout/verify_cpp_python.in		\
	examples/fanout/verify_python_cpp		\
	examples/fanout/verify_python_cpp.in		\
	examples/pub-sub/verify				\
	examples/pub-sub/verify.in			\
	examples/pub-sub/verify_cpp_python		\
	examples/pub-sub/verify_cpp_python.in		\
	examples/pub-sub/verify_python_cpp		\
	examples/pub-sub/verify_python_cpp.in		\
	examples/direct/verify				\
	examples/direct/verify.in			\
	examples/direct/verify_cpp_python		\
	examples/direct/verify_cpp_python.in		\
	examples/direct/verify_python_cpp		\
	examples/direct/verify_python_cpp.in

EXTRA_DIST=$(nobase_pkgdata_DATA) $(VERIFY_FILES)

verify: 
	cp $(top_srcdir)/../bin/verify $@

# Note: we don't use normal automake SUBDIRS because the example
# makefiles don't understand all the recursive automake targets.

clean-local:
	cd examples; $(MAKE) clean
	rm -f examples/*/*.out  examples/*/*.wait 

abs_top_builddir=@abs_top_builddir@
abs_top_srcdir=@abs_top_srcdir@

# Build the examples - copy sources to the build tree in VPATH build.
all-local:
	test -d examples || cp -R $(srcdir)/examples .
	cd examples && $(MAKE) CXX="$(CXX)" CXXFLAGS="$(CXXFLAGS) -I../../$(top_srcdir)/src -I../../$(top_srcdir)/src/gen -I../../$(top_builddir)/src/gen -L../../$(top_builddir)/src/.libs -Wl,-rpath,$(abs_top_builddir)/src/.libs" all

# Verify the examples in the buid tree.
check-local: all-local verify
	$(srcdir)/verify_all $(abs_top_srcdir) $(abs_top_builddir)

# TODO:
#  create a tarball for testing installed examples.
#  installcheck-local to use the tarball on installed example and clean up after.
# Build and verify installed C++ examples, clean up to avoid rpmbuild warnings.
# EXAMPLE_FLAGS=-I$(DESTDIR)$(includedir) -L$(DESTDIR)$(libdir) -Wl,-rpath,$(DESTDIR)$(libdir)
# EXAMPLE_DIR=$(DESTDIR)$(pkgdatadir)/examples/cpp 
# installcheck-local:
# 	cd $(EXAMPLE_DIR) && $(MAKE) CXX="$(CXX)" CXXFLAGS="$(EXAMPLE_FLAGS)" all
# 	cd $(EXAMPLE_DIR) && QPIDD=$(sbindir)/qpidd $(srcdir)/verify *
# 	cd $(EXAMPLE_DIR) && $(MAKE) clean

