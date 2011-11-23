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

# This script builds a WinSDK from a Qpid source checkout that
# has been cleaned of any SVN artifacts.
#
# On entry:
#  1. Args[0] holds the relative path to Qpid/trunk.
#       Directory ".\$args[0]" holds the "cpp" directory and
#       file QPID_VERSION.txt.
#  2. Args[1] holds the x86 32-bit BOOST_ROOT.           "c:\boost"
#  3. Args[2] holds the x64 64-bit BOOST_ROOT.           "c:\boost_x64"
#  4. Args[3] holds the optional version number.         "0.7.946106-99"
#     Defaults to contents of QPID_VERSION.txt
#  5. Args[4] holds the optional Visual Studio version   "VS2010"
#     Defaults to VS2008.
#  6. The current directory will receive x86 and x64 subdirs.
#  7. The x86 an x64 dirs are where cmake will run.
#  8. Two Boost installations, 32- and 64-bit, are available.
#     Boost was built with the same version of Visual Studio as this build.
#  9. Boost directories must not be on the path.
# 10. cmake, 7z, and devenv are already on the path.
# 11. devenv is Visual Studio 2008 or 2010 as set by Args[4].
#     The PATH, INCLUDE, LIBPATH, and LIB environment vars are
#     set by calling vcvarsall.bat and then running "devenv /useenv".
#
# This script creates separate zip kits for 32- and
# for 64-bit variants. Example output files:
#   qpid-cpp-x86-VS2008-1.3.0.12.zip
#   qpid-cpp-x64-VS2008-1.3.0.12.zip
#

# Avoid "The OS handle's position is not what FileStream expected" errors
# that crop up when powershell's and spawned subprocesses' std streams are merged.
# fix from: http://www.leeholmes.com/blog/2008/07/30/workaround-the-os-handles-position-is-not-what-filestream-expected/
$flagsFld      = [Reflection.BindingFlags] "Instance,NonPublic,GetField"
$flagsProp     = [Reflection.BindingFlags] "Instance,NonPublic,GetProperty"
$objectRef     = $host.GetType().GetField("externalHostRef", $flagsFld).GetValue($host)
$consoleHost   = $objectRef.GetType().GetProperty("Value", $flagsProp).GetValue($objectRef, @())
[void] $consoleHost.GetType().GetProperty("IsStandardOutputRedirected", $flagsProp).GetValue($consoleHost, @())
$field         = $consoleHost.GetType().GetField("standardOutputWriter", $flagsFld)
$field.SetValue( $consoleHost, [Console]::Out)
$field2        = $consoleHost.GetType().GetField("standardErrorWriter", $flagsFld)
$field2.SetValue($consoleHost, [Console]::Out)

Set-PSDebug -Trace 1
Set-PSDebug -strict
$ErrorActionPreference='Stop'

################################
#
# Global variables
#
[string] $global:bldwinsdkDirectory = Split-Path -parent $MyInvocation.MyCommand.Definition
[string] $global:sourceDirectory    = Split-Path -parent $global:bldwinsdkDirectory
[string] $global:currentDirectory   = Split-Path -parent $global:sourceDirectory
[string] $global:vsVersion          = "VS2008"


################################
#
# Unix2Dos
#   Change text file to DOS line endings
#
function Unix2Dos
{
    param
    (
        [string] $fname
    )

    $fContent = Get-Content $fname
    $fContent | Set-Content $fname
}


################################
#
# SetVS2008
#   Set environment for VS2008
#
function SetVS2008($varsallArg)
{
    $vs90comntools = (Get-ChildItem env:VS90COMNTOOLS).Value
    $batchFile = [System.IO.Path]::Combine($vs90comntools, "..\..\VC\vcvarsall.bat")
    $batchFile = Resolve-Path $batchFile
    GetBatchfilesEnvironment "$BatchFile" $varsallArg
}


################################
#
# SetVS2010
#   Set environment for VS2010
#
function SetVS2010($varsallArg)
{
    $vs100comntools = (Get-ChildItem env:VS100COMNTOOLS).Value
    $batchFile = [System.IO.Path]::Combine($vs100comntools, "..\..\VC\vcvarsall.bat")
    $batchFile = Resolve-Path $batchFile
    GetBatchfilesEnvironment $BatchFile $varsallArg
}
 
################################
#
# GetBatchfilesEnvironment
#   Run a batch file specifically to change environment variables.
#   Then propagate those variables to this process.
#
function GetBatchfilesEnvironment($file, $varsallArg)
{
    $exe = "cmd"
    [Array]$params = "/c", """$file""", $varsallArg, "&", "set";
    Write-Host "Select Visual Studio command line environment: $exe $params"
    & $exe $params | Foreach-Object {
        $p, $v = $_.split('=')
        Set-Item -path env:$p -value $v
    }
}


################################
#
# BuildAPlatform
#   Build a platform, x86 or x64.
#   Compiles and packages Debug and RelWithDebInfo configurations.
#
#   Typical invocation:
#   BuildAPlatform C:\qpid\cpp x86 "Visual Studio 9 2008" "Debug|Win32" `
#                  "RelWithDebInfo|Win32" D:\boost 01234567 VS2008
#
function BuildAPlatform
{
    param
    (
        [string] $qpid_cpp_dir,
        [string] $platform,
        [string] $cmakeGenerator,
        [string] $vsTargetDebug,
        [string] $vsTargetRelease,
        [string] $boostRoot,
        [string] $randomness,
        [string] $vsName
    )

    [string] $install_dir   = "install_$randomness"
    [string] $preserve_dir  = "preserve_$randomness"
    [string] $zipfile       = "qpid-cpp-$platform-$vsName-$ver.zip"
    [string] $platform_dir  = "$global:currentDirectory/$platform"
    [string] $qpid_cpp_src  = "$global:currentDirectory/$qpid_cpp_dir"
    [string] $msvcVer       = ""
    
    Write-Host "BuildAPlatform"
    Write-Host " qpid_cpp_dir   : $qpid_cpp_dir"
    Write-Host " platform       : $platform"
    Write-Host " cmakeGenerator : $cmakeGenerator"
    Write-Host " vsTargetDebug  : $vsTargetDebug"
    Write-Host " vsTargetRelease: $vsTargetRelease"
    Write-Host " boostRoot      : $boostRoot"
    Write-Host " randomness     : $randomness"
    Write-Host " vsName         : $vsName"
    
    #
    # Compute msvcVer string from the given vsName
    #
    if ($vsName -eq "VS2008") {
        $msvcVer = "msvc9"
    } else {
        if ($vsName -eq "VS2010") {
            $msvcVer = "msvc10"
        } else {
            Write-Host "illegal vsName parameter: $vsName"
            exit
        }
    }
    
    #
    # Create the platform directory if necessary
    #
    if (!(Test-Path -path $platform_dir))
    {
        New-Item $platform_dir -type Directory | Out-Null
    }

    #
    # Descend into platform directory
    #
    Set-Location $platform_dir

    #
    # Set environment for this build
    #
    # set up the vcvarsall arg for a native/cross compiler command line 
    if ($platform -eq "x86") {
        $varsallArg = "x86"
    } else {
        $os=Get-WMIObject win32_operatingsystem
        if ($os.OSArchitecture -eq "64-bit") {
            $varsallArg = "amd64"
        } else {
            $varsallArg = "x86_amd64"
        }
    }
    if ($vsName -eq "VS2008") {
        SetVS2008 $varsallArg
    } else {
        SetVS2010 $varsallArg
    }

    $env:BOOST_ROOT      = "$boostRoot"
    $env:QPID_BUILD_ROOT = Get-Location

    #
    # Run cmake
    #
    cmake -G "$cmakeGenerator" "-DCMAKE_INSTALL_PREFIX=$install_dir" $qpid_cpp_src

    #
    # Need to build doxygen api docs separately as nothing depends on them.
    # Build for both x86 and x64 or cmake_install fails.
    if ("x86" -eq $platform) {
        devenv /useenv qpid-cpp.sln /build "Release|Win32"     /project docs-user-api
    } else {
        devenv /useenv qpid-cpp.sln /build "Release|$platform" /project docs-user-api
    }

    # Build both Debug and Release builds so we can ship both sets of libs:
    # Make RelWithDebInfo for debuggable release code.
    # (Do Release after Debug so that the release executables overwrite the
    # debug executables. Don't skip Debug as it creates some needed content.)
    devenv /useenv qpid-cpp.sln /build "$vsTargetDebug"   /project INSTALL
    devenv /useenv qpid-cpp.sln /build "$vsTargetRelease" /project INSTALL

    $bindingSln = Resolve-Path $qpid_cpp_src\bindings\qpid\dotnet\$msvcVer\org.apache.qpid.messaging.sln
    
    # Build the .NET binding
    if ("x86" -eq $platform) {
        devenv /useenv $bindingSln /build "Debug|Win32"              /project org.apache.qpid.messaging
        devenv /useenv $bindingSln /build "Debug|$platform"          /project org.apache.qpid.messaging.sessionreceiver
        devenv /useenv $bindingSln /build "RelWithDebInfo|Win32"     /project org.apache.qpid.messaging
        devenv /useenv $bindingSln /build "RelWithDebInfo|$platform" /project org.apache.qpid.messaging.sessionreceiver
    } else {
        devenv /useenv $bindingSln /build "Debug|$platform"          /project org.apache.qpid.messaging
        devenv /useenv $bindingSln /build "Debug|$platform"          /project org.apache.qpid.messaging.sessionreceiver
        devenv /useenv $bindingSln /build "RelWithDebInfo|$platform" /project org.apache.qpid.messaging
        devenv /useenv $bindingSln /build "RelWithDebInfo|$platform" /project org.apache.qpid.messaging.sessionreceiver
    }

    # Create install Debug and Release directories
    New-Item $(Join-Path $install_dir "bin\Debug"  ) -type Directory | Out-Null
    New-Item $(Join-Path $install_dir "bin\Release") -type Directory | Out-Null
    
    # Define lists of items to be touched in installation tree
    # Move target must be a directory
    $move=(
        ('bin/*.lib',            'lib'),
        ('bin/boost/*-gd-*.dll', 'bin/Debug'),
        ('bin/boost/*.dll',      'bin/Release'),
        ('bin/Microsoft*',       'bin/Release'),
        ('bin/msvc*.dll',        'bin/Release') ,
        ('bin/*d.dll',           'bin/Debug'),
        ('bin/*.dll',            'bin/Release'),
        ('bin/*test.exe',        'bin/Debug')
    )

    $preserve=(
        'include/qpid/agent',
        'include/qpid/amqp_0_10',
        'include/qpid/management',
        'include/qpid/messaging',
        'include/qpid/sys/IntegerTypes.h',
        'include/qpid/sys/windows/IntegerTypes.h',
        'include/qpid/sys/posix/IntegerTypes.h',
        'include/qpid/types',
        'include/qpid/CommonImportExport.h',
        'include/qpid/ImportExport.h')

    $remove=(
        'bin/qpidd.exe',
        'bin/qpidbroker*.*',
        'bin/*PDB/qpidd.exe',
        'bin/*PDB/qpidbroker*.*',
        'bin/qmfengine*.*',
        'bin/qpidxarm*.*',
        'bin/*PDB/qmfengine*.*',
        'bin/*PDB/qpidxarm*.*',
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

    # Set top level info files to DOS line endings
    Unix2Dos "$install_dir/README-winsdk.txt"
    Unix2Dos "$install_dir/LICENSE"
    Unix2Dos "$install_dir/NOTICE"

    # Install the .NET binding examples
    New-Item -path $(Join-Path $(Get-Location) $install_dir)                 -name dotnet_examples -type directory
    New-Item -path $(Join-Path $(Get-Location) $install_dir/dotnet_examples) -name        examples -type directory

    $src = Resolve-Path "$qpid_cpp_src/bindings/qpid/dotnet/examples"
    $dst = Resolve-Path "$install_dir/dotnet_examples"
    Copy-Item "$src\" -destination "$dst\" -recurse -force

    $src = Resolve-Path "$qpid_cpp_src/bindings/qpid/dotnet/winsdk_sources/$msvcVer"
    $dst = Resolve-Path "$install_dir/dotnet_examples"
    Copy-Item "$src\*" -destination "$dst\" -recurse -force

    # Construct the examples' msvc-versioned solution/projects
    New-Item $(Join-Path $install_dir "examples\msvc") -type Directory | Out-Null
    Push-Location $(Join-Path $install_dir "examples\msvc")
        cmake -G $cmakeGenerator $(Resolve-Path "$qpid_cpp_src/examples/winsdk-cmake")
    Pop-Location
    
    # Zip the /bin PDB files
    &'7z' a -mx9 ".\$install_dir\bin\Debug\symbols-debug.zip"     ".\$install_dir\bin\DebugPDB\*.pdb"
    &'7z' a -mx9 ".\$install_dir\bin\Release\symbols-release.zip" ".\$install_dir\bin\ReleasePDB\*.pdb"
    Remove-Item -recurse ".\$install_dir\bin\DebugPDB"
    Remove-Item -recurse ".\$install_dir\bin\ReleasePDB"

    # Copy the dotnet bindings
    Copy-Item -force -path "./src/Debug/org.apache.qpid.messaging*.dll"          -destination "$install_dir/bin/Debug/"
    Copy-Item -force -path "./src/Debug/org.apache.qpid.messaging*.pdb"          -destination "$install_dir/bin/Debug/"

    Copy-Item -force -path "./src/RelWithDebInfo/org.apache.qpid.messaging*.dll" -destination "$install_dir/bin/Release/"
    Copy-Item -force -path "./src/RelWithDebInfo/org.apache.qpid.messaging*.pdb" -destination "$install_dir/bin/Release/"

    # Create a new zip for the whole kit.
    # Exclude *.pdb so as not include the debug symbols twice
    if (Test-Path $zipfile) {Remove-Item $zipfile}
    &'7z' a $zipfile ".\$install_dir\*" -xr!*pdb
}

################################
#
# Main()
#
# Process the args
#
if ($args.length -lt 3) {
    Write-Host 'Usage: bld-winsdk.ps1 qpid_src_dir boost32_dir boost64_dir [buildVersion [Visual Studio Version]]'
    Write-Host '       bld-winsdk.ps1 qpid         d:\boost-32 d:\boost-64  1.2.3.4       VS2008'
    exit
}

$qpid_src    = $args[0]
$boostRoot32 = $args[1]
$boostRoot64 = $args[2]
$ver         = $args[3]
if ($ver -eq $null) {
  $qpid_version_file="$qpid_src\QPID_VERSION.txt"

  if ( !(Test-Path $qpid_version_file)) {
    Write-Host "Path doesn't seem to be a qpid src tree (no QPID_VERSION.txt)"
    exit
  }
  $ver=Get-Content $qpid_version_file
}

$generator = ""
$global:vsVersion = $args[4]
if ( !($global:vsVersion -eq $null) ) {
    if ($global:vsVersion -eq "VS2008") {
        $generator = "Visual Studio 9 2008"
    } else {
        if ($global:vsVersion -eq "VS2010") {
            $generator = "Visual Studio 10"
        } else {
            Write-Host "Visual Studio Version must be VS2008 or VS2010"
            exit
        }
    }
} else {
    # default generator
    $global:vsVersion = "VS2008"
    $generator = "Visual Studio 9 2008"
}
Write-Host "bld-winsdk.ps1"
Write-Host " qpid_src    : $qpid_src"
Write-Host " boostRoot32 : $boostRoot32"
Write-Host " boostRoot64 : $boostRoot64"
Write-Host " ver         : $ver"
Write-Host " cmake gene  : $generator"
Write-Host " vsVersion   : $global:vsVersion"

#
# Verify that Boost is not in PATH
#
[string] $oldPath = $env:PATH
$oldPath = $oldPath.ToLower()
if ($oldPath.Contains("boost"))
{
    Write-Host "This script will not work with BOOST defined in the path environment variable."
    Exit
}


$randomness=[System.IO.Path]::GetRandomFileName()
$qpid_cpp_src="$qpid_src\cpp"

#
# buid
#
BuildAPlatform $qpid_cpp_src `
               "x64" `
               "$generator Win64" `
               "Debug|x64" `
               "RelWithDebInfo|x64" `
               $boostRoot64 `
               $randomness `
               $global:vsVersion

BuildAPlatform $qpid_cpp_src `
               "x86" `
               "$generator" `
               "Debug|Win32" `
               "RelWithDebInfo|Win32" `
               $boostRoot32 `
               $randomness `
               $global:vsVersion
