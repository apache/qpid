#
# Build RPMs from the distribution tarball.
#

SPEC=${top_srcdir}/qpidc.spec
RPMOPTS=--define "_sourcedir ${abs_top_srcdir}" --define "_topdir ${abs_builddir}"
DIRS="BUILD  RPMS  SOURCES  SPECS  SRPMS"

clean-local:
	-rm -rf $(DIRS)

.PHONY: rpm srpm

# Build source and binary RPMs.
rpm: $(DIRS)
	rpmbuild $(RPMOPTS) $(RPMEXTRAOPTS) -ba $(SPEC)
	rpmlint RPMS/*/*.rpm	

# Build source RPM only.
srpm: $(DIRS)
	rpmbuild $(RPMOPTS) -bs $(SPEC)

$(DIRS):
	mkdir -p $(DIRS)


