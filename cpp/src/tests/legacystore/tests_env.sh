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

	    # Paths and dirs
        #if test -z ${abs_srcdir}; then
	    #    abs_srcdir=`pwd`
        #fi
	    source $QPID_BLD/src/tests/test_env.sh
        # Override these two settings from test_env.sh:
        export RECEIVER_EXEC=$QPID_TEST_EXEC_DIR/qpid-receive
        export SENDER_EXEC=$QPID_TEST_EXEC_DIR/qpid-send

        echo "abs_srcdir=$abs_srcdir"
        export STORE_LIB="`pwd`/../lib/.libs/msgstore.so"
        export STORE_ENABLE=1
        export CLUSTER_LIB="${QPID_BLD}/src/.libs/cluster.so"

	    PYTHON_DIR="${QPID_DIR}/python"
	    export PYTHONPATH="${PYTHONPATH}":"${PYTHON_DIR}":"${QPID_DIR}/extras/qmf/src/py":"${QPID_DIR}/tools/src/py":"${QPID_DIR}/cpp/src/tests":"${abs_srcdir}"

	    # Libraries

	    # Executables
	    export QPIDD_EXEC="${QPID_BLD}/src/qpidd"

	    # Test data

    else
        # Set up the environment based on value of ${QPID_PREFIX} for testing against an installed qpid
        # Alternatively, make sure ${QPID_BIN_DIR}, ${QPID_SBIN_DIR}, ${QPID_LIB_DIR} and ${QPID_LIBEXEC_DIR} are set for
        # the installed location.
        if test "${QPID_PREFIX}" -a -d "${QPID_PREFIX}" ; then
            QPID_BIN_DIR=${QPID_PREFIX}/bin
            QPID_SBIN_DIR=${QPID_PREFIX}/sbin
            QPID_LIB_DIR=${QPID_PREFIX}/lib
            QPID_LIBEXEC_DIR=${QPID_PREFIX}/libexec
        fi

	    # These four env vars must be set prior to calling this script
	    func_checkpaths QPID_BIN_DIR QPID_SBIN_DIR QPID_LIB_DIR QPID_LIBEXEC_DIR

	    # Paths and dirs
	    export PYTHON_DIR="${QPID_BIN_DIR}"
	    export PYTHONPATH="${PYTHONPATH}":"${QPID_LIB_DIR}/python":"${QPID_LIBEXEC_DIR}/qpid/tests":"${QPID_LIB_DIR}/python2.4"


	    # Libraries

	    # Executables
	    export QPIDD_EXEC="${QPID_SBIN_DIR}/qpidd"

	    # Test Data

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
		TMP_DATA_DIR=/tmp/python_tests
		echo "TMP_DATA_DIR not set; using ${TMP_DATA_DIR}"
	fi

   	# Delete old test dirs if they exist
	if test -d "${TMP_DATA_DIR}" ; then
    	rm -rf "${TMP_DATA_DIR}/*"
	fi
   	mkdir -p "${TMP_DATA_DIR}"
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

func_check_required_env || exit 1   # Cannot run, exit with error

srcdir=`dirname $0`
if test -z ${abs_srcdir}; then
	abs_srcdir=${srcdir}
fi

func_set_env
func_check_qpid_python || exit 0    # A warning, not a failure.
func_mk_data_dir

# Check expected environment vars are set
func_checkpaths PYTHON_DIR PYTHONPATH TMP_DATA_DIR
func_checklibs STORE_LIB CLUSTER_LIB
func_checkexecs QPIDD_EXEC QPID_CONFIG_EXEC QPID_ROUTE_EXEC SENDER_EXEC RECEIVER_EXEC

FAILING_PYTHON_TESTS="${abs_srcdir}/failing_python_tests.txt"

