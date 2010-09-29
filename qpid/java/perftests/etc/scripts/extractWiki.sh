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

# Extract all results from the current set of results directories in wiki format.
# Runs from any directory with no arguments, output to standard out.

pushd .
cd $(dirname "$0")

echo "h1. Performance Testing Results"
echo
for r in results-*
do
	t=$(echo "${r}" | cut -d\- -f2)
	f=$(echo "${t}" | sed -e "s/^\(.\).*$/\1/")
	l=$(echo "${t}" | sed -e "s/^.\(.*\)$/\1/")
	T=$(echo "${f}" | tr '[a-z]' '[A-Z]')
	echo "h2. ${T}${l}"
	echo
	echo "||Test ||Total ||Passed ||Failed ||Error ||"
	./extractResults.sh "${r}" | tr "," "\|" | sed -e "s/^/\|/" | sed -e "s/$/\|/" | sed -e "s/[A-Z][a-z]*:\ *//g"
	echo
done

popd
