#
# Run doxygen to generate HTML doc.
# Generate dependency files so its rebuilt only when needed. 
#

EXTRA_DIST = html user.doxygen developer.doxygen

dist-hook: html

src=$(top_srcdir)/src

headers: $(src)/*.h $(src)/*/*.h $(src)/*/*/*.h
sources: $(src)/*.cpp $(src)/*/*.cpp $(src)/*/*/*.cpp

html: headers user.doxygen
	doxygen user.doxygen
	touch $@

html-dev: headers sources developer.doxygen
	doxygen developer.doxygen
	touch $@

clean-local:
	rm -rf html html-dev 
