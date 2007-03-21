#
# Build RPMs from the distribution tarball.
#

# TODO aconway 2006-12-21: use autoconf macros for version, base name etc.

SPEC=${top_srcdir}/qpidc.spec
RPMOPTS=--define "_sourcedir ${abs_top_srcdir}" --define "_topdir ${abs_builddir}"

clean-local:
	-rm -rf BUILD  RPMS  SOURCES  SPECS  SRPMS

.PHONY: rpm srpm dist

# Build source and binary RPMs.
rpm: dist
	rpmbuild $(RPMOPTS) $(RPMEXTRAOPTS) -ba $(SPEC)
	rpmlint RPMS/*/*.rpm	

# Build source RPM only.
srpm: dist
	rpmbuild $(RPMOPTS) -bs $(SPEC)

# Build source distribution and create required subdirs.
dist:
	cd .. && $(MAKE) $(AM_MAKEFLAGS) dist
	mkdir -p BUILD  RPMS  SOURCES  SPECS  SRPMS
