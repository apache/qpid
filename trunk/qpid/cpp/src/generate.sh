# !/bin/sh
# Generate code from AMQP specification.
# specs and gentools_dir are set by Makefile
# 
set -e

test -z "$JAVA" && JAVA=java ; 
test -z "$JAVAC" && JAVAC=javac ;

srcdir=`dirname $0`
checkspecs() {
    for s in $specs; do test -f $s || return 1; done
    return 0
}

# Can we generate code?
if { test -d $gentools_dir && checkspecs &&
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
mv generate.mk-t $srcdir/generate.mk



