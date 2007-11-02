#!/bin/sh
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
# Generate the gen-src.mk makefile fragment, to stdout.
# Usage: <gentools_dir> <gentools_srcdir>

gentools_dir=$1
gentools_srcdir=$2

wildcard() { echo `ls $* 2>/dev/null` ; }

cat <<EOF
generated_sources = `wildcard *.cpp`

generated_headers = `wildcard *.h`

if CAN_GENERATE_CODE

java_sources = `wildcard $gentools_srcdir/*.java`

cxx_templates = `wildcard $gentools_dir/templ.cpp/*.tmpl`

# Empty rules in case one of these files is removed,
# renamed or no longer generated.
\$(spec):
\$(java_sources):
\$(cxx_templates):
endif

EOF


