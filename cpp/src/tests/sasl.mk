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

# Test that are only relevant if SASL is enabled.
if HAVE_SASL

# Note: sasl_version is not a test -- it is a tool used by tests.
check_PROGRAMS+=sasl_version
sasl_version_SOURCES=sasl_version.cpp
sasl_version_LDADD=$(lib_client)

TESTS += 	sasl_fed
		sasl_fed_ex_dynamic
		sasl_fed_ex_link
		sasl_fed_ex_queue
		sasl_fed_ex_route
		sasl_no_dir

EXTRA_DIST += sasl_fed                        \
              sasl_fed_ex                     \
              sasl_fed_ex_dynamic             \
              sasl_fed_ex_link                \
              sasl_fed_ex_queue               \
              sasl_fed_ex_route               \
              sasl_no_dir


endif # HAVE_SASL
