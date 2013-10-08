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


abs_srcdir=@abs_srcdir@

TMP_DATA_DIR=$(abs_srcdir)/../tmp_data_dir

TESTS = \
  run_federation_sys_tests

LONG_TESTS = \
  run_long_federation_sys_tests

EXTRA_DIST = \
  federation_tests_env.sh \
  run_federation_sys_tests \
  run_long_federation_sys_tests

TESTS_ENVIRONMENT = \
  QPID_DIR=$(QPID_DIR) \
  QPID_BLD=$(QPID_BLD) \
  TMP_DATA_DIR=$(TMP_DATA_DIR) \
  abs_srcdir=$(abs_srcdir)
  
check-long: all
	$(MAKE) check TESTS="$(LONG_TESTS)" SUBDIRS=.

# END
    
