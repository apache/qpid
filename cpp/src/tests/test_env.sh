# Set up env var required by various tests.
# If run without args, assume the current directory
# 
# source test_env.sh checkout|install dir1 [ dir2 ]
#  checkout: dir1 is qpid dir of svn checkout, optional dir2 is cpp build directory
#  install: dir1 is the install prefix

usage() { echo "Usage: $0 checkout|install dir1 [ dir2 ]"; return 1; }
absdir() { echo `cd $1 && pwd`; }

qpid_checkout_env() {
    QPID_ROOT=$(absdir $1)
    if [ -n $2 ]; then QPID_BUILD=$(absdir $2); else QPID_BUILD=$QPID_ROOT/cpp; fi

    export QPID_PYTHON_COMMANDS=$QPID_BUILD/src/tests/python/commands
    export PYTHONPATH=$QPID_BUILD/src/tests/python:$QPID_PYTHON_COMMANDS:$PYTHONPATH
    export QPIDD_EXEC=$QPID_BUILD/src/qpidd
    export QPID_TEST_EXEC_DIR=$QPID_BUILD/src/tests
    export QPID_MODULE_DIR=$QPID_BUILD/src/.libs/
}

qpid_install_env() {
    QPID_PREFIX=$(absdir $1)

    export QPID_PYTHON_COMMANDS=$QPID_PREFIX/bin
    export PYTHONPATH=$QPID_PREFIX/python:$QPID_PYTHON_COMMANDS:$PYTHONPATH
    export QPIDD_EXEC=$QPID_PREFIX/sbin/qpidd
    export QPID_TEST_EXEC_DIR=$QPID_PREFIX/libexec/qpid/tests
    if [ test -d $QPID_PREFIX/lib64/qpid ]; then export QPID_MODULE_DIR=$PREFIX/lib64/qpid;
    elif [ test -d $QPID_PREFIX/lib/qpid ]; then export QPID_MODULE_DIR=$PREFIX/lib/qpid;
    else echo "Can't find module directory $QPID_PREFIX/lib[64]/qpid";
    fi
}

test $# -ge 2 || { usage; return 1; }

case $1 in
    checkout) qpid_checkout_env $2 $3 ;;
    install) qpid_install_env $2 ;;
    *) usage; return 1 ;;
esac

export QPID_CONFIG_EXEC=$QPID_PYTHON_COMMANDS/qpid-config
export QPID_ROUTE_EXEC=$QPID_PYTHON_COMMANDS/qpid-route
export QPID_CLUSTER_EXEC=$QPID_PYTHON_COMMANDS/qpid-cluster

export RECEIVER_EXEC=$QPID_TEST_EXEC_DIR/receiver
export SENDER_EXEC=$QPID_TEST_EXEC_DIR/sender

test -f $QPID_MODULE_DIR/cluster.so && export CLUSTER_LIB=$QPID_MODULE_DIR/cluster.so
test -f $QPID_MODULE_DIR/xml.so && export XML_LIB=$QPID_LIB_DIR/xml.so
