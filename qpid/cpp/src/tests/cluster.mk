
if HAVE_LIBCPG

#
# Cluster tests makefile fragment, to be included in Makefile.am
# 

# NOTE: Programs using the openais library must be run with gid=ais
# You should do "newgrp ais" before running the tests to run these.
# 


# ais_check checks pre-requisites for cluster tests and runs them if ok.
TESTS+=ais_check
EXTRA_DIST+=ais_check start_cluster stop_cluster

check_PROGRAMS+=cluster_test
cluster_test_SOURCES=unit_test.cpp cluster_test.cpp
cluster_test_LDADD=$(lib_client) ../cluster.la -lboost_unit_test_framework

unit_test_LDADD+=../cluster.la

endif
