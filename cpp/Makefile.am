AUTOMAKE_OPTIONS = 1.9.2 foreign
ACLOCAL_AMFLAGS = -I m4

EXTRA_DIST = README-dist DESIGN LICENSE.txt NOTICE.txt README.rhel3

SUBDIRS = gen lib tests src docs/api

# Update libtool, if needed.
libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status --recheck

