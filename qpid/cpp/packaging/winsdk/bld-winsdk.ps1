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
# It builds a single SDK.zip file.
# The environment for the build has been set up externally so
# that 'devenv' runs the right version of Visual Studio (2008
# 2010 2012) and the right architecture (x86 or x64), typically:
#    call "%VS90COMNTOOLS%..\..\VC\vcvarsall.bat" x86
#    call "%VS90COMNTOOLS%..\..\VC\vcvarsall.bat" amd64
# or
#    call "%VS100COMNTOOLS%..\..\VC\vcvarsall.bat" x86
#    call "%VS100COMNTOOLS%..\..\VC\vcvarsall.bat" amd64
# or
#    call "%VS110COMNTOOLS%..\..\VC\vcvarsall.bat" x86
#    call "%VS110COMNTOOLS%..\..\VC\vcvarsall.bat" amd64
#
# On entry:
#  1. Args[0] holds the BOOST_ROOT directory.            "c:\boost"
#  2. Args[1] holds the version number.                  "2.0.0.1"
#     This arg/version number is used for output package naming purposes.
#     The version number embedded in the built executables and libraries
#     comes from qpid/cpp/src/CMakeWinVersions.cmake.
#  3. Args[2] holds the Visual Studio version handle     "VS2010"
#     Pick VS2008, VS2010, or VS2012. Defaults to VS2008.
#  4. Args[3] holds the architecture handle              "x86"
#     Either x86 or x64. Defaults to x86.
#  4a. Args[4] optionally holds relative path to proton  "..\..\proton\install"
#      install root from the directory in which cmake
#      is run. If this arg is specified then the
#      shared directory is used. If this arg is blank
#      or absent then the kit is built without proton.
#      The path is relative to <kitroot>\<arch>-<vSversion> described below.
#  5. This file exists in directory kitroot/qpid/cpp.
#     The kit is built in a directory <kitroot>\<arch>-<VSversion>.
#     For example: <kitroot>\x86-VS2008
#  6. The <arch>-<VSversion> dirs are where cmake will run.
#  7. Boost must have been built with the same version of Visual Studio 
#     and the same architecture as this build.
#  8. Boost directories must not be on the path.
#  9. cmake, 7z, and devenv are already on the path.
#
# This script creates a separate zip kit for 32-bit or
# for 64-bit variants. Example output files:
#   qpid-cpp-x86-VS2008-2.0.0.1.zip
#   qpid-cpp-x64-VS2008-2.0.0.1.zip
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
[string] $global:vsArch             = "x86"
[string] $global:sharedBuildDir     = ""


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
        [string] $vsName,
        [string] $sharedInstallDir
    )

    [string] $install_dir   = $sharedInstallDir
    if ($install_dir -eq "") {
        # If proton shared install not specified use some local random dir
        $install_dir = "install_$randomness"
    }
    [string] $preserve_dir  = "preserve_$randomness"
    [string] $zipfile       = "qpid-cpp-$platform-$vsName-$ver.zip"
    [string] $platform_dir  = "$global:currentDirectory/$platform-$vsName"
    [string] $qpid_cpp_src  = "$global:currentDirectory/$qpid_cpp_dir"
    [string] $msvcVer       = ""
    [string] $msvcVerX      = ""
    
    Write-Host "BuildAPlatform"
    Write-Host " qpid_cpp_dir   : $qpid_cpp_dir"
    Write-Host " platform       : $platform"
    Write-Host " cmakeGenerator : $cmakeGenerator"
    Write-Host " vsTargetDebug  : $vsTargetDebug"
    Write-Host " vsTargetRelease: $vsTargetRelease"
    Write-Host " boostRoot      : $boostRoot"
    Write-Host " randomness     : $randomness"
    Write-Host " vsName         : $vsName"
    Write-Host " installDir     : $installDir"
    
    #
    # Compute msvcVer string from the given vsName
    #
    if ($vsName -eq "VS2008") {
        $msvcVer = "msvc9"
        $msvcVerX = "msvc9"
    } else {
        if ($vsName -eq "VS2010") {
            $msvcVer = "msvc10"
            $msvcVerX = "msvcx"
        } else {
            if ($vsName -eq "VS2012") {
                $msvcVer = "msvc11"
                $msvcVerX = "msvcx"
            } else {
                if ($vsName -eq "VS2013") {
                    $msvcVer = "msvc12"
                    $msvcVerX = "msvcx"
                } else {
                    Write-Host "illegal vsName parameter: $vsName Choose VS2008, VS2010, VS2012, or VS2013"
                    exit
                }
            }
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

    $env:BOOST_ROOT      = "$boostRoot"
    $env:QPID_BUILD_ROOT = Get-Location

    #
    # Run cmake
    #
    Write-Host "Running cmake:  cmake -G ""$cmakeGenerator"" ""-DCMAKE_INSTALL_PREFIX=$install_dir"" $qpid_cpp_src"
    Write-Host "From directory: " $(Get-Location)
    cmake -G "$cmakeGenerator" "-DCMAKE_INSTALL_PREFIX=$install_dir" $qpid_cpp_src

    #
    # Need to build doxygen api docs separately as nothing depends on them.
    # Build for both x86 and x64 or cmake_install fails.
    if ("x86" -eq $platform) {
        devenv qpid-cpp.sln /build "Release|Win32"     /project docs-user-api
    } else {
        devenv qpid-cpp.sln /build "Release|$platform" /project docs-user-api
    }

    # Build both Debug and Release builds so we can ship both sets of libs:
    # Make RelWithDebInfo for debuggable release code.
    # (Do Release after Debug so that the release executables overwrite the
    # debug executables. Don't skip Debug as it creates some needed content.)
    devenv qpid-cpp.sln /build "$vsTargetDebug"   /project INSTALL
    devenv qpid-cpp.sln /build "$vsTargetRelease" /project INSTALL

    $bindingSln = Resolve-Path $platform_dir\bindings\qpid\dotnet\$msvcVerX\org.apache.qpid.messaging.sln
    
    # Build the .NET binding
    if ("x86" -eq $platform) {
        devenv $bindingSln /build "Debug|Win32"              /project org.apache.qpid.messaging
        devenv $bindingSln /build "Debug|$platform"          /project org.apache.qpid.messaging.sessionreceiver
        devenv $bindingSln /build "RelWithDebInfo|Win32"     /project org.apache.qpid.messaging
        devenv $bindingSln /build "RelWithDebInfo|$platform" /project org.apache.qpid.messaging.sessionreceiver
    } else {
        devenv $bindingSln /build "Debug|$platform"          /project org.apache.qpid.messaging
        devenv $bindingSln /build "Debug|$platform"          /project org.apache.qpid.messaging.sessionreceiver
        devenv $bindingSln /build "RelWithDebInfo|$platform" /project org.apache.qpid.messaging
        devenv $bindingSln /build "RelWithDebInfo|$platform" /project org.apache.qpid.messaging.sessionreceiver
    }

    # Create install Debug and Release directories
    New-Item $(Join-Path $install_dir "bin\Debug"  ) -type Directory | Out-Null
    New-Item $(Join-Path $install_dir "bin\Release") -type Directory | Out-Null
    
    # Define lists of items to be touched in installation tree
    # Move target must be a directory
    $move=(
        ('bin/*.lib',            'lib'),
        ('bin/*-gd-*.dll',       'bin/Debug'),
        ('bin/boost*.dll',       'bin/Release'),
        ('bin/Microsoft*',       'bin/Release'),
        ('bin/msvc*d.dll',       'bin/Debug'),
        ('bin/msvc*.dll',        'bin/Release') ,
        ('bin/*d.dll',           'bin/Debug'),
        ('bin/*.dll',            'bin/Release'),
        ('bin/*test.exe',        'bin/Release'),
        ('bin/qpid-send.exe',    'bin/Release'),
        ('bin/qpid-receive.exe', 'bin/Release')
    )

    $preserve=(
        'include/qpid/messaging',
        'include/qpid/sys/IntegerTypes.h',
        'include/qpid/sys/windows/IntegerTypes.h',
        'include/qpid/sys/posix/IntegerTypes.h',
        'include/qpid/types',
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
        'bin/*.exe',
        'bin/qmf-gen',
        'bin/Debug/msvc*',
        'conf',
        'examples/*.sln',
        'examples/*.vcproj',
        'examples/messaging/*.vcproj',
        'include',
        'plugins')

    $removeProtonShared=(
        'lib/cmake',
        'lib/pkgconfig',
        'proton',
        'share')

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
    if ($sharedInstallDir -ne "") {
        foreach ($pattern in $removeProtonShared) {
            $target = Join-Path $install_dir $pattern
            if (Test-Path -path $target) {
                Remove-Item -recurse $target
            }
        }
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
    Unix2Dos "$install_dir/docs/LICENSE"
    Unix2Dos "$install_dir/docs/NOTICE"
    Unix2Dos "$install_dir/examples/README.txt"

    # Install the .NET binding example source code
    New-Item -path $(Join-Path $(Get-Location) $install_dir)                 -name dotnet_examples -type directory
    New-Item -path $(Join-Path $(Get-Location) $install_dir/dotnet_examples) -name        examples -type directory

    $src = Resolve-Path "$qpid_cpp_src/bindings/qpid/dotnet/examples"
    $dst = Resolve-Path "$install_dir/dotnet_examples"
    Copy-Item "$src\" -destination "$dst\" -recurse -force

    Get-ChildItem * -include *.csv -recurse | remove-item
    cmd /c "rd /s /q ""$install_dir/dotnet_examples/examples/msvc9"""
    cmd /c "rd /s /q ""$install_dir/dotnet_examples/examples/msvc10"""
    
    # TODO: Fix up the .NET binding example solution/projects before including them.
    $src = Resolve-Path "$platform_dir/bindings/qpid/dotnet/winsdk_sources/$msvcVerX"
    $dst = Resolve-Path "$install_dir/dotnet_examples"
    Copy-Item "$src\*" -destination "$dst\" -recurse -force

    # For the C++ examples: install a CMakeLists.txt file so customers can build
    # their own Visual Studio solutions and projects.
    New-Item $(Join-Path $install_dir "examples\examples-cmake") -type Directory | Out-Null
    $src = Resolve-Path "$global:sourceDirectory/cpp/examples/winsdk-cmake"
    $dst = Join-Path $install_dir "examples\examples-cmake"
    Copy-Item "$src\*" -destination "$dst\"

    # Create a batch file that will run examples-cmake with the correct generator
    $dst = Join-Path $install_dir "examples\examples-cmake\run-cmake.bat"
    "REM"                                                       | Out-File -filepath $dst -encoding ASCII
    "REM  run-cmake.bat"                                        | Out-File -filepath $dst -encoding ASCII -append
    "REM"                                                       | Out-File -filepath $dst -encoding ASCII -append
    "REM  Runs cmake to build native C++ example solution and"  | Out-File -filepath $dst -encoding ASCII -append
    "REM  projects for this WinSDK: $platform $vsName"          | Out-File -filepath $dst -encoding ASCII -append
    "REM"                                                       | Out-File -filepath $dst -encoding ASCII -append
    "cmake -G ""$cmakeGenerator"" ."                            | Out-File -filepath $dst -encoding ASCII -append
    
    # Zip the /bin PDB files
    &'7z' a -mx9 ".\$install_dir\bin\Debug\symbols-debug.zip"     ".\$install_dir\lib\DebugPDB\*.pdb"
    &'7z' a -mx9 ".\$install_dir\bin\Release\symbols-release.zip" ".\$install_dir\lib\ReleasePDB\*.pdb"
    Remove-Item -recurse ".\$install_dir\lib\DebugPDB"
    Remove-Item -recurse ".\$install_dir\lib\ReleasePDB"

    # Copy the dotnet bindings
    Copy-Item -force -path "./src/Debug/org.apache.qpid.messaging*.dll"          -destination "$install_dir/bin/Debug/"
    Copy-Item -force -path "./src/Debug/org.apache.qpid.messaging*.pdb"          -destination "$install_dir/bin/Debug/"

    Copy-Item -force -path "./src/RelWithDebInfo/org.apache.qpid.messaging*.dll" -destination "$install_dir/bin/Release/"
    Copy-Item -force -path "./src/RelWithDebInfo/org.apache.qpid.messaging*.pdb" -destination "$install_dir/bin/Release/"

    # Create a new zip for the whole kit.
    if (Test-Path $zipfile) {Remove-Item $zipfile}
    &'7z' a $zipfile ".\$install_dir\*"
}

################################
#
# Main()
#
# Process the args
#

if ($args.length -lt 3) {
    Write-Host 'Usage: bld-winsdk.ps1 boost_root  buildVersion [VisualStudioVersion [architecture [relative-path-to-proton-install-dir]]]'
    Write-Host '       bld-winsdk.ps1 d:\boost-32 1.2.3.4       VS2008               x86           ..\..\git-proton\install'
    exit
}

$qpid_src    = Split-Path -leaf $global:sourceDirectory
$boostRoot   = $args[0]
$ver         = $args[1]
$generator = ""
$global:vsVersion = $args[2]
if ( !($global:vsVersion -eq $null) ) {
    if ($global:vsVersion -eq "VS2008") {
        $generator = "Visual Studio 9 2008"
    } else {
        if ($global:vsVersion -eq "VS2010") {
            $generator = "Visual Studio 10"
        } else {
            if ($global:vsVersion -eq "VS2012") {
                $generator = "Visual Studio 11"
            } else {
                if ($global:vsVersion -eq "VS2013") {
                    $generator = "Visual Studio 12"
                } else {
                    Write-Host "Visual Studio Version must be VS2008, VS2010, VS2012, or VS2013"
                    exit
                }
            }
        }
    }
} else {
    # default generator
    $global:vsVersion = "VS2008"
    $generator = "Visual Studio 9 2008"
}

$global:vsArch = $args[3]
if ( !($global:vsArch -eq $null) ) {
    if ($global:vsArch -eq "x86") {
    } else {
        if ($global:vsArch -eq "x64") {
        } else {
            Write-Host "Architecture must be x86 or x64"
            exit
        }
    }
} else {
    # default architecture
    $global:vsArch = "x86"
}

$global:sharedBuildDir = $args[4]
if ($global:sharedBuildDir -eq $null) {
    $global:sharedBuildDir = ""
}

Write-Host "bld-winsdk.ps1"
Write-Host " qpid_src    : $qpid_src"
Write-Host " boostRoot   : $boostRoot"
Write-Host " ver         : $ver"
Write-Host " cmake gene  : $generator"
Write-Host " vsVersion   : $global:vsVersion"
Write-Host " vsArch      : $global:vsArch"
Write-Host " sharedBuild : $global:sharedBuildDir"

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
if ($global:vsArch -eq "x86") {
    BuildAPlatform $qpid_cpp_src `
                   "x86" `
                   "$generator" `
                   "Debug|Win32" `
                   "RelWithDebInfo|Win32" `
                   $boostRoot `
                   $randomness `
                   $global:vsVersion `
                   $global:sharedBuildDir
} else {
    BuildAPlatform $qpid_cpp_src `
                   "x64" `
                   "$generator Win64" `
                   "Debug|x64" `
                   "RelWithDebInfo|x64" `
                   $boostRoot `
                   $randomness `
                   $global:vsVersion `
                   $global:sharedBuildDir
}