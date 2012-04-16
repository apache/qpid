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


# Include cluster scripts and extra files in distribution even if
# we're not configured for cluster.

# Useful scripts for doing cluster testing.
CLUSTER_TEST_SCRIPTS_LIST=			\
	allhosts rsynchosts			\
	qpid-build-rinstall qpid-src-rinstall	\
	qpid-test-cluster

EXTRA_DIST +=					\
	$(CLUSTER_TEST_SCRIPTS_LIST)		\
	ais_check				\
	run_cluster_test			\
	cluster_read_credit			\
	test_watchdog				\
	start_cluster				\
	stop_cluster				\
	restart_cluster				\
	cluster_python_tests			\
	cluster_python_tests_failing.txt	\
	federated_cluster_test			\
	clustered_replication_test		\
	run_cluster_tests			\
	run_long_cluster_tests			\
	testlib.py				\
	brokertest.py				\
	cluster_tests.py			\
	cluster_test_logs.py			\
	long_cluster_tests.py			\
	cluster_tests.fail


if HAVE_LIBCPG

#
# Cluster tests makefile fragment, to be included in Makefile.am
# 

# NOTE: Programs using the openais library must be run with gid=ais
# You should do "newgrp ais" before running the tests to run these.
# 


# ais_check checks pre-requisites for cluster tests and runs them if ok.
TESTS +=					\
	run_cluster_test			\
	cluster_read_credit			\
	test_watchdog				\
	run_cluster_tests			\
	federated_cluster_test			\
	clustered_replication_test

# Clean up after cluster_test and start_cluster
CLEANFILES += cluster_test.acl cluster.ports

LONG_TESTS +=					\
	run_long_cluster_tests                  \
	start_cluster				\
	cluster_python_tests			\
	stop_cluster

qpidexectest_PROGRAMS += cluster_test

cluster_test_SOURCES =				\
    cluster_test.cpp			\
    unit_test.cpp				\
    ClusterFixture.cpp			\
    ClusterFixture.h			\
    ForkedBroker.h				\
    ForkedBroker.cpp			\
    PartialFailure.cpp			\
    ClusterFailover.cpp         \
    InitialStatusMap.cpp

# Moved this file here from cluster_test_SOURCES as it breaks the autotools build, but not the cmake
# build and so we need to make sure it is present in the tarball
EXTRA_DIST += StoreStatus.cpp

cluster_test_LDADD=$(lib_client) $(lib_broker) ../cluster.la -lboost_unit_test_framework

qpidexectest_SCRIPTS += run_cluster_tests brokertest.py cluster_tests.py cluster_test_logs.py run_long_cluster_tests long_cluster_tests.py testlib.py cluster_tests.fail
qpidexectest_SCRIPTS += $(CLUSTER_TEST_SCRIPTS_LIST)

endif
