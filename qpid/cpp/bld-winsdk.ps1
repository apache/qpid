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

Set-PSDebug -strict
$ErrorActionPreference='Stop'

if ($args.length -lt 1) {
  Write-Host 'Need to specify location of qpid src tree'
  exit
}

$qpid_src=$args[0]
$ver=$args[1]
if ($ver -eq $null) {
  $qpid_version_file="$qpid_src\QPID_VERSION.txt"

  if ( !(Test-Path $qpid_version_file)) {
    Write-Host "Path doesn't seem to be a qpid src tree (no QPID_VERSION.txt)"
    exit
  }
  $ver=Get-Content $qpid_version_file
}

$randomness=[System.IO.Path]::GetRandomFileName()

$qpid_cpp_src="$qpid_src\cpp"
$install_dir="install_$randomness"
$preserve_dir="preserve_$randomness"
$zipfile="qpid-cpp-$ver.zip"

# This assumes Visual Studio 2008
cmake -G "Visual Studio 9 2008" "-DCMAKE_INSTALL_PREFIX=$install_dir" $qpid_cpp_src

# Need to build doxygen api docs separately as nothing depends on them
devenv qpid-cpp.sln /build "Release|Win32" /project docs-user-api

# Build both debug and release so we can ship both sets of libs
devenv qpid-cpp.sln /build "Release|Win32" /project INSTALL
devenv qpid-cpp.sln /build "Debug|Win32" /project INSTALL

# This is kludgy until we have more than one entry as the array declaration syntax
# can't cope with just one nested array
$move1=('bin/boost/*','bin')
$move=@(0)
$move[0]=$move1

$preserve=(
	'include/qpid/agent',
	'include/qpid/management',
	'include/qpid/messaging',
	'include/qpid/sys/IntegerTypes.h',
	'include/qpid/sys/windows/IntegerTypes.h', 'include/qpid/sys/posix/IntegerTypes.h',
	'include/qpid/types',
	'include/qpid/CommonImportExport.h')
$removable=(
	'bin/qpidd.exe', 'bin/qpidbroker*.*',
	'bin/qmfengine*.*', 'bin/qpidxarm*.*',
	'bin/boost_regex*.*', 'bin/boost*.lib',
	'bin/boost',
	'conf',
	'include',
	'plugins')

# Move some files around in the install tree
foreach ($pattern in $move) {
	$target = Join-Path $install_dir $pattern[1]
	$tparent = Split-Path -parent $target
	New-Item -force -type directory $tparent
	Move-Item -force -path "$install_dir/$($pattern[0])" -destination "$install_dir/$($pattern[1])"
}
# Copy aside the files to preserve
New-Item -path $preserve_dir -type directory
foreach ($pattern in $preserve) {
	$target = Join-Path $preserve_dir $pattern
	$tparent = Split-Path -parent $target
	New-Item -force -type directory $tparent
	Move-Item -force -path "$install_dir/$pattern" -destination "$preserve_dir/$pattern"
}
# Remove everything to remove
foreach ($pattern in $removable) {
	Remove-Item -recurse "$install_dir/$pattern"
}
# Copy back the preserved things
foreach ($pattern in $preserve) {
	$target = Join-Path $install_dir $pattern
	$tparent = Split-Path -parent $target
	New-Item -force -type directory $tparent
	Move-Item -force -path "$preserve_dir/$pattern" -destination "$install_dir/$pattern"
}
Remove-Item -recurse $preserve_dir

# It would be very good to cut down on the shipped boost include files too, ideally by
# starting with the qpid files and recursively noting all boost headers actually needed

# Create a new zip
if (Test-Path $zipfile) {Remove-Item $zipfile}
&'7z' a $zipfile ".\$install_dir\*"

# Remove temporary install area
# Remove-Item -recurse $install_dir
