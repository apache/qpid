#!/usr/bin/env bash

STORE_DIR=/tmp
LINEARSTOREDIR=~/RedHat/linearstore

rm -rf $STORE_DIR/qls
rm -rf $STORE_DIR/p002
rm $STORE_DIR/p004

mkdir $STORE_DIR/qls
mkdir $STORE_DIR/p002
touch $STORE_DIR/p004
mkdir $STORE_DIR/qls/p001
touch $STORE_DIR/qls/p003
ln -s $STORE_DIR/p002 $STORE_DIR/qls/p002
ln -s $STORE_DIR/p004 $STORE_DIR/qls/p004

${LINEARSTOREDIR}/tools/src/py/linearstore/efptool.py $STORE_DIR/qls/ -a -p 1 -s 2048 -n 25
${LINEARSTOREDIR}/tools/src/py/linearstore/efptool.py $STORE_DIR/qls/ -a -p 1 -s 512 -n 25
${LINEARSTOREDIR}/tools/src/py/linearstore/efptool.py $STORE_DIR/qls/ -a -p 2 -s 2048 -n 25

${LINEARSTOREDIR}/tools/src/py/linearstore/efptool.py $STORE_DIR/qls/ -l
tree -la $STORE_DIR/qls

