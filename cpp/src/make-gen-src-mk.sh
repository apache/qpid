#!/bin/sh
# Generates  the gen-src.mk makefile fragment, to stdout.
# Usage: <gentools_dir> <gentools_srcdir>

gentools_dir=$1
gentools_srcdir=$2

wildcard() { echo `ls $* 2>/dev/null` ; }

cat <<EOF
generated_sources = `wildcard gen/*.cpp`

generated_headers = `wildcard gen/*.h`

if CAN_GENERATE_CODE

java_sources = `wildcard $gentools_srcdir/*.java`

cxx_templates = `wildcard $gentools_dir/templ.cpp/*.tmpl`

# Empty rules in case one of these files is removed,
# renamed or no longer generated.
\$(spec):
\$(java_sources):
\$(cxx_templates):
endif

EOF


