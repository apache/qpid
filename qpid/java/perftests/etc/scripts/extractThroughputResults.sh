#!/bin/bash +x
#
# Process a given directory (defaults to '.') and provides the throughput results as 
# reported by the tests.
# 
# if a second argument of -n is provided then it will only list the number output for 
# easy copy/paste
#

if [ $# == 0 ] ; then
 dir=.
else
 dir=$1
fi

numeric=0
if [ "$dir" == "-n" ] ; then
 numeric=1
 dir=.
fi

if [ "$2" == "-n" ] ; then
 numeric=1
fi

if [ $numeric == 1 ] ; then
   grep 'Total Tests:' $dir/*Qpid* | sed -e 's/^.*\/\([A-Z]*-[A-Z][A-Z]-Qpid-01\).*Size Throughput:, \([0-9.]*\).*$/\1, \2/' | sed -e 's/\.\([0-9][0-9][0-9]\)/\1\./' | sed -e 's/, 0/, /' | awk '{print $2}'
else
   grep 'Total Tests:' $dir/*Qpid* | sed -e 's/^.*\/\([A-Z]*-[A-Z][A-Z]-Qpid-01\).*Size Throughput:, \([0-9.]*\).*$/\1, \2/' | sed -e 's/\.\([0-9][0-9][0-9]\)/\1\./' | sed -e 's/, 0/, /'
fi
