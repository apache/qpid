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
	doxygen $(srcdir)/user.doxygen

html-dev: headers sources developer.doxygen
	doxygen $(srcdir)/developer.doxygen

clean-local:
	rm -rf html html-dev 
