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


# --- Function definitions ---

func_check_required_env ()
#-------------------------
# Check that EITHER:
#    QPID_DIR is set (for running against svn QPID)
# OR
#    QPID_PREFIX is set (for running against installed QPID
# Will exit with error code 1 if neither of these is defined.
# Params: None
# Returns: 0 if env vars ok, 1 otherwise   
{
	if test -z "${QPID_DIR}" -a -z "${QPID_PREFIX}"; then
		# Try to find qpidd in the normal installed location
		if test -x /usr/sbin/qpidd; then
			QPID_PREFIX=/usr
		else
			echo "ERROR: Could not find installed Qpid"
			echo "Either of the following must be set in the environment for this script to run:"
			echo "  QPID_DIR for running against a Qpid svn build"
			echo "  QPID_PREFIX for running against an installed Qpid"
			return 1
		fi
	fi
	return 0
}


func_check_clustering ()
#-----------------------
# Check openAIS/corosync is running and user has correct privileges
# Params: None
# Returns: 0 if openAIS/corosync is running, 1 otherwise
# Sets env var COROSYNC to 1 if corosync is running, not set otherwise
{
    # Check either aisexec or corosync is running as root
    cluster_prog=`ps -u root | grep 'aisexec\|corosync'`
    test -n "$cluster_prog" || NODAEMON="Neither aisexec nor corosync is running as root"
    if test -z "$NODAEMON"; then
        # Test for corosync running
        echo $cluster_prog | grep "aisexec" > /dev/null || COROSYNC=1
        if test -n "$COROSYNC"; then
            # Corosync auth test
            user=`whoami`
            ls /etc/corosync/uidgid.d | grep $user > /dev/null || NOAUTH="You are not authorized to use corosync."
        else
            # OpenAis auth test
            id -nG | grep '\<ais\>' >/dev/null || NOAUTH="You are not a member of the ais group."
        fi
    fi
    
    if test -n "$NODAEMON" -o -n "$NOAUTH"; then
        cat <<EOF

    ========== WARNING: NOT RUNNING CLUSTER TESTS ============
	
    Cluster tests will not be run because:
	
    $NODAEMON
    $NOAUTH
	
    ==========================================================
    
EOF
    	return 1
	fi
    CLUSTERING_ENABLED=1
	return 0
}


func_check_qpid_python ()
#------------------------
# Check that Qpid python environment is ok
# Params: None
# Returns: 0 if Python environment is ok; 1 otherwise
{
	if ! python -c "import qpid" ; then
    	cat <<EOF
	
    ===========  WARNING: PYTHON TESTS DISABLED ==============
	
    Unable to load python qpid module - skipping python tests.
	
    PYTHONPATH=${PYTHONPATH}
	
    ===========================================================
	
EOF
    	return 1
	fi
	return 0
}

func_set_python_env()
#--------------------
# Set up the python path
# Params: None
# Returns: Nothing
{
	if test "${QPID_DIR}" -a -d "${QPID_DIR}" ; then
		QPID_PYTHON=${QPID_DIR}/python
		QPID_TOOLS=${QPID_DIR}/tools/src/py
		QMF_LIB=${QPID_DIR}/extras/qmf/src/py
		export PYTHONPATH=${QPID_PYTHON}:${QMF_LIB}:${QPID_TOOLS}:$PYTHONPATH
	fi
}

func_set_env ()
#--------------
# Set up the environment based on value of ${QPID_DIR}: if ${QPID_DIR} exists, assume a svn checkout,
# otherwise set up for an installed or prefix test.
# Params: None
# Returns: Nothing
{
    if test "${QPID_DIR}" -a -d "${QPID_DIR}" ; then
        # QPID_DIR is defined for source tree builds by the --with-qpid-checkout configure option.
        # QPID_BLD is defined as the build directory, either $QPID_DIR/cpp or separately specified with
        # the --with-qpid-build option for VPATH builds.

	    # Check QPID_BLD is also set
	    if test -z ${QPID_BLD}; then
		    QPID_BLD="${QPID_DIR}/cpp"
	    fi
	    source $QPID_BLD/src/tests/env.sh
#	    CPP_CLUSTER_EXEC="${QPID_BLD}/src/tests/cluster_test"
#	    PYTHON_CLUSTER_EXEC="${QPID_DIR}/cpp/src/tests/$PYTHON_TESTNAME"
	    FEDERATION_SYS_TESTS_FAIL="${QPID_DIR}/cpp/src/tests/federation_sys_tests.fail"
        if test -z ${STORE_LIB}; then
            STORE_LIB="../../lib/.libs/msgstore.so"
        fi
#        export STORE_ENABLE=1
    else
        # Set up the environment based on value of ${QPID_PREFIX} for testing against an installed qpid
        # Alternatively, make sure ${QPID_BIN_DIR}, ${QPID_SBIN_DIR}, ${QPID_LIB_DIR} and ${QPID_LIBEXEC_DIR} are set for
        # the installed location.
        if test "${QPID_PREFIX}" -a -d "${QPID_PREFIX}" ; then
            QPID_BIN_DIR=${QPID_PREFIX}/bin
            QPID_SBIN_DIR=${QPID_PREFIX}/sbin
            QPID_LIB_DIR=${QPID_PREFIX}/lib
            QPID_LIBEXEC_DIR=${QPID_PREFIX}/libexec
	    export PATH="$QPID_BIN_DIR:$QPID_SBIN_DIR:$QPID_LIBEXEC_DIR/qpid/tests:$PATH"
        fi
    
	    # These four env vars must be set prior to calling this script
	    func_checkpaths QPID_BIN_DIR QPID_SBIN_DIR QPID_LIB_DIR QPID_LIBEXEC_DIR
	
	    # Paths and dirs
	    export PYTHON_DIR="${QPID_BIN_DIR}"
	    export PYTHONPATH="${QPID_LIB_DIR}/python:${QPID_LIBEXEC_DIR}/qpid/tests:${QPID_LIB_DIR}/python2.4:${QPID_LIB_DIR}/python2.4/site-packages:${PYTHONPATH}"
	    # Libraries
	    export CLUSTER_LIB="${QPID_LIB_DIR}/qpid/daemon/cluster.so"
	    export ACL_LIB="${QPID_LIB_DIR}/qpid/daemon/acl.so"
	    export TEST_STORE_LIB="${QPID_LIB_DIR}/qpid/tests/test_store.so"
	
	    # Executables
#	    CPP_CLUSTER_EXEC="${QPID_LIBEXEC_DIR}/qpid/tests/cluster_test"
#	    PYTHON_CLUSTER_EXEC="${QPID_LIBEXEC_DIR}/qpid/tests/$PYTHON_TESTNAME"
	    export QPIDD_EXEC="${QPID_SBIN_DIR}/qpidd"
	    export QPID_CONFIG_EXEC="${QPID_BIN_DIR}/qpid-config"
	    export QPID_ROUTE_EXEC="${QPID_BIN_DIR}/qpid-route"
	    export QPID_CLUSTER_EXEC="${QPID_BIN_DIR}/qpid-cluster"
#	    export RECEIVER_EXEC="${QPID_LIBEXEC_DIR}/qpid/tests/receiver"
#	    export SENDER_EXEC="${QPID_LIBEXEC_DIR}/qpid/tests/sender"
	    export QPID_PYTHON_TEST="${QPID_BIN_DIR}/qpid-python-test"

	    # Data
	    FEDERATION_SYS_TESTS_FAIL="${QPID_LIBEXEC_DIR}/qpid/tests/federation_sys_tests.fail"
    fi
}


func_mk_data_dir ()
#------------------
# Create a data dir at ${TMP_DATA_DIR} if not present, clear it otherwise.
# Set TMP_DATA_DIR if it is not set.
# Params: None
# Returns: Nothing
{
	if test -z "${TMP_DATA_DIR}"; then
		TMP_DATA_DIR=/tmp/federation_sys_tests
		echo "TMP_DATA_DIR not set; using ${TMP_DATA_DIR}"
	fi
	
   	# Delete old cluster test dirs if they exist
	if test -d "${TMP_DATA_DIR}" ; then
    	rm -rf "${TMP_DATA_DIR}/cluster"
	fi
   	mkdir -p "${TMP_DATA_DIR}/cluster"
	export TMP_DATA_DIR
}


func_checkvar ()
#---------------
# Check that an environment var is set (ie non-zero length)
# Params: $1 - env var to be checked
# Returns: 0 = env var is set (ie non-zero length)
#          1 = env var is not set
{
	local loc_VAR=$1
	if test -z ${!loc_VAR}; then
		echo "WARNING: environment variable ${loc_VAR} not set."
		return 1
	fi
	return 0
}


func_checkpaths ()
#-----------------
# Check a list of paths (each can contain ':'-separated sub-list) is set and valid (ie each path exists as a dir)
# Params: $@ - List of path env vars to be checked
# Returns: Nothing
{
	local loc_PATHS=$@
	for path in ${loc_PATHS}; do
		func_checkvar ${path}
		if test $? == 0; then
			local temp_IFS=${IFS}
			IFS=":"
			local pl=${!path}
			for p in ${pl[@]}; do
				if test ! -d ${p}; then
					echo "WARNING: Directory ${p} in var ${path} not found."
				fi
			done
			IFS=${temp_IFS}
		fi
	done
}


func_checklibs ()
#----------------
# Check that a list of libs is set and valid (ie each lib exists as an executable file)
# Params: $@ - List of lib values to be checked
# Returns: Nothing
{
	local loc_LIBS=$@
	for lib in ${loc_LIBS[@]}; do
		func_checkvar ${lib}
		if test $? == 0; then
			if test ! -x ${!lib}; then
				echo "WARNING: Library ${lib}=${!lib} not found."
			fi
		fi
	done
}


func_checkexecs ()
#-----------------
# Check that a list of executable is set and valid (ie each exec exists as an executable file)
# Params: $@ - List of exec values to be checked
# Returns: Nothing
{
	local loc_EXECS=$@
	for exec in ${loc_EXECS[@]}; do
		func_checkvar ${exec}
		if test $? == 0; then
			if test ! -x ${!exec}; then
				echo "WARNING: Executable ${exec}=${!exec} not found or is not executable."
			fi
		fi
	done
}


#--- Start of script ---

func_set_python_env
func_check_required_env || exit 1   # Cannot run, exit with error
func_check_qpid_python || exit 1    # Cannot run, exit with error
func_check_clustering               # Warning

PYTHON_TESTNAME=federation_sys.py
func_set_env
func_mk_data_dir

# Check expected environment vars are set
func_checkpaths PYTHON_DIR PYTHONPATH TMP_DATA_DIR
func_checklibs CLUSTER_LIB STORE_LIB
func_checkexecs QPIDD_EXEC QPID_CONFIG_EXEC QPID_ROUTE_EXEC QPID_PYTHON_TEST

FAILING_PYTHON_TESTS="${abs_srcdir}/../failing_python_tests.txt"
if test -z $1; then
	FEDERATION_SYS_TEST="${QPID_PYTHON_TEST} -m cluster_tests -I ${FEDERATION_SYS_TESTS_FAIL}"
else
	FEDERATION_SYS_TEST="${QPID_PYTHON_TEST} -m cluster_tests -I ${FEDERATION_SYS_TESTS_FAIL} cluster_tests.LongTests.*"
	LONG_TEST=1
fi

