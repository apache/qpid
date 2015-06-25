#! /bin/bash

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

# tx-test-soak
#
# Basic test methodology:
# 1. Start broker
# 2. Run qpid-txtest against broker using randomly generated parameters
# 3. After some time, kill the broker using SIGKILL
# 4. Restart broker, recover messages
# 5. Run qpid-txtest against broker in check mode, which checks that all expected messages are present.
# 6. Wash, rinse, repeat... The number of runs is determined by ${NUM_RUNS}

# NOTE: The following is based on typical development tree paths, not installed paths

NUM_RUNS=1000
BASE_DIR=${HOME}/RedHat
CMAKE_BUILD_DIR=${BASE_DIR}/q.cm

# Infrequently adjusted
RESULT_BASE_DIR_PREFIX=${BASE_DIR}/results.tx-test-soak
RECOVER_TIME_PER_QUEUE=1
STORE_MODULE="linearstore.so"
BROKER_LOG_LEVEL="info+"
BROKER_MANAGEMENT="no" # "no" or "yes"
TRUNCATE_INTERVAL=10
MAX_DISK_PERC_USED=90

# Constants (don't adjust these)
export BASE_DIR
RELATIVE_BASE_DIR=`python -c "import os,os.path; print os.path.relpath(os.environ['BASE_DIR'], os.environ['PWD'])"`
export PYTHONPATH=${BASE_DIR}/qpid/python:${BASE_DIR}/qpid/extras/qmf/src/py:${BASE_DIR}/qpid/tools/src/py
LOG_FILE_NAME=log.txt
QPIDD_FN=qpidd
QPIDD=${CMAKE_BUILD_DIR}/src/${QPIDD_FN}
TXTEST_FN=qpid-txtest
TXTEST=${CMAKE_BUILD_DIR}/src/tests/${TXTEST_FN}
ANALYZE_FN=qpid_qls_analyze.py
ANALYZE=${BASE_DIR}/qpid/tools/src/py/${ANALYZE_FN}
ANALYZE_ARGS="--efp --show-recs --stats"
QPIDD_BASE_ARGS="--load-module ${STORE_MODULE} -m ${BROKER_MANAGEMENT} --auth no --default-flow-stop-threshold 0 --default-flow-resume-threshold 0 --default-queue-limit 0 --store-dir ${BASE_DIR} --log-enable ${BROKER_LOG_LEVEL} --log-to-stderr no --log-to-stdout no"
TXTEST_INIT_STR="--init yes --transfer no --check no"
TXTEST_RUN_STR="--init no --transfer yes --check no"
TXTEST_CHK_STR="--init no --transfer no --check yes"
SUCCESS_MSG="All expected messages were retrieved."
TIMESTAMP_FORMAT="+%Y-%m-%d_%H:%M:%S"
ANSI_RED="\e[1;31m"
ANSI_NONE="\e[0m"
DEFAULT_EFP_DIR=2048k
DEFAULT_EFP_SIZE=2101248
SIG_KILL=-9
SIG_TERM=-15

# Creates a random number into the variable named in string $1 in the range [$2..$3] (both inclusive).
# $1: variable name as string to which random value is assigned
# $2: minimum inclusive range of random number
# $3: maximum inclusive range of random number
get_random() {
	eval $1=`python -S -c "import random; print random.randint($2,$3)"`
}

# Uses anon-uniform distribution to set a random message size.
# Most messages must be small (0 - 1k), but we need a few medium (10k) and large (100k) ones also.
# Sets message size into var ${MSG_SIZE}
set_message_size() {
	local key=0
	get_random "key" 1 10
	if (( "${key}" == "10" )); then  # 1 out of 10 - very large
		get_random "MSG_SIZE" 100000 1000000
		FILE_SIZE_MULTIPLIER=3
	elif (( "${key}" >= "8" )); then # 2 out of 10 - large
		get_random "MSG_SIZE" 10000 100000
		FILE_SIZE_MULTIPLIER=2
	elif (( "${key}" >= "6" )); then # 2 out of 10 - medium
		get_random "MSG_SIZE" 1000 10000
		FILE_SIZE_MULTIPLIER=1
	else                             # 5 out of 10 - small
		get_random "MSG_SIZE" 10 1000
		FILE_SIZE_MULTIPLIER=1
	fi
}

# Start or restart broker
# $1: Log suffix: either "A" or "B". If "A", broker is started with truncation, otherwise broker is restarted with recovery.
# $2: Truncate flag - only used if Log suffix is "A": if true, then truncate store
# The PID of the broker is returned in ${QPIDD_PID}
start_broker() {
	local truncate_val
	local truncate_str
	if [[ "$1" == "A" ]]; then
		if [[ $2 == true ]]; then
			truncate_val="yes"
			truncate_str="(Store truncated)"
			if [[ -e ${BASE_DIR}/qls/p001/efp/${DEFAULT_EFP_DIR} ]]; then
				for f in ${BASE_DIR}/qls/p001/efp/${DEFAULT_EFP_DIR}/*; do
					local filesize=`stat -c%s "${f}"`
					if (( ${filesize} != ${DEFAULT_EFP_SIZE} )); then
						rm ${f}
					fi
				done
			fi
		else
			truncate_val="no"
		fi
	else
		truncate_val="no"
	fi
	echo "${QPIDD} ${QPIDD_BASE_ARGS} --truncate ${truncate_val} --log-to-file ${RESULT_DIR}/qpidd.$1.log &" > ${RESULT_DIR}/qpidd.$1.cmd
	${QPIDD} ${QPIDD_BASE_ARGS} --truncate ${truncate_val} --log-to-file ${RESULT_DIR}/qpidd.$1.log &
	QPIDD_PID=$!
	echo "Broker PID=${QPIDD_PID} ${truncate_str}"  | tee -a ${LOG_FILE}
}

# Start or evaluate results of transaction test client
# $1: Log suffix flag: either "A" or "B". If "A", client is started in test mode, otherwise client evaluates recovery.
start_tx_test() {
	local tx_test_params="--messages-per-tx ${MSGS_PER_TX} --tx-count 1000000 --total-messages ${TOT_MSGS} --size ${MSG_SIZE} --queues ${NUM_QUEUES}"
	if [[ "$1" == "A" ]]; then
		# Run in background
		echo "${TXTEST##*/} parameters: ${tx_test_params}" | tee -a ${LOG_FILE}
		echo "${TXTEST} ${tx_test_params} ${TXTEST_INIT_STR} &> ${RESULT_DIR}/txtest.$1.log" > ${RESULT_DIR}/txtest.$1.cmd
		${TXTEST} ${tx_test_params} ${TXTEST_INIT_STR} &> ${RESULT_DIR}/txtest.$1.log
		echo "${TXTEST} ${tx_test_params} ${TXTEST_RUN_STR} &> ${RESULT_DIR}/txtest.$1.log &" >> ${RESULT_DIR}/txtest.$1.cmd
		${TXTEST} ${tx_test_params} ${TXTEST_RUN_STR} &> ${RESULT_DIR}/txtest.$1.log &
	else
		# Run in foreground
		#echo "${TXTEST##*/} ${tx_test_params} ${TXTEST_CHK_STR}" | tee -a ${LOG_FILE}
		echo "${TXTEST} ${tx_test_params} ${TXTEST_CHK_STR} &> ${RESULT_DIR}/txtest.$1.log" > ${RESULT_DIR}/txtest.$1.cmd
		${TXTEST} ${tx_test_params} ${TXTEST_CHK_STR} &> ${RESULT_DIR}/txtest.$1.log
	fi
}

# Search for the presence of core.* files, move them into the current result directory and run gdb against them.
# No params
process_core_files() {
	ls core.* &> /dev/null
	if (( "$?" == "0" )); then
		for cf in core.*; do
			gdb --batch --quiet -ex "thread apply all bt" -ex "quit" ${QPIDD} ${cf} &> ${RESULT_DIR}/${cf##*/}.gdb.txt
			gdb --batch --quiet -ex "thread apply all bt full" -ex "quit" ${QPIDD} ${cf} &> ${RESULT_DIR}/${cf##*/}.gdb-full.txt
			cat ${RESULT_DIR}/${cf##*/}.gdb.txt
			mv ${cf} ${RESULT_DIR}/
			echo "Core file ${cf##*/} found and recovered"
		done
	fi
}

# Kill a process quietly
# $1: Signal
# $2: PID
kill_process() {
	kill ${1} ${2} &>> ${LOG_FILE}
	wait ${2} &>> ${LOG_FILE}
}

# Check that test can run: No other copy of qpidd running, enough disk space
check_ready_to_run() {
	# Check no copy of qpidd is running
	PID=`pgrep ${QPIDD_FN}`
	if [[ "$?" == "0" ]]; then
		echo "ERROR: qpidd running as pid ${PID}"
		exit 1
	fi
	# Check disk is < 90% full
	local perc_full=`df -h ${HOME} | tail -1 | awk '{print substr($5,0, length($5)-1)}'`
	if (( ${perc_full} >= ${MAX_DISK_PERC_USED} )); then
		echo "ERROR: Disk is too close to full (${perc_full}%)"
		exit 2
	fi
}

# Analyze store files
# $1: Log suffix flag: either "A" or "B". If "A", client is started in test mode, otherwise client evaluates recovery.
analyze_store() {
	${ANALYZE} ${ANALYZE_ARGS} ${BASE_DIR}/qls &> ${RESULT_DIR}/qls_analysis.$1.log
	echo >> ${RESULT_DIR}/qls_analysis.$1.log
	echo "----------------------------------------------------------" >> ${RESULT_DIR}/qls_analysis.$1.log
	echo "With transactional reconsiliation:" >> ${RESULT_DIR}/qls_analysis.$1.log
	echo >> ${RESULT_DIR}/qls_analysis.$1.log
	${ANALYZE} ${ANALYZE_ARGS} --txn ${BASE_DIR}/qls &>> ${RESULT_DIR}/qls_analysis.$1.log
}

ulimit -c unlimited # Allow core files to be created

RESULT_BASE_DIR_SUFFIX=`date "${TIMESTAMP_FORMAT}"`
RESULT_BASE_DIR="${RESULT_BASE_DIR_PREFIX}.${RESULT_BASE_DIR_SUFFIX}"
LOG_FILE=${RESULT_BASE_DIR}/${LOG_FILE_NAME}
if [[ -n "${RESULT_BASE_DIR}" ]]; then
	rm -rf ${RESULT_BASE_DIR}
fi

mkdir -p ${RESULT_BASE_DIR}
for rn in `seq ${NUM_RUNS}`; do
	# === Prepare result dir, check ready to run test, set run vars ===
	RESULT_DIR=${RESULT_BASE_DIR}/run_${rn}
	mkdir -p ${RESULT_DIR}
	check_ready_to_run
	if (( (${rn} - 1) % ${TRUNCATE_INTERVAL} == 0 )) || [[ -n ${ERROR_FLAG} ]]; then
		TRUNCATE_FLAG=true
	else
		TRUNCATE_FLAG=false
	fi
	set_message_size
	get_random "MSGS_PER_TX" 1 20
	get_random "TOT_MSGS" 100 1000
    get_random "NUM_QUEUES" 2 15
	MIN_RUNTIME=$(( 20 * ${FILE_SIZE_MULTIPLIER} ))
	MAX_RUNTIME=$(( 120 * ${FILE_SIZE_MULTIPLIER} ))	
	get_random "RUN_TIME" ${MIN_RUNTIME} ${MAX_RUNTIME}
	RECOVER_TIME=$(( ${NUM_QUEUES} * ${RECOVER_TIME_PER_QUEUE} * ${FILE_SIZE_MULTIPLIER} ))
	echo "Run ${rn} of ${NUM_RUNS} ==============" | tee -a ${LOG_FILE}

	# === PART A: Initial run of qpid-txtest ===
	start_broker "A" ${TRUNCATE_FLAG}
	sleep ${RECOVER_TIME} # Need a way to test if broker has started here
	start_tx_test "A"
	echo "Running for ${RUN_TIME} secs..." | tee -a ${LOG_FILE}
	sleep ${RUN_TIME}
	kill_process ${SIG_KILL} ${QPIDD_PID}
	sleep 2
	analyze_store "A"
	tar -czf ${RESULT_DIR}/qls_A.tar.gz ${RELATIVE_BASE_DIR}/qls

	# === PART B: Recovery and check ===
	start_broker "B"
	echo "Recover time=${RECOVER_TIME} secs..." | tee -a ${LOG_FILE}
	sleep ${RECOVER_TIME} # Need a way to test if broker has started here
	start_tx_test "B"
	sleep 1
	kill_process ${SIG_TERM} ${QPIDD_PID}
	sleep 2
	PID=`pgrep ${QPIDD_FN}`
	if [[ "$?" == "0" ]]; then
		kill_process ${SIG_KILL} ${PID}
		sleep 2
	fi
	analyze_store "B"
	tar -czf ${RESULT_DIR}/qls_B.tar.gz ${RELATIVE_BASE_DIR}/qls

	# === Check for errors, cores and exceptions in logs ===
	grep -Hn "jexception" ${RESULT_DIR}/qpidd.A.log | tee -a ${LOG_FILE}
	grep -Hn "jexception" ${RESULT_DIR}/qpidd.B.log | tee -a ${LOG_FILE}
	grep -Hn "Traceback (most recent call last):" ${RESULT_DIR}/qls_analysis.A.log | tee -a ${LOG_FILE}
	grep -Hn "Traceback (most recent call last):" ${RESULT_DIR}/qls_analysis.B.log | tee -a ${LOG_FILE}
	grep "${SUCCESS_MSG}" ${RESULT_DIR}/txtest.B.log &> /dev/null
	if [[ "$?" != "0" ]]; then
		echo "ERROR in run ${rn}" >> ${LOG_FILE}
		echo -e "${ANSI_RED}ERROR${ANSI_NONE} in run ${rn}"
		ERROR_FLAG=true
	else
		unset ERROR_FLAG
	fi
	sleep 2
	process_core_files
	echo | tee -a ${LOG_FILE}
done

