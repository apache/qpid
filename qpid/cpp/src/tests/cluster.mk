if CPG
#
# Cluster tests makefile fragment, to be included in Makefile.am
# 

lib_cluster = $(abs_builddir)/../libqpidcluster.la

# NOTE: Programs using the openais library must be run with gid=ais
# You should do "newgrp ais" before running the tests to run these.
# 


# FIXME aconway 2008-07-04: disabled till process leak is plugged.
# ais_check checks conditions for cluster tests and run them if ok.
#TESTS+=ais_check
EXTRA_DIST+=ais_check

check_PROGRAMS+=cluster_test
cluster_test_SOURCES=unit_test.cpp cluster_test.cpp
cluster_test_LDADD=$(lib_client) $(lib_cluster) -lboost_unit_test_framework

endif
