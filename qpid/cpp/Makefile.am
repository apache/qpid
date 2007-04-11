AUTOMAKE_OPTIONS = 1.9.2 foreign
ACLOCAL_AMFLAGS = -I m4

SPEC=$(PACKAGE).spec

EXTRA_DIST = \
  LICENSE NOTICE README \
  etc/qpidd \
  $(SPEC) $(SPEC).in \
  rpm/README.qpidd-devel 

SUBDIRS = src docs/api docs/man

# Update libtool, if needed.
libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status --recheck

# This cannot be done by AC_CONFIG_FILES, because automake's
# make distcheck target does not like auto-generated files
# being included in the distributed archive.
$(SPEC): %: %.in Makefile
	sed 's/@''PACKAGE@/@PACKAGE@/;s/@''VERSION@/@VERSION@/' $< > $@-tmp
	mv $@-tmp $@
MAINTAINERCLEANFILES = $(SPEC)

#
# Build RPMs from the distribution tarball.
#
RPMDIRS=rpm/BUILD  rpm/RPMS rpm/SPECS rpm/SRPMS
RPMMACROS=--define "_topdir @abs_builddir@/rpm" --define "_sourcedir @abs_builddir@"
# Override this variable e.g. with -bs to produce srpm only
RPMOPTS=-ba

clean-local:
	-rm -rf $(RPMDIRS)

.PHONY: rpmbuild

rpmbuild: $(SPEC) dist-gzip
	mkdir -p $(RPMDIRS)
	rpmbuild $(RPMMACROS) $(RPMOPTS) $(SPEC)
if HAS_RPMLINT
	rpmlint `find rpm -name '*.rpm'`
else
	@echo "WARNING: rpmlint not found, could not validate RPMs."
endif


