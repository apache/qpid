
html: Doxyfile doxygen.tstamp

# TODO aconway 2006-12-07: devdoc target to generate doc for all
# headers, with private/protected functions.

SOURCES = \
  $(wildcard $(topsrcdir)/gen/*.h) \
  $(wildcard $(topsrcdir)/lib/common/*.h) \
  $(wildcard $(topsrcdir)/lib/common/sys/*.h) \
  $(wildcard $(topsrcdir)/lib/common/framing/*.h) \
  $(wildcard $(topsrcdir)/lib/client/*.h) 

doxygen.tstamp: $(SOURCES)
	doxygen
	touch $@

clean-local:
	rm -rf docs.tstamp html man latex doxygen.tstamp xml

HTML_TARGET_DIR=$(pkgdatadir)/api/html

install-data-local:
	installfiles=`echo html/*`; \
	if test "$$installfiles" = 'html/*'; \
	then echo '-- Nothing to install' ; \
	else \
		$(mkinstalldirs) $(DESTDIR)$(HTML_TARGET_DIR); \
		for i in $$installfiles; do \
			echo '-- Installing '$$i ; \
			$(INSTALL_DATA) $$i $(DESTDIR)$(HTML_TARGET_DIR); \
		done; \
	fi

uninstall-local:
	rm -f $(DESTDIR)$(HTML_TARGET_DIR)/*

