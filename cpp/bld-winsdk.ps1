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

# Build both Debug and Release builds so we can ship both sets of libs:
# Make RelWithDebInfo for debuggable release code.
# (Do Release after Debug so that the release executables overwrite the
# debug executables. Don't skip Debug as it creates some needed content.)
devenv qpid-cpp.sln /build "Debug|Win32" /project INSTALL
devenv qpid-cpp.sln /build "RelWithDebInfo|Win32" /project INSTALL

# Build the .NET binding
devenv .\bindings\qpid\dotnet\org.apache.qpid.messaging.sln /build "Debug|x86" /project org.apache.qpid.messaging
devenv .\bindings\qpid\dotnet\org.apache.qpid.messaging.sln /build "Debug|x86" /project org.apache.qpid.messaging.sessionreceiver

# This would be kludgy if we have only one entry as the array declaration syntax
# can't cope with just one nested array
# Target must be a directory
$move=(
	('bin/*.lib','lib'),
	('bin/boost/*.dll','bin')
)

$preserve=(
	'include/qpid/agent',
	'include/qpid/amqp_0_10',
	'include/qpid/management',
	'include/qpid/messaging',
	'include/qpid/sys/IntegerTypes.h',
	'include/qpid/sys/windows/IntegerTypes.h', 'include/qpid/sys/posix/IntegerTypes.h',
	'include/qpid/types',
	'include/qpid/CommonImportExport.h')
$remove=(
	'bin/qpidd.exe', 'bin/qpidbroker*.*',
	'bin/*PDB/qpidd.exe', 'bin/*PDB/qpidbroker*.*',
	'bin/qmfengine*.*', 'bin/qpidxarm*.*',
	'bin/*PDB/qmfengine*.*', 'bin/*PDB/qpidxarm*.*',
	'bin/boost_regex*.*',
	'bin/boost',
	'conf',
	'examples/direct',
	'examples/failover',
	'examples/fanout',
	'examples/pub-sub',
	'examples/qmf-console',
	'examples/request-response',
	'examples/tradedemo',
	'examples/old-examples.sln',
	'examples/README.*',
	'examples/verify*',
	'include',
	'plugins')

# Move some files around in the install tree
foreach ($pattern in $move) {
	$target = Join-Path $install_dir $pattern[1]
	New-Item -force -type directory $target
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
foreach ($pattern in $remove) {
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

# Install the README
Copy-Item -force -path "$qpid_cpp_src/README-winsdk.txt" -destination "$install_dir/README-winsdk.txt"

# Install the .NET binding
Copy-Item -force -path "./src/Debug/org.apache.qpid.messaging*.dll" -destination "$install_dir/bin"
Copy-Item -force -path "./src/Debug/org.apache.qpid.messaging*.pdb" -destination "$install_dir/bin/DebugPDB"

# Zip the /bin PDB files into two zip files.
# we previously arranged that the Debug pdbs go in the DebugPDB subdirectory
# and the Release pdbs go in the ReleasePDB subdirectory
&'7z' a -mx9 ".\$install_dir\bin\symbols-debug.zip" ".\$install_dir\bin\DebugPDB\*.pdb"
&'7z' a -mx9 ".\$install_dir\bin\symbols-release.zip" ".\$install_dir\bin\ReleasePDB\*.pdb"

# It would be very good to cut down on the shipped boost include files too, ideally by
# starting with the qpid files and recursively noting all boost headers actually needed


# Create a new zip for the whole kit.
# Exclude *.pdb so as not include the debug symbols twice
if (Test-Path $zipfile) {Remove-Item $zipfile}
&'7z' a $zipfile ".\$install_dir\*" -xr!*pdb

# Remove temporary install area
# Remove-Item -recurse $install_dir
