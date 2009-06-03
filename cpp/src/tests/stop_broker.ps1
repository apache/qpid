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

# Stop the broker, check for errors.
Get-Content -path qpidd.port -totalCount 1 | Set-Variable -name qpid_port
Remove-Item qpidd.port

# Piping the output makes the script wait for qpidd to finish.
..\Debug\qpidd --quit --port $qpid_port | Write-Output

# Check qpidd.log.
filter bad_stuff {
  $_ -match "( warning | error | critical )"
}

$qpidd_errors = $false
Get-Content -path qpidd.log | where { bad_stuff } | Out-Default | Set-Variable -name qpidd_errors -value $true
if ($qpidd_errors -eq $true) {
  "WARNING: Suspicious broker log entries in qpidd.log, above."
  exit 1
}

exit 0
