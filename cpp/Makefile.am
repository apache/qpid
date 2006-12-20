AUTOMAKE_OPTIONS = 1.9.2 foreign
ACLOCAL_AMFLAGS = -I m4

EXTRA_DIST = README-dev DESIGN LICENSE.txt NOTICE.txt README.rhel3 README.txt etc/qpidd

SUBDIRS = gen lib tests src docs/api docs/man

# Update libtool, if needed.
libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status --recheck

# Convenience targets for making RPMs.
RPM_TAR=/usr/src/redhat/SOURCES/$(distdir).tar.gz

$(RPM_TAR): dist-gzip
	cp $(distdir).tar.gz  $(RPM_TAR)
	rpmbuild -bs -v qpidc.spec

srpm: $(RPM_TAR)
	rpmbuild -bs -v qpidc.spec

rpm: $(RPM_TAR)
	rpmbuild -ba -v qpidc.spec
