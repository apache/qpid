AUTOMAKE_OPTIONS = 1.9.2 foreign
ACLOCAL_AMFLAGS = -I m4

EXTRA_DIST = README-dev DESIGN LICENSE.txt NOTICE.txt README.rhel3 README.txt etc/qpidd

SUBDIRS = gen lib tests src docs/api docs/man rpm

# Update libtool, if needed.
libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status --recheck

.PHONY: rpm srpm

rpm srpm:
	cd rpm && $(MAKE) $(MAKEFLAGS_AM) $@
