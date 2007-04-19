#!/bin/bash

if [ -z QPID_HOME ] ; then

echo "QPID_HOME must be set"
exit 0
fi

configs=`pwd`

pushd $QPID_HOME/bin/

echo "Starting qpid server - device config"
./qpid-server -c $configs/bdb-qpid-4/device.xml

echo "Starting qpid server - filepath  config"
./qpid-server -c $configs/bdb-qpid-4/filepath.xml

echo "Starting qpid server - none existent path config"
./qpid-server -c $configs/bdb-qpid-4/noneexistantpath.xml

echo "Starting qpid server - no permission config"
./qpid-server -c $configs/bdb-qpid-4/nopermission.xml

echo "Starting qpid server - Star in path config"
./qpid-server -c $configs/bdb-qpid-4/starpath.xml

popd
