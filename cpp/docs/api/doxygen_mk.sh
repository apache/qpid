#!/bin/sh
#
# Generate makefile for doxygen with dependencies on source files.
# 

deps() {
    find "$top_srcdir/src" -name "$2" -exec echo -ne '\\\n  {} ' \;
    echo ; echo
}

cat <<EOF
html: user.doxygen
	doxygen $srcdir/user.doxygen
html-dev: developer.doxygen
	doxygen $srcdir/developer.doxygen
EOF

deps "html: " "*.h"
deps "html-dev: html " "*.cpp"
