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
if (!(Test-Path $PYTHON_DIR -pathType Container)) {
    "Skipping msg_group test as python libs not found"
    exit 0
}

. .\test_env.ps1

if (Test-Path qpidd.port) {
   set-item -path env:QPID_PORT -value (get-content -path qpidd.port -totalcount 1)
}

# Test runs from the tests directory but the test executables are in a
# subdirectory based on the build type. Look around for it before trying
# to start it.
. $srcdir\find_prog.ps1 .\msg_group_test.exe
if (!(Test-Path $prog)) {
    "Cannot locate msg_group_test.exe"
    exit 1
}

$QUEUE_NAME="group-queue"
$GROUP_KEY="My-Group-Id"
$BROKER_URL="localhost:$env:QPID_PORT"

$tests=@("python $QPID_CONFIG_EXEC -b $BROKER_URL add queue $QUEUE_NAME --group-header=${GROUP_KEY} --shared-groups",
    "$prog -b $BROKER_URL -a $QUEUE_NAME --group-key $GROUP_KEY --messages 103 --group-size 13 --receivers 2 --senders 3 --capacity 3 --ack-frequency 7 --randomize-group-size --interleave 3",
    "$prog -b $BROKER_URL -a $QUEUE_NAME --group-key $GROUP_KEY --messages 103 --group-size 13 --receivers 2 --senders 3 --capacity 7 --ack-frequency 7 --randomize-group-size",
    "python $QPID_CONFIG_EXEC -b $BROKER_URL add queue ${QUEUE_NAME}-two --group-header=${GROUP_KEY} --shared-groups",
    "$prog -b $BROKER_URL -a $QUEUE_NAME --group-key $GROUP_KEY --messages 103 --group-size 13 --receivers 2 --senders 3 --capacity 7 --ack-frequency 3 --randomize-group-size",
    "$prog -b $BROKER_URL -a ${QUEUE_NAME}-two --group-key $GROUP_KEY --messages 103 --group-size 13 --receivers 2 --senders 3 --capacity 3 --ack-frequency 7 --randomize-group-size --interleave 5",
    "$prog -b $BROKER_URL -a $QUEUE_NAME --group-key $GROUP_KEY --messages 59  --group-size 5  --receivers 2 --senders 3 --capacity 1 --ack-frequency 3 --randomize-group-size",
    "python $QPID_CONFIG_EXEC -b $BROKER_URL del queue ${QUEUE_NAME}-two --force",
    "$prog -b $BROKER_URL -a $QUEUE_NAME --group-key $GROUP_KEY --messages 59  --group-size 3  --receivers 2 --senders 3 --capacity 1 --ack-frequency 1 --randomize-group-size",
    "$prog -b $BROKER_URL -a $QUEUE_NAME --group-key $GROUP_KEY --messages 211 --group-size 13 --receivers 2 --senders 3 --capacity 47 --ack-frequency 79 --interleave 53",
    "$prog -b $BROKER_URL -a $QUEUE_NAME --group-key $GROUP_KEY --messages 10000  --group-size 1 --receivers 0 --senders 1",
    "$prog -b $BROKER_URL -a $QUEUE_NAME --group-key $GROUP_KEY --messages 10000  --receivers 5 --senders 0",
    "python $QPID_CONFIG_EXEC -b $BROKER_URL del queue $QUEUE_NAME --force")

foreach ($cmd in $tests)
{
  Invoke-Expression "$cmd" | Write-Output
  $ret = $LASTEXITCODE
  if ($ret -ne 0) {Write-Host "FAILED message group test. Failed command: $cmd"
    break}
}
exit $ret
