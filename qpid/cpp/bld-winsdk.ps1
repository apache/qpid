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

# Filter to extract the included files from c style #include lines
# TODO: Not used yet
function extractIncludes {
  param($includedir=".", $found=@{}, $notfound=@{})
  process {
    # Put original files in list if not already there
	$file = $_.FullName
	if (!($found.Contains($file))) {
	  $found[$file] = $true;
	}
    $content = Get-Content $_
	$filebase = $_.PSParentPath
    $content | foreach {
      if ($_ -match '^\s*#include\s*([<"])([^">]*)([>"])\s*') {
	    $included=$matches[2]
		# Try to find the corresponding file in the same directory
		# as the including file then try the include dir
	    $testpathf=Join-Path $filebase $included
		$testpathi=Join-Path $includedir $included
		if (Test-Path $testpathf) {
		  $includedfile = Get-Item $testpathf
		} elseif (Test-Path $testpathi) {
	      $includedfile = Get-Item $testpathi
		} else {
		  $notfound[$included] = $file
		  continue;
		}
	    if (!($found.Contains($includedfile.FullName))) {
		  $found[$includedfile.FullName] = $file
          $includedfile
		}
	  }
    }
  }
}

function getIncludeFiles {
  param($base, $findall=$false)
  if ($findall) {
    Get-ChildItem -recurse -include *.h $base
  } else {
    foreach ($path in $input) {
	  $full=Join-Path $base $path
      if (Test-Path $full) {
	   Get-Item $full
	   }
    }
  }
}

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

$qpid_cpp_src="$qpid_src\cpp"
$install_dir="install_$([System.IO.Path]::GetRandomFileName())"
$zipfile="qpid-cpp-$ver.zip"

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

# Create a new zip
if (Test-Path $zipfile) {Remove-Item $zipfile}
&'7z' a $zipfile ".\$install_dir\*"

# Remove temporary install area
# Remove-Item -recurse $install_dir
