# !/bin/sh
# Generate code from AMQP specification.
# srcdir must 
# 
srcdir=`dirname $0`
set -e

gentools_dir="$srcdir/../gentools"
specs_dir="$srcdir/../../specs"
specs="$specs_dir/amqp.0-9.xml $specs_dir/amqp-errata.0-9.xml  $specs_dir/amqp-dtx-preview.0-9.xml"
test -z "$JAVA" && JAVA=java ; 
test -z "$JAVAC" && JAVAC=javac ; 

# Can we generate code?
if { test -d $gentools_dir && test -d $specs_dir && \
    which $JAVA && which $JAVAC; } > /dev/null;
then
    echo "Generating code."
    mkdir -p gen/qpid/framing
    ( cd $gentools_dir/src && $JAVAC `find -name '*.java' -print` ; ) 
    $JAVA -cp $gentools_dir/src org.apache.qpid.gentools.Main \
	-c -o gen/qpid/framing -t $gentools_dir/templ.cpp $specs 
    GENERATED=yes
fi

# Print a Makefile variable assignment.
make_assign() {
    echo -n "$1 = "; shift
    prefix=$1; shift
    for f in $*; do echo "\\" ; echo -n "  $prefix$f "; done
    echo
}

# Generate a Makefile fragment
(
    make_assign "generated_cpp" "" `find gen -name '*.cpp' -print`
    make_assign "generated_h" "" `find gen -name '*.h' -print`
    if test x$GENERATED = xyes; then
	make_assign "generator" "" $specs \
	    `find ../gentools \( -name '*.java' -o -name '*.tmpl' \) -print`
    fi
) > generate.mk-t
mv generate.mk-t generate.mk



