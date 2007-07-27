if CLUSTER
# Cluster tests makefile fragment, to be included in Makefile.am
# 

lib_cluster = $(abs_builddir)/../libqpidcluster.la

# NOTE: Programs using the openais library must be run with gid=ais
# You should do "newgrp ais" before running the tests to run these.
# 

#
# Cluster tests.
# 

# ais_check runs ais if the conditions to run AIS tests
# are met, otherwise it prints a warning.
TESTS+=ais_check
EXTRA_DIST+=ais_check
AIS_TESTS=

ais_check: ais_tests
ais_tests:
	echo $(AIS_TESTS) >$@

AIS_TESTS+=Cpg
check_PROGRAMS+=Cpg
Cpg_SOURCES=Cpg.cpp
Cpg_LDADD=$(lib_cluster) -lboost_unit_test_framework

# TODO aconway 2007-07-26: Fix this test.
#AIS_TESTS+=Cluster
check_PROGRAMS+=Cluster
Cluster_SOURCES=Cluster.cpp Cluster.h
Cluster_LDADD=$(lib_cluster) -lboost_unit_test_framework

check_PROGRAMS+=Cluster_child 
Cluster_child_SOURCES=Cluster_child.cpp Cluster.h
Cluster_child_LDADD=$(lib_cluster) -lboost_test_exec_monitor

# TODO aconway 2007-07-03: In progress
#AIS_TESTS+=cluster_client
check_PROGRAMS+=cluster_client
cluster_client_SOURCES=cluster_client.cpp
cluster_client_LDADD=$(lib_client) -lboost_unit_test_framework

endif
