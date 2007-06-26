if CLUSTER
# Cluster tests makefile fragment, to be included in Makefile.am
# 

lib_cluster = $(abs_builddir)/../libqpidcluster.la

# NOTE: Programs using the openais library must be run with gid=ais
# Such programs are built as *.ais, with a wrapper script *.sh that
# runs the program under sudo -u ais.
# 

# Rule to generate wrappers.
# The chmod is a horrible hack to allow libtools annoying wrapers to
# relink the executable when run as user ais.
.ais.sh:
	echo sudo -u ais env VALGRIND=$(VALGRIND) srcdir=$(srcdir) $(srcdir)/run_test ./$<  >$@; chmod a+x $@
	chmod a+rwx . .libs

# Cluster tests.
# 
check_PROGRAMS+=Cpg.ais
Cpg_ais_SOURCES=Cpg.cpp
Cpg_ais_LDADD=$(lib_cluster) -lboost_unit_test_framework
unit_wrappers+=Cpg.sh

check_PROGRAMS+=Cluster.ais
Cluster_ais_SOURCES=Cluster.cpp Cluster.h
Cluster_ais_LDADD=$(lib_cluster) -lboost_unit_test_framework
unit_wrappers+=Cluster.sh

check_PROGRAMS+=Cluster_child 
Cluster_child_SOURCES=Cluster_child.cpp Cluster.h
Cluster_child_LDADD=$(lib_cluster) -lboost_test_exec_monitor

endif
