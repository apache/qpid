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

# Quick and quiet topic test for make check.
$srcdir = Split-Path $myInvocation.ScriptName
$PsHome\powershell $srcdir\topictest.ps1 -subscribers 2 -messages 2 -batches 1 > topictest.log 2>&1
if ($LastExitCode != 0) {
    echo $0 FAILED:
    cat topictest.log
    exit $LastExitCode
}
rm topictest.log
exit 0
