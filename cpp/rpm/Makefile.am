#
# Build RPMs from the distribution tarball.
#

abs_top_srcdir=@abs_top_srcdir@
abs_builddir=@abs_builddir@

SPEC=${top_srcdir}/qpidc.spec
RPMOPTS=--define "_sourcedir ${abs_top_srcdir}" --define "_topdir ${abs_builddir}" --define "dist .M2"

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
