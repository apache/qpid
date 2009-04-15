#!/bin/bash +x
#
# Process a given directory (defaults to '.') and provide a list of the tests run so 
# identification of any failures can be seen.
#

if [ $# == 0 ] ; then
 dir=.
else
 dir=$1
fi

grep 'Total Tests:' $dir/*Qpid* | sed -e 's/^.*\/\([A-Z\-]*-Qpid-[0-9]*\).*Total Tests:, \([0-9.]*\).*Total Passed:, \([0-9.]*\).*Total Failed:, \([0-9.]*\).*Total Error:, \([0-9.]*\).*$/\1, Total:\t\2,\tPassed:\t\3,\tFailed:\t\4,\tError:\t\5/'
