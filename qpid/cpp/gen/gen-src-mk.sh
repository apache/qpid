#!/bin/sh
# Generates gen-src.mk makefile fragment, to stdout.
# Usage: <gentools_dir> <gentools_srcdir>

gentools_dir=$1
gentools_srcdir=$2

backslashify() {
    for x in $* ; do
	echo " \\"
	echo -n "  $x"
    done
    echo ; echo
}
    

echo -n "generated_cpp = "
backslashify `find * -name '*.cpp' -print`
echo -n  "generated_h = "
backslashify `find * -name '*.h' -print`

echo
echo -n "java_sources ="
backslashify `find $gentools_srcdir -name '*.java' -print`
echo -n "cxx_templates ="
backslashify `find $gentools_dir/templ.cpp -name '*.tmpl'`

cat <<EOF
# Empty rules in case one of these files is removed,
# renamed or no longer generated.
\$(spec):
\$(java_sources):
\$(cxx_templates):
EOF


