SUBDIRS = direct fanout pub-sub request-response failover
if HAVE_XML
  SUBDIRS += xml-exchange
  broker_args = "--no-module-dir --data-dir \"\" --auth no --load-module $(top_builddir)/src/.libs/xml.so"	
endif
if !HAVE_XML
  exclude_examples_regexp="xml"	# Exclude XML examples.
  broker_args = "--no-module-dir --data-dir \"\" --auth no"	
endif

MAKEDIST=.libs/Makefile
SUBMAKE='	for d in $(SUBDIRS) ; do $$(MAKE) -C $$$$d $$@ ; done'
$(MAKEDIST): Makefile
	mkdir -p .libs
	@$(ECHO) all: 		        > $(MAKEDIST)
	@$(ECHO) $(SUBMAKE)             >> $(MAKEDIST)
	@$(ECHO) clean:                 >> $(MAKEDIST)
	@$(ECHO) $(SUBMAKE)             >> $(MAKEDIST)

examplesdir=$(pkgdatadir)/examples
examples_DATA = README $(MAKEDIST)

EXTRA_DIST = $(examples_DATA) README.verify verify verify_all 

# For older versions of automake
abs_top_srcdir = @abs_top_srcdir@

# Verify the examples in the buid tree.
check-local: 
	$(srcdir)/verify_all $(abs_top_srcdir)/.. $(top_builddir)/src/qpidd $(broker_args) $(exclude_examples_regexp)

