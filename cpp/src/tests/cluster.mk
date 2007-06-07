#-*-Makefile-*-
# Cluster tests makefile fragment, to be included in Makefile.am
# 

lib_cluster = $(abs_builddir)/../libqpidcluster.la
#
#  AIS_UNIT_TESTS must be called with gid=ais. They are run
#  separately under sudo -u ais.
#
AIS_UNIT_TESTS=

AIS_UNIT_TESTS+=Cpg
Cpg_SOURCES=unit/Cpg.cpp 
Cpg_LDADD=-lboost_unit_test_framework $(lib_cluster) 

RUN_AIS_TESTS=ais_unit_tests	# Run ais unit tests via check-ais.

# The chmod is a horrible hack to allow libtools annoying wrapers to
# relink the executable when run as user ais.
check-ais: $(AIS_UNIT_TESTS)
	chmod a+rwx . .libs
	sudo -u ais $(MAKE) check TESTS=$(AIS_UNIT_TESTS)

