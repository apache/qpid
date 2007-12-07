
nobase_pkgdata_DATA=						\
	examples/Makefile					\
	examples/request-response/client.cpp			\
	examples/request-response/server.cpp			\
	examples/request-response/Makefile			\
	examples/fanout/Makefile				\
	examples/fanout/fanout_config_queues.cpp		\
	examples/fanout/listener.cpp				\
	examples/fanout/fanout_publisher.cpp			\
	examples/pub-sub/Makefile				\
	examples/pub-sub/topic_publisher.cpp			\
	examples/pub-sub/topic_listener.cpp			\
	examples/direct/Makefile				\
	examples/direct/direct_publisher.cpp			\
	examples/direct/direct_persistent_config_queues.cpp	\
	examples/direct/listener.cpp				\
	examples/direct/direct_config_queues.cpp		\
	examples/direct/direct_persistent_publisher.cpp

EXTRA_DIST=$(nobase_pkgdata_DATA)

# Note: we don't use normal automake SUBDIRS because the example
# makefiles don't understand all the recursive automake targets.

clean:
	cd examples; $(MAKE) clean

# Build the installed examples, then clean up to avoid rpmbuild warnings.
EXAMPLE_FLAGS=-I$(DESTDIR)$(includedir) -L$(DESTDIR)$(libdir) -Wl,-rpath,$(DESTDIR)$(libdir)
EXAMPLE_DIR=$(DESTDIR)$(pkgdatadir)/examples 
installcheck-local:
	cd $(EXAMPLE_DIR) && $(MAKE) CXX="$(CXX)" CXXFLAGS="$(EXAMPLE_FLAGS)" all
	cd $(EXAMPLE_DIR) && $(abs_srcdir)/verify
	cd $(EXAMPLE_DIR) && $(MAKE) clean
