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

#
# Versions settings overrides for Windows dll/exe file version resource.
# These values are compiled into the dll and exe files.
#
# The settings override precedence from lowest to highest:
# 1. CPACK settings from cpp/CMakeLists.txt
# 2. Global settings from this file
# 3. Command line version number (only) from add_msvc_version_full call
# 4. Per-project settings from this file
#

#
# Specification of global settings for all projects.
#
# set ("winver_PACKAGE_NAME"         "qpid-cpp")
# set ("winver_DESCRIPTION_SUMMARY"  "Apache Qpid C++")
# set ("winver_FILE_VERSION_N1"      "0")
# set ("winver_FILE_VERSION_N2"      "11")
# set ("winver_FILE_VERSION_N3"      "0")
# set ("winver_FILE_VERSION_N4"      "0")
# set ("winver_PRODUCT_VERSION_N1"   "0")
# set ("winver_PRODUCT_VERSION_N2"   "11")
# set ("winver_PRODUCT_VERSION_N3"   "0")
# set ("winver_PRODUCT_VERSION_N4"   "0")
# set ("winver_LEGAL_COPYRIGHT"      "")

#
# Specification of per-project settings:
#
# set ("winver_${projectName}_FileVersionBinary"    "0,11,0,0")
# set ("winver_${projectName}_ProductVersionBinary" "0,11,0,0")
# set ("winver_${projectName}_FileVersionString"    "0, 11, 0, 0")
# set ("winver_${projectName}_ProductVersionString" "0, 11, 0, 0")
# set ("winver_${projectName}_FileDescription"      "qpid-cpp-qpidcommon Library")
# set ("winver_${projectName}_LegalCopyright"       "")
# set ("winver_${projectName}_InternalName"         "qpidcommon")
# set ("winver_${projectName}_OriginalFilename"     "qpidcommon.dll")
# set ("winver_${projectName}_ProductName"          "Apache Qpid C++")
