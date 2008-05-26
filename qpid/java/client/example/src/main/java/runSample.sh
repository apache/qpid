#!/bin/bash

# Work out the CLASSPATH divider
UNAME=`uname -s`
case $UNAME in
    CYGWIN*)
	DIVIDER=";"
    ;;
    *)
	DIVIDER=":"
;;
esac

if test "'x$QPID_HOME'" != "'x'"
then
    QPID_HOME=$QPID_HOME
else
    QPID_HOME="/usr/share/java/"
fi
echo "Using QPID_HOME: $QPID_HOME"

if test "'x$QPID_SAMPLE'" != "'x'"
then
    QPID_SAMPLE=$QPID_SAMPLE
else
    QPID_SAMPLE="/usr/share/doc/rhm-0.2"
fi
echo "Using QPID_SAMPLE: $QPID_SAMPLE"


# set the CLASSPATH
CLASSPATH=`find "$QPID_HOME" -name '*.jar' | tr '\n' "$DIVIDER"`


# compile the samples
javac -cp  "$CLASSPATH" -sourcepath "$QPID_SAMPLE" -d . `find $QPID_SAMPLE -name '*.java'`

# Add output classes to CLASSPATH
CLASSPATH="$CLASSPATH$DIVIDER$."

# Set VM parameters
QPID_PARAM="$QPID_PARAM -Dlog4j.configuration=file://$PWD/log4j.xml"


# Check if the user supplied a sample classname
if test "'x$1'" = "'x'"
then
    echo "No sample classname specified"
    exit;
else
    java -cp $CLASSPATH $QPID_PARAM $*
fi
