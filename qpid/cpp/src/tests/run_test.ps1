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

param(
  [string]$workingDir = $pwd,
  [string]$buildDir = $(throw "-buildDir is required"),
  [string]$sourceDir,
  [switch]$python = $false,
  [switch]$boostTest = $false,
  [switch]$xml,
  [switch]$startBroker = $false,
  [string]$brokerOptions,
  [switch]$help,
  [Parameter(Mandatory=$true, ValueFromRemainingArguments=$true, Position=0)]
  [String[]]$rest
  )

if ([string]::IsNullOrEmpty($sourceDir)) {
  $sourceDir = Split-Path $myInvocation.InvocationName
}

if ([string]::IsNullOrEmpty($xml)) {
  $xml = Test-Path variable:global:QPID_XML_TEST_OUTPUT
}
 
# Set up environment and run a test executable or script.
. .\test_env.ps1

if ($rest[0] -eq $null) {
   "No wrapped command specified"
   exit 1
}
# The test exe is probably not in the current binary dir - it's usually
# placed in a subdirectory based on the configuration built in Visual Studio.
# So check around to see where it is - when located, set the QPID_LIB_DIR
# and PATH to look in the corresponding configuration off the src directory,
# one level up.
$prog = $rest[0]
$logfilebase = [System.IO.Path]::GetFileNameWithoutExtension($prog)
$logfilebase = "$pwd\\$logfilebase"
# Qpid client lib sees QPID_LOG_TO_FILE; acts like using --log-to-file on
# command line.
$env:QPID_LOG_TO_FILE = "$logfilebase.log"
$is_script = $prog -match ".ps1$"
if (($is_script -or $python) -and !(Test-Path "$prog")) {
   "$prog does not exist"
   exit 1
}
if (!$is_script -and !(Test-Path "$prog")) {
   . $sourceDir\find_prog.ps1 $prog
   $rest[0] = $prog
   $env:QPID_LIB_DIR = "..\$sub"
}

# Set up environment for running a Qpid test. If a broker should be started,
# do that, else check for a saved port number to use.
if ($startBroker) {
  $broker = new-object System.Diagnostics.ProcessStartInfo
  $broker.WorkingDirectory = $pwd
  $broker.UseShellExecute = $false
  $broker.CreateNoWindow = $true
  $broker.RedirectStandardOutput = $true
  $broker.FileName = $env:QPIDD_EXEC
  $broker.Arguments = "--auth=no --no-module-dir --port=0 --interface 127.0.0.1 --log-to-file $logfilebase-qpidd.log $brokerOptions"
  $broker_process = [System.Diagnostics.Process]::Start($broker)
  $env:QPID_PORT = $broker_process.StandardOutput.ReadLine()
}
else {
  # If qpidd.port exists and is not empty run test with QPID_PORT set.
  if (Test-Path qpidd.port) {
     set-item -path env:QPID_PORT -value (get-content -path qpidd.port -totalcount 1)
  }
}

# Now start the real test.
if ($python) {
  $to_run = $PYTHON_EXE
  $skip_args0 = $false
  $outputfile = ""
}
elseif ($boostTest) {
  if ($xml) {
    $env:BOOST_TEST_SHOW_PROGRESS=no
    $env:BOOST_TEST_OUTPUT_FORMAT=XML
    $env:BOOST_TEST_LOG_LEVEL=test_suite
    $env:BOOST_TEST_REPORT_LEVEL=no
    $to_run = $rest[0]
    $skip_args0 = $true
    $outputfile = "$logfilebase-unittest.xml"
  }
  else {
    $to_run = $rest[0]
    $skip_args0 = $true
    $outputfile = ""
  }
}
else {
  # Non-boost executable or powershell script
  $outputfile = ""
  if ($is_script) {
    $to_run = (get-command powershell.exe).Definition
    $skip_args0 = $false
  }
  else {
    $to_run = $rest[0]
    $skip_args0 = $true
  }
}

if ($skip_args0) {
   $arglist = $rest[1..($rest.length-1)]
}
else {
  $arglist = $rest
}

if ($outputfile -eq "") {
  $p = Start-Process -FilePath $to_run -ArgumentList $arglist -NoNewWindow -PassThru
  $line = ""
}
else {
  $p = Start-Process -FilePath $to_run -ArgumentList $arglist -NoNewWindow -RedirectStandardOutput $outputfile -PassThru
}
Wait-Process -InputObject $p
$status = $p.ExitCode

if (Test-Path $env:QPID_LOG_TO_FILE) {
  $problems = Select-String -Path $env:QPID_LOG_TO_FILE -pattern " error ", " warning ", " critical "
  if ($problems -ne $null) {
    "WARNING: suspicious log entries in $env:QPID_LOG_TO_FILE:\n$problems"
    $status = 1
  }
}

# If a broker was started, stop it.
if ($startBroker) {
  & $env:QPIDD_EXEC --no-module-dir --quit
  # Check qpid log for problems
  $problems = Select-String -Path $logfilebase-qpidd.log -pattern " error ", " warning ", " critical "
  if ($problems -ne $null) {
    "WARNING: suspicious log entries in $logfilebase-qpidd.log:\n$problems"
    $status = 1
  }
}

exit $status
