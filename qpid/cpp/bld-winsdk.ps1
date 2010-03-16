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

# This script requires cmake, and 7z to be already on the path devenv should be on the path as
# a result of installing Visual Studio

foreach ($arg in $args) {"Arg: $arg"}

$qpid_cpp_src='..\qpid\cpp'
$install_dir='install_dir'
$ver='0.6'
$zipfile="qpid-cpp-$ver.zip"

# Clean out install directory
Remove-Item -recurse $install_dir

# This assumes Visual Studio 2008
cmake -G "Visual Studio 9 2008" "-DCMAKE_INSTALL_PREFIX=$install_dir" $qpid_cpp_src

# Need to build doxygen api docs separately as nothing depends on them
devenv qpid-cpp.sln /build "Release|Win32" /project docs-user-api

# Build both debug and release so we can ship both sets of libs
devenv qpid-cpp.sln /build "Release|Win32" /project INSTALL
devenv qpid-cpp.sln /build "Debug|Win32" /project INSTALL

# Cut down the files to put in the zip
$removable=@(
	'bin/qpidd.exe', 'bin/qpidbroker*.*', 'plugins',
	'bin/qmfengine*.*', 'bin/qpidxarm*.*',
	'bin/boost_regex*.*')
foreach ($pattern in $removable) {
	Remove-Item -recurse "$install_dir/$pattern"
}

# It would be very good to cut down on the shipped boost include files too, ideally by
# starting with the qpid files and recursively noting all boost headers actually needed

# Createza new zip
Remove-Item $zipfile
&'7z' a $zipfile ".\$install_dir\*"
