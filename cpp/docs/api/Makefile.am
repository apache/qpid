#
# Run doxygen to generate HTML doc.
# Generate dependency files so its rebuilt only when needed. 
#

# TODO aconway 2007-04-12: html should have a
# dependency on source/header files.

if HAVE_DOXYGEN

EXTRA_DIST = html user.doxygen developer.doxygen html.timestamp

html: html.timestamp

html.timestamp:
	doxygen user.doxygen
	touch $@

clean-local:
	rm -rf html html-dev html.timestamp

endif
