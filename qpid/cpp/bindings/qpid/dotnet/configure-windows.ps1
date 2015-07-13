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

Set-PSDebug -Trace 0
Set-PSDebug -strict
$ErrorActionPreference='Stop'

$global:usageText = @("#
# configure-windows.ps1 [studio-and-architecture [boost-root-directory]]
#
# This script configures a qpid\cpp developer build environment under Windows
# to enable working with cpp\bindings\qpid\dotnet binding source code.
#
# ARG PROCESSING
# ==============
# All arguments may be specified on the command line. If not then this
# script will prompt the user for choices.
#
#   * studio-and-architecture
#     User chooses a version of Visual Studio and a target platform:
#       2012-x86
#       2012-x64
#       2010-x86
#       2010-x64
#       2008-x86
#       2008-x64 
#
#   * boost-root-directory
#     The path to the version of boost to be used in this build
#
# CONFIGURATION OUTPUT
# ====================
# This script uses these build and install directories for the build:
#       build_2008_x86  install_2008_x86
#       build_2010_x86  install_2010_x86
#       build_2008_x64  install_2008_x64
#       build_2010_x64  install_2010_x64
#
#
# PROTON AMQP 1.0
# ===============
# Proton is included automatically by having previously executed proton's
# ""make install"" to the install_xx_xxxx directory that qpid is about to use.
#
# Prerequisites
# =============
#  1. Powershell must be installed.
#  2. 32-bit and/or 64-bit Boost libraries must be installed in separate
#     directories. A user system may have any number of Boost library
#     versions installed on it as long as each may be referred to through
#     a separate BOOST_ROOT directory path.
#  3. CMake 2.8 (or later) must be installed. The cmake\bin directory
#     must be in the user's path.
#  4. Boost library specifications may or may not be in the user's path.
#     The script author recommends not to have Boost in the path and only
#     allow the Boost path to be specified by generated command procedures.
#  5. Visual Studio build environment must be installed.
#
#
# Use case: Create a new build environment
# ========================================
#
#  Required VS2010 Boost:
#        32-bit library - C:\Boost
#        64-bit library - D:\Boost_64
#
#  Required Qpid checkout tree
#        C:\svn\qpid\...
#
#  Run this script, select VS2010 compiler, x86 platform:
#
#    configure-windows.ps1 2010-x86 C:\Boost
#
#  This script will automatically create build directory
#        C:\svn\qpid\build_2010_x86
#
#  Next this script runs CMake.
#
#  * This step creates qpid-cpp.sln and related project files.
#      C:\svn\qpid\build_2010_x86\qpid-cpp.sln
#
#  This script generates several other helper scripts:
#
#   C:\svn\qpid\build_2010_x86\start-devenv-messaging-x86-32bit.ps1
#   C:\svn\qpid\build_2010_x86\start-devenv-messaging-x86-32bit.bat
#   C:\svn\qpid\build_2010_x86\setenv-messaging-x86-32bit.bat
#   C:\svn\qpid\build_2010_x86\run-cmake.bat
#   C:\svn\qpid\build_2010_x86\run-qpid-devenv-debug.bat
")

#############################
# global strings to be written to script files
#
$global:txtPath = '$env:PATH'
$global:txtQR   = '$env:QPID_BUILD_ROOT'
$global:txtWH   = 'Write-Host'

#############################
# Visual Studio version selection dialog items and choice
#
[array]$global:VsVersionCmakeChoiceList = `
    "Visual Studio 2013 - x86", `
    "Visual Studio 2013 - x64", `
    "Visual Studio 2012 - x86", `
    "Visual Studio 2012 - x64", `
    "Visual Studio 2010 - x86", `
    "Visual Studio 2010 - x64", `
    "Visual Studio 2008 - x86", `
    "Visual Studio 2008 - x64"
$global:vsSelectedOption = ''
$global:vsVersion = ''       # "Visual Studio 2010"
$global:vsShortName = ''     # "2010"
$global:cmakeGenerator = ''  # "Visual Studio 10"
$global:vsSubdir = ''        # "msvc10"
$global:vsSubdirX = ''       # "msvc9" or "msvcx"
$global:cmakeCompiler = ''   # "-vc100"
$global:build32or64 = ''     # "32" or "64"
$global:buildPathSizeId = '' # "x86" or "x64"
$global:vsEnvironment = ''   # ""%VS100COMNTOOLS%..\..\vcvarsall.bat" x86"
$global:vsBuildTarget = ''   # "Debug|Win32"

$global:cmakeCommandLine = ''

$global:boostRootPath = ''

#############################
# Usage
#
function Usage
{
    Write-Host $global:usageText
}

#############################
# Select-Folder
#   Return a folder or null
#
function Select-Folder ($message="Select a folder", $path=0)
{
    $shellApp = New-Object -comObject Shell.Application
    $folder = $shellApp.BrowseForFolder(0, $message, 0, $path)
    if ($folder -ne $null) {
        $folder.self.Path
    }
}


#############################
# AskYesOrNo
#   Show modal dialog messagebox and return yes or no
#
function AskYesOrNo ($Question="No question?", $Title="No Title?")
{
    $dlg = [Windows.Forms.MessageBox]::Show($Question, $Title,  `
           [Windows.Forms.MessageBoxButtons]::YesNo, `
           [Windows.Forms.MessageBoxIcon]::Question)

    $result = $dlg -eq [Windows.Forms.DialogResult]::Yes

    $result
}


#############################
# SanityCheckBoostPath
#   A path is a "boost path" if it contains
#   both lib and include subdirectories.
#
function SanityCheckBoostPath ($path=0)
{
    $result = 1
    $displayPath = ""

    if ($path -ne $null) {
        $displayPath = $path

        $toTest = ('lib')
        foreach ($pattern in $toTest) {
            $target = Join-Path $path $pattern
            if (!(Test-Path -path $target)) {
                $result = 0
            }
        }
    } else {
        $result = 0
    }

    if (! $result) {
        Write-Host "The path ""$displayPath"" does not appear to be a Boost root path."
    }
    $result
}


#############################
# SanityCheckBuildPath
#   A path is a "build path" if it contains
#   various subdirectories.
#
function SanityCheckBuildPath ($path=0)
{
    $result = 1
    $displayPath = ""
    if ($path -ne $null) {
        $displayPath = $path

        $toTest = ('CMakeFiles', 'docs', 'etc', 'examples', 'include',
                   'managementgen', 'src')
        foreach ($pattern in $toTest) {
            $target = Join-Path $path $pattern
            if (!(Test-Path -path $target)) {
                $result = 0
            }
        }
    } else {
        $result = 0
    }
    if (! $result) {
        Write-Host "The path ""$displayPath"" does not appear to be a Qpid C++ build root path."
    }
    $result
}


#############################
# WriteDotnetBindingSlnLauncherPs1
#   Write a powershell script that sets up the environment
#   and then launches Visual Studio solution file.
#
function WriteDotnetBindingSlnLauncherPs1
{
    param
    (
        [string] $slnName,
        [string] $boostRoot,
        [string] $buildRoot,
        [string] $cppDir,
        [string] $vsPlatform,
        [string] $nBits,
        [string] $outfileName,
        [string] $studioVersion,
        [string] $studioSubdir
    )

    $out = @("#
# Launch $slnName in $studioVersion $vsPlatform ($nBits-bit) environment
#
$global:txtPath  = ""$boostRoot\lib;$global:txtPath""
$global:txtQR    = ""$buildRoot""
$global:txtWH      ""Launch $slnName in $studioVersion $vsPlatform ($nBits-bit) environment.""
$buildRoot\bindings\qpid\dotnet\$vsSubdirX\$slnName
")
    Write-Host "        $buildRoot\$outfileName"
    $out | Out-File "$buildRoot\$outfileName" -encoding ASCII
}


#############################
# WriteDotnetBindingSlnLauncherBat
#   Write a batch file that
#   launches a powershell script.
#
function WriteDotnetBindingSlnLauncherBat
{
    param
    (
        [string] $slnName,
        [string] $buildRoot,
        [string] $vsPlatform,
        [string] $nBits,
        [string] $psScriptName,
        [string] $outfileName,
        [string] $studioVersion,
        [string] $studioSubdir
    )

    $out = @("@ECHO OFF
REM
REM Launch $slnName in $studioVersion $vsPlatform ($nBits-bit) environment
REM
ECHO Launch $slnName in $studioVersion $vsPlatform ($nBits-bit) environment
powershell $buildRoot\$psScriptName
")
    Write-Host "        $buildRoot\$outfileName"
    $out | Out-File "$buildRoot\$outfileName" -encoding ASCII
}


#############################
# WriteDotnetBindingEnvSetupBat
#   Write a batch file that sets the desired environment into
#   the user's current environment settings.
#
function WriteDotnetBindingEnvSetupBat
{
    param
    (
        [string] $slnName,
        [string] $boostRoot,
        [string] $buildRoot,
        [string] $vsPlatform,
        [string] $nBits,
        [string] $outfileName,
        [string] $studioVersion,
        [string] $studioSubdir,
        [string] $cmakeLine
    )

    $out = @("@ECHO OFF
REM
REM Call this command procedure from a command prompt to set up a
REM $studioVersion $vsPlatform ($nBits-bit)
REM $slnName environment
REM
REM     > call $outfileName
REM     >
REM
REM The solution was generated with cmake command line:
REM $cmakeLine
SET se_buildconfig=%1
ECHO %PATH% | FINDSTR /I boost > NUL
IF %ERRORLEVEL% EQU 0 ECHO WARNING: Boost is defined in your path multiple times!
SET PATH=$boostRoot\lib;%PATH%
SET QPID_BUILD_ROOT=$buildRoot
IF NOT DEFINED se_buildconfig (GOTO :CONT)
SET PATH=%QPID_BUILD_ROOT%\src\%se_buildconfig%;%PATH%
:CONT
ECHO Environment set for $slnName $studioVersion $vsPlatform $nBits-bit development.
")
    Write-Host "        $buildRoot\$outfileName"
    $out | Out-File "$buildRoot\$outfileName" -encoding ASCII
}

#############################
# WriteCmakeRerunnerBat
#   Write a batch file that runs cmake again
#
function WriteCmakeRerunnerBat
{
    param
    (
        [string] $slnName,
        [string] $boostRoot,
        [string] $buildRoot,
        [string] $vsPlatform,
        [string] $nBits,
        [string] $outfileName,
        [string] $studioVersion,
        [string] $studioSubdir,
        [string] $cmakeLine
    )

    $out = @("@ECHO OFF
REM
REM Call this command procedure from a command prompt to rerun cmake
REM $studioVersion $vsPlatform ($nBits-bit)
REM
$cmakeLine
")
    Write-Host "        $buildRoot\$outfileName"
    $out | Out-File "$buildRoot\$outfileName" -encoding ASCII
}

#############################
# WriteMakeInstallBat
#   Write a batch file that runs "make install" for debug build
#
function WriteMakeInstallBat
{
    param
    (
        [string] $buildRoot,
        [string] $outfileName,
        [string] $varfileName,
        [string] $vsEnvironment,
        [string] $vsBuildTarget
    )
    $newTarget = $vsBuildTarget -replace "Debug", "%mi_buildconfig%"
    $out = @("@ECHO OFF
REM
REM Call this command procedure from a command prompt to run 'make install'
REM   %1 selects build configuration. Defaults to Debug
REM
setlocal
SET mi_buildconfig=%1
IF NOT DEFINED mi_buildconfig (SET mi_buildconfig=Debug)
call $varfileName %mi_buildconfig%
call $vsEnvironment
devenv qpid-cpp.sln /build $newTarget /project INSTALL
endlocal
")
    Write-Host "        $buildRoot\$outfileName"
    $out | Out-File "$buildRoot\$outfileName" -encoding ASCII
}

#############################
# Given a visual studio selection from command line or selection list
#  Return the visual studio and architecture settings or exit
#
function ParseStudioSelection 
{
    param
    (
        [string] $vsSelection
    )
    Write-Host "Checking studio version: $vsSelection"
    if ($vsSelection.Contains("2013")) {
        $global:vsVersion = "Visual Studio 2013"
        $global:cmakeGenerator = "Visual Studio 12 2013"
        $global:vsSubdir = "msvc12"
        $global:vsSubdirX = "msvcx"
        $global:cmakeCompiler = "-vc120"
        $global:vsShortName = "2013"
        $global:vsEnvironment = """%VS120COMNTOOLS%..\..\VC\vcvarsall.bat"""
    } elseif ($vsSelection.Contains("2012")) {
        $global:vsVersion = "Visual Studio 2012"
        $global:cmakeGenerator = "Visual Studio 11"
        $global:vsSubdir = "msvc11"
        $global:vsSubdirX = "msvcx"
        $global:cmakeCompiler = "-vc110"
        $global:vsShortName = "2012"
        $global:vsEnvironment = """%VS110COMNTOOLS%..\..\VC\vcvarsall.bat"""
    } elseif ($vsSelection.Contains("2010")) {
        $global:vsVersion = "Visual Studio 2010"
        $global:cmakeGenerator = "Visual Studio 10"
        $global:vsSubdir = "msvc10"
        $global:vsSubdirX = "msvcx"
        $global:cmakeCompiler = "-vc100"
        $global:vsShortName = "2010"
        $global:vsEnvironment = """%VS100COMNTOOLS%..\..\VC\vcvarsall.bat"""
    } elseif ($vsSelection.Contains("2008")) {
        $global:vsVersion = "Visual Studio 2008"
        $global:cmakeGenerator = "Visual Studio 9 2008"
        $global:vsSubdir = "msvc9"
        $global:vsSubdirX = "msvc9"
        $global:cmakeCompiler = "-vc90"
        $global:vsShortName = "2008"
        $global:vsEnvironment = """%VS90COMNTOOLS%..\..\VC\vcvarsall.bat"""
    } else {
        Write-Host "Visual Studio must be 2008, 2010, 2012, or 2013"
        exit
    }
    $global:vsSelectedOption = $vsSelection
    
    if ($vsSelection.Contains("x86")) {
        $global:buildPathSizeId = "x86"
        $global:build32or64 = "32"
        $global:vsEnvironment += " x86"
        $global:vsBuildTarget = """Debug|Win32"""
    } elseif ($vsSelection.Contains("x64")) {
        $global:buildPathSizeId = "x64"
        $global:build32or64 = "64"
        $global:vsEnvironment += " amd64"
        $global:vsBuildTarget = """Debug|x64"""
        # Promote CMAKE generator to 64 bit variant
        $global:cmakeGenerator += " Win64"
    } else {
        Write-Host "Studio selection must contain x86 or x64"
        exit
    }
}

#############################
# When the user presses 'select' then this function handles it.
#  Return the visual studio and architecture.
#  Close the form.
#
function SelectVisualStudio {
    if ($DropDown.SelectedItem -ne $null) {
        $vsVersion = $DropDown.SelectedItem.ToString()
        
        ParseStudioSelection $vsVersion 

        $Form.Close() 2> $null
        Write-Host "Selected generator: $global:cmakeGenerator"
    }
}

#############################
# Create the Visual Studio version form and launch it
#
function SelectVisualStudioVersion {

    $Form = New-Object System.Windows.Forms.Form

    $Form.width = 350
    $Form.height = 150
    $Form.Text = "Select Visual Studio Version and platform"

    $DropDown          = new-object System.Windows.Forms.ComboBox
    $DropDown.Location = new-object System.Drawing.Size(120,10)
    $DropDown.Size     = new-object System.Drawing.Size(150,30)

    ForEach ($Item in $global:VsVersionCmakeChoiceList) {
        [void] $DropDown.Items.Add($Item)
    }
    $DropDown.SelectedIndex = 0

    $Form.Controls.Add($DropDown)

    $Button          = new-object System.Windows.Forms.Button
    $Button.Location = new-object System.Drawing.Size(120,50)
    $Button.Size     = new-object System.Drawing.Size(120,20)
    $Button.Text     = "Select"
    $Button.Add_Click({SelectVisualStudio})
    $form.Controls.Add($Button)

    $Form.Add_Shown({$Form.Activate()})
    $Form.ShowDialog()
}


#############################
# Main
#############################
#
# curDir is qpid\cpp\bindings\qpid\dotnet.
#
[string] $curDir   = Split-Path -parent $MyInvocation.MyCommand.Definition
[string] $projRoot = Resolve-Path (Join-Path $curDir "..\..\..\..")
[string] $cppDir   = Resolve-Path (Join-Path $curDir "..\..\..")

[System.Reflection.Assembly]::LoadWithPartialName("System.Windows.Forms") | Out-Null
[System.Reflection.Assembly]::LoadWithPartialName("System.Drawing")       | Out-Null

#############################
# Get command line args
#
if ($args.Length -ge 1) {
    if (($args[0].Contains("help")) -or ($args[0].Contains("-h"))) {
        Usage
        exit
    }
    
    $vsVer = $args[0]
    ParseStudioSelection $vsVer
}

if ($args.Length -ge 2) {
    $global:boostRootPath = $args[1]
}

#############################
# User dialog to select a version of Visual Studio as CMake generator
#
if ($global:vsVersion -eq '') {
    SelectVisualStudioVersion
}

#############################
# User dialog to get boost paths
#
if ($global:boostRootPath -eq '') {
    $global:boostRootPath = Select-Folder -message "Select BOOST_ROOT folder for $global:vsSelectedOption build. Press CANCEL to quit"
}

#############################
# Decide to run cmake or not.
#   If the build directory is absent the run cmake
#   If the build directory already exists then it's the user's choice
#
$make = 0
$defined = ($global:boostRootPath -ne $null) -and ($global:boostRootPath -ne '')
if ($defined) {
    $found = SanityCheckBoostPath $global:boostRootPath
    if (! $found) {
        exit
    }
    
    $build   = Join-Path $projRoot   "build_${global:vsShortName}_${global:buildPathSizeId}"
    $install = Join-Path $projRoot "install_${global:vsShortName}_${global:buildPathSizeId}"
        
    $found = SanityCheckBuildPath $build
    if ($found) {
        $make = AskYesOrNo "build directory ""$build"" appears to have run cmake already. Run cmake again?"
    } else {
        $make = 1
    }
}

if (! $make) {
    exit
}

#############################
# run CMake
#
if(!(Test-Path -Path $build)) {
    New-Item -ItemType directory -Path $build
}
cd "$build"
$global:cmakeCommandLine  = "CMake -G ""$global:cmakeGenerator"" "
$global:cmakeCommandLine += """-DBUILD_DOCS=No"" "
$global:cmakeCommandLine += """-DCMAKE_INSTALL_PREFIX=$install"" "
$global:cmakeCommandLine += """-DBoost_COMPILER=$global:cmakeCompiler"" "
$global:cmakeCommandLine += """-DBOOST_ROOT=$global:boostRootPath"" "
$global:cmakeCommandLine += """-DINSTALL_QMFGEN=No"" "
$global:cmakeCommandLine +=  $cppDir
Write-Host "Running CMake in $build : $global:cmakeCommandLine"
& cmd /c "$global:cmakeCommandLine 2>&1"


#############################
# Emit scripts
#
Write-Host "Writing helper scripts..."

###########
# Powershell script to launch org.apache.qpid.messaging.sln
#
WriteDotnetBindingSlnLauncherPs1     -slnName "org.apache.qpid.messaging.sln" `
                                   -boostRoot "$global:boostRootPath" `
                                   -buildRoot "$build" `
                                      -cppDir "$cppDir" `
                                  -vsPlatform $global:buildPathSizeId `
                                       -nBits $global:build32or64 `
                                 -outfileName "start-devenv-messaging-$global:vsSubdir-$global:buildPathSizeId-$global:build32or64-bit.ps1" `
                               -studioVersion "$global:vsVersion" `
                                -studioSubdir "$global:vsSubdir"

###########
# Batch script (that you doubleclick) to launch powershell script
# that launches org.apache.qpid.messaging.sln.
#
WriteDotnetBindingSlnLauncherBat      -slnName "org.apache.qpid.messaging.sln" `
                                    -buildRoot "$build" `
                                   -vsPlatform $global:buildPathSizeId `
                                        -nBits $global:build32or64 `
                                 -psScriptName "start-devenv-messaging-$global:vsSubdir-$global:buildPathSizeId-$global:build32or64-bit.ps1" `
                                  -outfileName "start-devenv-messaging-$global:vsSubdir-$global:buildPathSizeId-$global:build32or64-bit.bat" `
                                -studioVersion "$global:vsVersion" `
                                 -studioSubdir "$global:vsSubdir"

###########
# Batch script (that you CALL from a command prompt)
# to establish the org.apache.qpid.messaging.sln build environment.
#
WriteDotnetBindingEnvSetupBat     -slnName "org.apache.qpid.messaging.sln" `
                                -boostRoot "$global:boostRootPath" `
                                -buildRoot "$build" `
                               -vsPlatform $global:buildPathSizeId `
                                    -nBits $global:build32or64 `
                              -outfileName "setenv-messaging-$global:vsSubdir-$global:buildPathSizeId-$global:build32or64-bit.bat" `
                            -studioVersion "$global:vsVersion" `
                             -studioSubdir "$global:vsSubdir" `
                                -cmakeLine "$global:cmakeCommandLine"

###########
# Batch script to re-run cmake
#
WriteCmakeRerunnerBat             -slnName "org.apache.qpid.messaging.sln" `
                                -boostRoot ""$global:boostRootPath"" `
                                -buildRoot "$build" `
                               -vsPlatform $global:buildPathSizeId `
                                    -nBits $global:build32or64 `
                              -outfileName "run-cmake.bat" `
                            -studioVersion "$global:vsVersion" `
                             -studioSubdir "$global:vsSubdir" `
                                -cmakeLine "$global:cmakeCommandLine"
                                
###########
# Batch script to do command line "make install"
#
WriteMakeInstallBat             -buildRoot "$build" `
                              -outfileName "make-install.bat" `
                              -varfileName "setenv-messaging-$global:vsSubdir-$global:buildPathSizeId-$global:build32or64-bit.bat" `
                            -vsEnvironment $global:vsEnvironment `
                            -vsBuildTarget $global:vsBuildTarget

#############################
# Pause on exit. If user ran this script through a graphical launch and there's
# an error then the window closes and the user never sees the error. This pause
# gives him a chance to figure it out.
#
#Write-Host "Press any key to continue ..."
#[void] $host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
