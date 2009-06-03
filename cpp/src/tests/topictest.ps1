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

# Run the C++ topic test

# Clean up old log files
Get-Item subscriber_*.log | Remove-Item

# Parameters with default values: s (subscribers) m (messages) b (batches)
#                                 h (host) t (false; use transactions)
param (
  [int]$subscribers = 10,
  [int]$messages = 2000,
  [int]$batches = 10,
  [string]$broker,
  [switch] $t           # transactional
)

function subscribe {
    "Start subscriber $args[0]"
    $LOG = "subscriber_$args[0].log"
    . $srcdir\background.ps1 {
      $env:OUTDIR\topic_listener $TRANSACTIONAL > $LOG 2>&1
      if ($LastExitCode -ne 0) { Remove-Item $LOG }
    } -inconsole
}

publish() {
    if ($t) {
      $transactional = "--transactional --durable"
    }
    $env:OUTDIR\topic_publisher --messages $messages --batches $batches --subscribers $subscribers $host $transactional 2>&1
}

$srcdir = Split-Path $myInvocation.ScriptName
if ($broker.length) {
  $broker = "-h$broker"
}

$i = $subscribers
while ($i -gt 0) {
  subscribe $i
  $i--
}

# FIXME aconway 2007-03-27: Hack around startup race. Fix topic test.
Start-Sleep 2
publish
exit $LastExitCode
