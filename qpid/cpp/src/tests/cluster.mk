if CPG
#
# Cluster tests makefile fragment, to be included in Makefile.am
# 

lib_cluster = $(abs_builddir)/../libqpidcluster.la

# NOTE: Programs using the openais library must be run with gid=ais
# You should do "newgrp ais" before running the tests to run these.
# 

# ais_check checks conditions for AIS tests and runs if ok.
TESTS+=ais_check
EXTRA_DIST+=ais_check ais_run start_cluster stop_cluster

check_PROGRAMS+=ais_test
ais_test_SOURCES=ais_test.cpp Cpg.cpp 
ais_test_LDADD=$(lib_client) $(lib_cluster) -lboost_unit_test_framework

endif
