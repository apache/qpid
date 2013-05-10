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

# Library Version Information (CURRENT.REVISION.AGE):
#
#  CURRENT  => API/ABI version.  Bump this if the interface changes
#  REVISION => Version of underlying implementation.
#              Bump if implementation changes but API/ABI doesn't
#  AGE      => Number of API/ABI versions this is backward compatible with

set (qmf_version 1.0.0)
set (qmf2_version 1.0.0)
set (qmfconsole_version 2.0.0)
set (qmfengine_version 1.1.0)
set (qpidbroker_version 2.0.0)
set (qpidclient_version 2.0.0)
set (qpidcommon_version 2.0.0)
set (qpidmessaging_version 2.0.0)
set (qpidtypes_version 1.0.0)
set (rdmawrap_version 2.0.0)
set (sslcommon_version 2.0.0)
set (legacystore_version 1.0.0)

string(REGEX MATCH "[0-9]*" qmf_version_major ${qmf_version})
string(REGEX MATCH "[0-9]*" qmf2_version_major ${qmf2_version})
string(REGEX MATCH "[0-9]*" qmfconsole_version_major ${qmfconsole_version})
string(REGEX MATCH "[0-9]*" qmfengine_version_major ${qmfengine_version})
string(REGEX MATCH "[0-9]*" qpidbroker_version_major ${qpidbroker_version})
string(REGEX MATCH "[0-9]*" qpidclient_version_major ${qpidclient_version})
string(REGEX MATCH "[0-9]*" qpidcommon_version_major ${qpidcommon_version})
string(REGEX MATCH "[0-9]*" qpidmessaging_version_major ${qpidmessaging_version})
string(REGEX MATCH "[0-9]*" qpidtypes_version_major ${qpidtypes_version})
string(REGEX MATCH "[0-9]*" rdmawrap_version_major ${rdmawrap_version})
string(REGEX MATCH "[0-9]*" sslcommon_version_major ${sslcommon_version})
string(REGEX MATCH "[0-9]*" legacystore_version_major ${legacystore_version})
