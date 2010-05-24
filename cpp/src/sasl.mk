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
SASLTEST_DIR = tests/sasl_config
SASLTEST_CONF = $(SASLTEST_DIR)/qpidd.conf
SASLTEST_DB = $(SASLTEST_DIR)/qpidd.sasldb

$(SASLTEST_DB):
	echo zig | $(SASL_PASSWD) -c -p -f $(SASLTEST_DB) -u QPID zig
	echo zag | $(SASL_PASSWD) -c -p -f $(SASLTEST_DB) -u QPID zag

sasltestdbdir = $(SASLTEST_DIR)
sasltestdb_DATA = $(SASLTEST_DB)

CLEANFILES=$(SASLTEST_DB)

