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

# This script sets up a test directory which contains both
# recoverable and non-recoverable files and directories for
# the empty file pool (EFP).

# NOTE: The following is based on typical development tree paths, not installed paths

BASE_DIR=${HOME}/RedHat
STORE_DIR=${BASE_DIR}
PYTHON_TOOLS_DIR=${BASE_DIR}/qpid/tools/src/linearstore
export PYTHONPATH=${BASE_DIR}/qpid/python:${BASE_DIR}/qpid/extras/qmf/src/py:${BASE_DIR}/qpid/tools/src/py

# Remove old dirs (if present)
rm -rf ${STORE_DIR}/qls
rm -rf ${STORE_DIR}/p002
rm ${STORE_DIR}/p004

# Create new dir tree and links
mkdir ${STORE_DIR}/p002_ext
touch ${STORE_DIR}/p004_ext
mkdir ${STORE_DIR}/qls
mkdir ${STORE_DIR}/qls/p001
touch ${STORE_DIR}/qls/p003
ln -s ${STORE_DIR}/p002_ext ${STORE_DIR}/qls/p002
ln -s ${STORE_DIR}/p004_ext ${STORE_DIR}/qls/p004

# Populate efp dirs with empty files
${PYTHON_TOOLS_DIR}/efptool.py $STORE_DIR/qls/ -a -p 1 -s 2048 -n 25
${PYTHON_TOOLS_DIR}/efptool.py $STORE_DIR/qls/ -a -p 1 -s 512 -n 25
${PYTHON_TOOLS_DIR}/efptool.py $STORE_DIR/qls/ -a -p 2 -s 2048 -n 25

# Show the result for information
${LINEARSTOREDIR}/tools/src/py/linearstore/efptool.py $STORE_DIR/qls/ -l
tree -la $STORE_DIR/qls

