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

if HAVE_LIBCPG

#
# Cluster tests makefile fragment, to be included in Makefile.am
# 

# NOTE: Programs using the openais library must be run with gid=ais
# You should do "newgrp ais" before running the tests to run these.
# 


# ais_check checks pre-requisites for cluster tests and runs them if ok.
TESTS+=ais_check
EXTRA_DIST+=ais_check start_cluster stop_cluster restart_cluster cluster_python_tests cluster_python_tests_failing.txt

check_PROGRAMS+=cluster_test
cluster_test_SOURCES=unit_test.cpp cluster_test.cpp ClusterFixture.cpp ClusterFixture.h ForkedBroker.h ForkedBroker.cpp
cluster_test_LDADD=$(lib_client) ../cluster.la -lboost_unit_test_framework 

unit_test_LDADD+=../cluster.la

LONG_TESTS+=start_cluster cluster_python_tests stop_cluster
endif
