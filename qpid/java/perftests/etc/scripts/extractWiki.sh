#!/bin/bash
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

# Extract all results from the current set of results directories in wiki
# format. Output is compatible with Atlassian Confluence only. Runs from
# any directory with no arguments, output to standard out.

if [ $# -eq 0 ]
then
    dirs=$(echo results-*)
else
    dirs=$(echo $*)
fi

pushd . > /dev/null
cd $(dirname "$0")

ver=$(basename $(dirname $(pwd)))
node=$(uname -n)

echo "h1. Qpid ${ver} Test Results"
echo
echo "{toc:type=flat|separator=pipe|minLevel=2|maxLevel=3}"
echo
for r in ${dirs}
do
	if [ -d "${r}" ]
	then
		t=$(echo "${r}" | cut -d\- -f2)
		f=$(echo "${t}" | sed -e "s/^\(.\).*$/\1/")
		l=$(echo "${t}" | sed -e "s/^.\(.*\)$/\1/")
		T=$(echo "${f}" | tr '[a-z]' '[A-Z]')
		echo "h2. ${T}${l}"
		echo
		echo "Generated on _$(ls -ld ${r} | cut -d\  -f7-9)_."
		echo
		echo "{table:cellpadding=3|cellspacing=0|border=1}"
		echo "{tr}{td:bgcolor=#eeeeff}*Test*{td}"
		echo "{td:bgcolor=#eeeeff}*Total*{td}{td:bgcolor=#eeeeff}*Passed*{td}"
		echo "{td:bgcolor=#eeeeff}*Failed*{td}{td:bgcolor=#eeeeff}*Errors*{td}{tr}"
		./extractResults.sh "${r}" |
			sed -e "s/,/{td}{td}/g" |
			sed -e "s/{td}\s*Error:\s*\([1-9][0-9]*\)/{td:bgcolor=#ffeeee}*\1*/" |
			sed -e "s/{td}\s*Failed:\s*\([1-9][0-9]*\)/{td:bgcolor=#ffeeee}*\1*/" |
			sed -e "s/^/{tr}{td}/" |
			sed -e "s/$/{td}{tr}/" |
			sed -e "s/[A-Z][a-z]*:\s*//g" |
			sed -e "s/\s*//g"
		echo "{table}"
		echo
	fi
done

if echo ${dirs} | grep  results-throughput > /dev/null && test -d results-throughput
then
	echo "h2. Throughput Numbers"
	echo
	echo "Generated on _$(ls -ld results-throughput | cut -d\  -f7-9)_."
	echo
	echo "{table:cellpadding=3|cellspacing=0|border=1}"
	echo "{tr}{td:bgcolor=#eeeeff}*Test*{td}{td:bgcolor=#eeeeff}*Thoughput*{td}{tr}"
	./extractThroughputResults.sh results-throughput |
		sed -e "s/,/{td}{td}/g" |
		sed -e "s/^/{tr}{td}/" |
		sed -e "s/$/{td}{tr}/" |
		sed -e "s/\s*//g"
	echo "{table}"
	echo
fi

popd > /dev/null 2>&1
