if [ -z $QPID_HOME ] ; then
    echo "QPID_HOME must be set"
    exit
fi
CP=$QPID_HOME/lib/qpid-incubating.jar:../target/classes

if [ `uname -o` == "Cygwin" ] ; then
     CP=`cygpath --path --windows $CP`
fi
