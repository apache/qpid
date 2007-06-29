if CLUSTER
# Cluster tests makefile fragment, to be included in Makefile.am
# 

lib_cluster = $(abs_builddir)/../libqpidcluster.la

# NOTE: Programs using the openais library must be run with gid=ais
# Such programs are built as *.ais, with a wrapper script *.sh that
# runs the program with newgrp ais.
# 

# Rule to generate wrapper scripts for tests that require gid=ais.
run_test="env VALGRIND=$(VALGRIND) srcdir=$(srcdir) $(srcdir)/run_test"
.ais.sh:
	echo "if groups | grep '\bais\b' >/dev/null;" > $@_t
	echo "then echo $(run_test) ./$< \"$$@	\"| newgrp ais;" >>$@_t
	echo "else echo WARNING: `whoami` not in group ais, skipping $<.;" >>$@_t
	echo "fi"  >> $@_t
	mv $@_t $@
	chmod a+x $@

#
# Cluster tests.
# 
check_PROGRAMS+=Cpg.ais
Cpg_ais_SOURCES=Cpg.cpp
Cpg_ais_LDADD=$(lib_cluster) -lboost_unit_test_framework
unit_wrappers+=Cpg.sh

# FIXME aconway 2007-06-29: Fixing problems with the test.
# check_PROGRAMS+=Cluster.ais
# Cluster_ais_SOURCES=Cluster.cpp Cluster.h
# Cluster_ais_LDADD=$(lib_cluster) -lboost_unit_test_framework
# unit_wrappers+=Cluster.sh

check_PROGRAMS+=Cluster_child 
Cluster_child_SOURCES=Cluster_child.cpp Cluster.h
Cluster_child_LDADD=$(lib_cluster) -lboost_test_exec_monitor

endif
