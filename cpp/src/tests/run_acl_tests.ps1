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

# Run the acl tests.

$srcdir = Split-Path $myInvocation.InvocationName
. .\test_env.ps1
if (!(Test-Path $PYTHON_DIR -pathType Container)) {
    "Skipping acl tests as python libs not found"
    exit 1
}

$Global:BROKER_EXE = ""

Function start_broker($acl_options)
{
  # Test runs from the tests directory but the broker executable is one level
  # up, and most likely in a subdirectory from there based on what build type.
  # Look around for it before trying to start it.
  . $srcdir\find_prog.ps1 ..\qpidd.exe
  if (!(Test-Path $prog)) {
    "Cannot locate qpidd.exe"
    exit 1
  }
  $Global:BROKER_EXE = $prog
  if (Test-Path qpidd.port) {
    Remove-Item qpidd.port
  }
  $cmdline = "$prog --auth=no --no-module-dir --port=0 --log-to-file qpidd.log $acl_options | foreach { set-content qpidd.port `$_ }"
  $cmdblock = $executioncontext.invokecommand.NewScriptBlock($cmdline)
  . $srcdir\background.ps1 $cmdblock
  # Wait for the broker to start
  $wait_time = 0
  while (!(Test-Path qpidd.port) -and ($wait_time -lt 30)) {
    Start-Sleep 2
    $wait_time += 2
  }
  if (!(Test-Path qpidd.port)) {
    "Timeout waiting for broker to start"
    exit 1
  }
  set-item -path env:BROKER_PORT -value (get-content -path qpidd.port -totalcount 1)
}

Function stop_broker
{
  "Stopping $Global:BROKER_EXE"
  Invoke-Expression "$Global:BROKER_EXE --no-module-dir -q --port $env:BROKER_PORT" | Write-Output
  Remove-Item qpidd.port
}

$DATA_DIR = [IO.Directory]::GetCurrentDirectory() + "\data_dir"
Remove-Item $DATA_DIR -recurse
New-Item $DATA_DIR -type directory
Copy-Item $srcdir\policy.acl $DATA_DIR
start_broker("--data-dir $DATA_DIR --acl-file policy.acl")
"Running acl tests using broker on port $env:BROKER_PORT"
Invoke-Expression "python $PYTHON_DIR/qpid-python-test -m acl -b localhost:$env:BROKER_PORT" | Out-Default
$RETCODE=$LASTEXITCODE
stop_broker

# Now try reading the acl file from an absolute path.
Remove-Item qpidd.log
$policy_full_path = "$srcdir\policy.acl"
start_broker("--no-data-dir --acl-file $policy_full_path")
#test_loading_acl_from_absolute_path(){
#    POLICY_FILE=$srcdir/policy.acl
#    rm -f temp.log
#    PORT=`../qpidd --daemon --port 0 --no-module-dir --no-data-dir --auth no --load-module $ACL_LIB --acl-file $POLICY_FILE -t --log-to-file temp.log  2>/dev/null`
#    ACL_FILE=`grep "notice Read ACL file" temp.log | sed 's/^.*Read ACL file //'`
#   $QPIDD_EXEC --no-module-dir -q --port $PORT
#   if test "$ACL_FILE" != "\"$POLICY_FILE\""; then
#     echo "unable to load policy file from an absolute path";
#     return 1;
#   fi
#   rm temp.log
#}
#
#    test_loading_acl_from_absolute_path || EXITCODE=1
#    rm -rf $DATA_DIR
#    exit $EXITCODE
stop_broker
exit $RETCODE
