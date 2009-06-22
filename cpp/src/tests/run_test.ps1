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

# Set up environment and run a test executable or script.
$env:QPID_DATA_DIR = ""
$env:BOOST_TEST_SHOW_PROGRESS = "yes"

# The test exe is probably not in the current binary dir - it's usually
# placed in a subdirectory based on the configuration built in Visual Studio.
# So check around to see where it is - when located, set the QPID_LIB_DIR
# and PATH to look in the corresponding configuration off the src directory,
# one level up.
$prog = $args[0]
$is_script = $prog -match ".ps1$"
if (Test-Path $prog) {
   $env:QPID_LIB_DIR = ".."
   $env:PATH += ";.."
}
else {
   $dir = Split-Path $prog
   $exe = Split-Path $prog -leaf
   $subs = "Debug","Release","MinSizeRel","RelWithDebInfo"
   foreach ($sub in $subs) {
      $prog = "$dir\$sub\$exe"
      if (Test-Path $prog) {
         $args[0] = $prog
         $env:QPID_LIB_DIR = "..\$sub"
         $env:PATH += ";..\$sub"
         break
      }
   }
}

# If qpidd.port exists and is not empty run test with QPID_PORT set.
if (Test-Path qpidd.port) {
   set-item -path env:QPID_PORT -value (get-content -path qpidd.port -totalcount 1)
}

#$p = new-object System.Diagnostics.Process
$si = new-object System.Diagnostics.ProcessStartInfo
$si.WorkingDirectory = $pwd
$si.UseShellExecute = $true

if ($is_script) {
   $si.FileName = (get-command powershell.exe).Definition
   $si.Arguments = $args
}
else {
   $si.FileName = $args[0]
   if ($args.length > 1) {
      $si.Arguments = $args[1..$args.length-1]
   }
}
$p = [diagnostics.process]::Start($si)
$p.WaitForExit()
exit $?
