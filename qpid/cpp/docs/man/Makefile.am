if HAVE_HELP2MAN

dist_man_MANS = qpidd.1

man_aux = $(dist_man_MANS:.1=.x) 
EXTRA_DIST = $(man_aux) 
DISTCLEANFILES = $(dist_man_MANS)

dist-hook: $(man_aux)

qpidd.1: $(srcdir)/qpidd.x $(top_builddir)/src/qpidd

# Depend on configure.ac to get version number changes.
$(dist_man_MANS): $(top_srcdir)/configure.ac

SUFFIXES = .x .1
.x.1:
	@rm -f $@
	@echo "Updating man page $@"
	$(HELP2MAN) --no-info --include=$(srcdir)/$*.x --output=$@-t ../../src/$*
	@chmod a-w $@-t
	@mv $@-t $@

CLEANFILES=qpidd.1

endif
