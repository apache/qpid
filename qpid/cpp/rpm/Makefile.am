#
# Build RPMs from the distribution tarball.
#

# TODO aconway 2006-12-21: use autoconf macros for version, base name etc.

SPEC=${top_srcdir}/qpidc.spec
RPMOPTS=--define "_sourcedir ${abs_top_srcdir}" --define "_topdir ${abs_builddir}"

clean:
	rm -rf BUILD  RPMS  SOURCES  SPECS  SRPMS

.PHONY: rpm srpm dist

# Build source and binary RPMs.
rpm: dist
	rpmbuild $(RPMOPTS) -ba $(SPEC)

# Source RPM only.
srpm: dist
	rpmbuild $(RPMOPTS) -bs $(SPEC)

# Build source distribution.
dist:
	cd .. && $(MAKE) $(AM_MAKEFLAGS) dist
