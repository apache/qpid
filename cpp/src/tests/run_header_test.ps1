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

# Simple test of encode/decode of a double in application headers
# TODO: this should be expanded to cover a wider set of types and go
# in both directions

$srcdir = Split-Path $myInvocation.InvocationName
$PYTHON_DIR = "$srcdir\..\..\..\python"
if (Test-Path qpidd.port) {
   set-item -path env:QPID_PORT -value (get-content -path qpidd.port -totalcount 1)
}

if (Test-Path $PYTHON_DIR -pathType Container) {
    Invoke-Expression "$env:OUTDIR\header_test -p $env:QPID_PORT"
    $env:PYTHONPATH="$PYTHON_DIR;$env:PYTHONPATH"
    python "$srcdir/header_test.py" "localhost" "$env:QPID_PORT"
    exit $LASTEXITCODE
}
else {
    "Skipping header test as python libs not found"
    exit 0
}
