AUTOMAKE_OPTIONS = 1.9.2 foreign
ACLOCAL_AMFLAGS = -I m4

EXTRA_DIST = README.txt README-dev DESIGN LICENSE.txt NOTICE.txt README.rhel3
dist_doc_DATA = README.txt

dist_sysconf_DATA = etc/init.d/qpidd

SUBDIRS = gen lib tests src docs/api docs/man

# Update libtool, if needed.
libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status --recheck

