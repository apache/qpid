AUTOMAKE_OPTIONS = 1.9.2 foreign
ACLOCAL_AMFLAGS = -I m4

SPEC=$(PACKAGE).spec

EXTRA_DIST = \
  LICENSE NOTICE README RELEASE_NOTES DESIGN\
  $(SPEC) $(SPEC).in \
  rpm/README.qpidd-devel \
  xml/cluster.xml

SUBDIRS = managementgen etc src docs/api docs/man examples

# Update libtool, if needed.
libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status --recheck

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
	QPID_PYTHON_DIR=@abs_top_srcdir@/../python rpmbuild $(RPMMACROS) $(RPMOPTS) $(SPEC)
if HAS_RPMLINT
	rpmlint `find rpm -name '*.rpm'`
else
	@echo "WARNING: rpmlint not found, could not validate RPMs."
endif
