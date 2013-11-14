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

# This script cleans up any previous database and journal files, and should
# be run prior to the store system tests, as these are prone to crashing or
# hanging under some circumstances if the database is old or inconsistent.

if [ -d ${TMP_DATA_DIR} ]; then
    rm -rf ${TMP_DATA_DIR}
fi
if [ -d ${TMP_PYTHON_TEST_DIR} ]; then
    rm -rf ${TMP_PYTHON_TEST_DIR}
fi
rm -f ${abs_srcdir}/*.vglog*
