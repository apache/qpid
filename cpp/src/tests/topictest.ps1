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

# Parameters with default values: s (subscribers) m (messages) b (batches)
#                                 h (host) t (false; use transactions)
param (
  [int]$subscribers = 10,
  [int]$message_count = 2000,
  [int]$batches = 10,
  [string]$broker,
  [switch] $t           # transactional
)

# Run the C++ topic test
[string]$me = $myInvocation.InvocationName
$srcdir = Split-Path $me
#$srcdir = Split-Path $myInvocation.InvocationName

# Clean up old log files
Get-Item subscriber_*.log | Remove-Item

if ($t) {
    $transactional = "--transactional --durable"
}

# Find which subdir the exes are in
. $srcdir\find_prog.ps1 .\qpid-topic-listener.exe

function subscribe {
    param ([int]$num, [string]$sub)
    "Start subscriber $num"
    $LOG = "subscriber_$num.log"
    $cmdline = ".\$sub\qpid-topic-listener $transactional > $LOG 2>&1
                if (`$LastExitCode -ne 0) { Remove-Item $LOG }"
    $cmdblock = $executioncontext.invokecommand.NewScriptBlock($cmdline)
    . $srcdir\background.ps1 $cmdblock
}

function publish {
    param ([string]$sub)
    Invoke-Expression ".\$sub\qpid-topic-publisher --messages $message_count --batches $batches --subscribers $subscribers $host $transactional" 2>&1
}

if ($broker.length) {
  $broker = "-h$broker"
}

$i = $subscribers
while ($i -gt 0) {
  subscribe $i $sub
  $i--
}

# FIXME aconway 2007-03-27: Hack around startup race. Fix topic test.
Start-Sleep 2
publish $sub
exit $LastExitCode
