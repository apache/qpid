dist_man_MANS = qpidd.1

man_aux = $(dist_man_MANS:.1=.x) 
EXTRA_DIST = $(man_aux) 
DISTCLEANFILES = $(dist_man_MANS)
CLEANFILES=qpidd.1

dist-hook: $(man_aux)

# Depend on configure.ac to get version number changes.
$(dist_man_MANS): $(top_srcdir)/configure.ac

if HAVE_HELP2MAN

SUFFIXES = .x .1
qpidd.1: $(srcdir)/qpidd.x $(top_builddir)/src/qpidd
.x.1:
	@rm -f $@
	@echo "Updating man page $@"
	$(HELP2MAN) --no-info --include=$(srcdir)/$*.x --output=$@-t ../../src/$*
	@chmod a-w $@-t
	@mv $@-t $@
else
qpidd.1:
	@echo "Warning: help2man not available, man page lacks options."
	@cp $(srcdir)/qpidd.x $@
endif


