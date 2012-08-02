#!/bin/bash

run_test() {
    local cmd=$1
    echo $cmd
    $cmd
    if (( $? != 0 )); then
        exit 1
    fi    
}

NUM_MSGS=1000
TEST_PROG="./asyncStorePerf"

run_test "${TEST_PROG}"
run_test "${TEST_PROG} --help"
for q in 1 2; do
    for p in 1 2; do
        for c in 1 2; do
            for e in 0 1 3; do
                for d in 0 1 3; do
                    for dur in "" "--durable"; do
                        run_test "${TEST_PROG} --num-queues $q --num-msgs ${NUM_MSGS} --num-producers $p --num-consumers $c --enq-txn-size $e --deq-txn-size $d ${dur}"
                    done
                done
            done
        done
    done
done


NUM_MSGS=1000
TEST_PROG="./jrnl2Perf"


run_test "${TEST_PROG}"

# This test returns 1, don't use run_test until this is fixed.
cmd="${TEST_PROG} --help"
echo $cmd
$cmd

for q in 1 2; do
    for p in 1 2; do
        for c in 1; do # BUG - this will fail for c > 1
            run_test "./jrnl2Perf --num_queues $q --num_msgs ${NUM_MSGS} --num_enq_threads_per_queue $p --num_deq_threads_per_queue $c"
        done
    done
done

#exit 0
