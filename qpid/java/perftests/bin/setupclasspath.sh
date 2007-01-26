if [ -z $QPID_HOME ] ; then
    echo "QPID_HOME must be set"
    exit
fi

CP=../lib/qpid-performance.jar:$QPID_HOME/lib/qpid-incubating.jar

if [ `uname -o` == "Cygwin" ] ; then
     CP=`cygpath --path --windows $CP`
fi


