#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#


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

