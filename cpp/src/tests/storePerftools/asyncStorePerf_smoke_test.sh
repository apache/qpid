#!/bin/bash

run_test() {
    local cmd=$1
    echo $cmd
    $cmd
    if (( $? != 0 )); then
        exit 1
    fi    
}

NUM_MSGS=10000
TEST_PROG="./asyncStorePerf"

# Default (no args)
run_test "${TEST_PROG}"

# Help
run_test "${TEST_PROG} --help"

# Limited combinations of major params
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
