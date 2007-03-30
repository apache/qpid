#
# Run doxygen to generate HTML doc.
# Generate dependency files so its rebuilt only when needed. 
#

EXTRA_DIST = html user.doxygen

dist-hook: html

html: doxygen.mk
	make -f doxygen.mk $@

html-dev: doxygen.mk
	make -f doxygen.mk $@

doxygen.mk: doxygen.deps

doxygen.deps: force
	echo "html: \\" >> $@-t
	find $(top_srcdir) -name "*.h" -exec echo "  {} \\" \; >> $@-t
	echo >> $@-t
	echo "html-dev: html\\" >> $@-t
	find $(top_srcdir) -name "*.cpp" -exec echo "  {} \\" \; >> $@-t
	echo >> $@-t
	mv $@-t $@

force:

clean-local:
	rm -rf html html-dev *.deps
