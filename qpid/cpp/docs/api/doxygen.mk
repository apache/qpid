include doxygen.deps

html: user.doxygen 
	doxygen user.doxygen
	touch $@

html-dev: developer.doxygen
	doxygen developer.doxygen
	touch $@
