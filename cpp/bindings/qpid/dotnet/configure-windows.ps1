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
# configure-windows.ps1
# =====================
#
# This script configures a qpid\cpp developer build environment under Windows
# to enable working with cpp\bindings\qpid\dotnet binding source code.
#
# * Supports multiple versions of Visual Studio (VS2008, VS2010, VS2012) 
#   as CMake generator.
#
# * Supports 32-bit and/or 64-bit development platforms.
#
# * User chooses in-source or out-of-source build directories.
#
#     - 'In-source' builds happen when CMake is run from directory qpid\cpp.
#       Hundreds of CMake-generated output files are placed in qpid\cpp\src.
#       These files go right on top of files that are part of the source tree
#       in qpid\cpp\src.
#       In-source builds support only one platform.
#       Choose only a 32-bit or a 64-bit platform but not both.
#
#     - Out-of-source builds happen when the user chooses another directory
#       under qpid in which to run CMake. Out-of-source builds are required
#       in order to build both x86 and x64 targets using the same source tree.
#       For each build platform (32-bit x86 or Win32, or 64-bit x64) the user
#       specifies a build directory and a specific version of Boost.
#       Many platform/Boost-version directories may reside side by side.
#
# * User chooses to run CMake or not.
#
#     - When a new build directory is created then the user is given the
#       option of running CMake in that directory. Running CMake is a
#       necessary step as CMake creates important source, solution, and
#       project files.
#
#     - If a directory "looks like" is has already had CMake run in it
#       then this script skips running CMake again.
#
#
# Prerequisites
#
#  1. Powershell must be installed.
#  2. 32-bit and/or 64-bit Boost libraries must be installed in separate
#     directories. A user system may have any number of Boost library
#     versions installed on it as long as each may be referred to through
#     a separate BOOST_ROOT directory path.
#  3. CMake 2.8 (or later) must be installed. The cmake\bin directory
#     must be in the user's path.
#  4. Boost library specifications may or may not be in the user's path.
#     The script author recommeds not to have Boost in the path and only
#     allow the Boost path to be specified by generated command procedures.
#  5. Visual Studio build environment must be installed.
#
#
# Use case: Create a new build environment
# ========================================
#
#  Required Boost:
#        32-bit library - C:\Boost
#        64-bit library - D:\Boost_64
#
#  Required Qpid checkout tree
#        C:\svn\qpid\...
#
#  Run this script. It will ask for four directories:
#
#        Needed info        User clicks on or adds new
#        ----------------   --------------------------
#        32-bit Boost       C:\boost
#        32-bit build dir   C:\svn\qpid\build32
#        64-bit Boost       D:\Boost_64
#        64-bit build dir   C:\svn\qpid\build64
#
#  In this example the build dirs are new. The script will prompt
#  asking if CMake is to run in the build directories. User chooses Yes.
#
#  Now this script runs CMake twice, once each with the 32-bit and 64-bit
#  generators.
#  * This step creates qpid-cpp.sln and related project files.
#      C:\svn\qpid\build32\qpid-cpp.sln
#      C:\svn\qpid\build64\qpid-cpp.sln
#
#  This script generates other scripts as follows:
#
#   C:\svn\qpid\build32\start-devenv-messaging-x86-32bit.ps1
#   C:\svn\qpid\build32\start-devenv-messaging-x86-32bit.bat
#   C:\svn\qpid\build32\setenv-messaging-x86-32bit.bat
#
#   C:\svn\qpid\build64\start-devenv-messaging-x64-64bit.ps1
#   C:\svn\qpid\build64\start-devenv-messaging-x64-64bit.bat
#   C:\svn\qpid\build64\setenv-messaging-x64-64bit.bat
#
#  Next the user compiles solution qpid\build32\qpid-cpp.sln.
#
# Using the generated scripts:
#
# Case 1. Run an executable in 32-bit mode.
#  1. Open a command prompt.
#  2. > CD   c:\svn\qpid\build32
#  3. > CALL setenv-messaging-x86-32bit.bat
#  4. > CD   src\debug
#  5. > run the chosen program
#
#  Note: Step #3 adds Boost to the user's path. This script selects the
#        version of Boost to match the executables in src\debug.
#
# Case 2. Launch Visual Studio org.apache.qpid.messaging.sln in 64-bit mode.
#  1. > CD c:\svn\qpid\build64
#  2. > powershell start-devenv-messaging-x64-64bit.ps1
#     or
#  1. Double-click c:\svn\qpid\build64\start-devenv-messaging-x64-64bit.bat
#
#  Note: In this case the scripts set QPID_BUILD_ROOT to point to the out-of-
#        source directory used by qpid-cpp.sln. Also the scripts put Boost in
#        the path so that executables may run directly from Visual Studio.
#

Set-PSDebug -Trace 0
Set-PSDebug -strict
$ErrorActionPreference='Stop'

#############################
# global strings to be written to script files
#
$global:txtPath = '$env:PATH'
$global:txtQR   = '$env:QPID_BUILD_ROOT'
$global:txtWH   = 'Write-Host'

#############################
# Visual Studio version selection dialog items and choice
#
[array]$global:VsVersionCmakeChoiceList = "Visual Studio 2012", "Visual Studio 2010", "Visual Studio 2008"
$global:vsVersion = ''
$global:cmakeGenerator = ''
$global:vsSubdir = ''
$global:cmakeCompiler = ''

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
    $result = $true
    $displayPath = ""

    if ($path -ne $null) {
        $displayPath = $path

        $toTest = ('include', 'lib')
        foreach ($pattern in $toTest) {
            $target = Join-Path $path $pattern
            if (!(Test-Path -path $target)) {
                $result = $false
            }
        }
    } else {
        $result = $false
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
    $result = $true
    $displayPath = ""
    if ($path -ne $null) {
        $displayPath = $path

        $toTest = ('CMakeFiles', 'docs', 'etc', 'examples', 'include',
                   'managementgen', 'src')
        foreach ($pattern in $toTest) {
            $target = Join-Path $path $pattern
            if (!(Test-Path -path $target)) {
                $result = $false
            }
        }
    } else {
        $result = $false
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
$cppDir\bindings\qpid\dotnet\$vsSubdir\$slnName
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
        [string] $studioSubdir
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
ECHO %PATH% | FINDSTR /I boost > NUL
IF %ERRORLEVEL% EQU 0 ECHO WARNING: Boost is defined in your path multiple times!
SET PATH=$boostRoot\lib;%PATH%
SET QPID_BUILD_ROOT=$buildRoot
ECHO Environment set for $slnName $studioVersion $vsPlatform $nBits-bit development.
")
    Write-Host "        $buildRoot\$outfileName"
    $out | Out-File "$buildRoot\$outfileName" -encoding ASCII
}

#############################
# Return the SelectedItem from the dropdown list and close the form.
#
function Return-DropDown {
    if ($DropDown.SelectedItem -ne $null) {
        $global:vsVersion = $DropDown.SelectedItem.ToString()
        if ($global:vsVersion -eq 'Visual Studio 2012') {
            $global:cmakeGenerator = "Visual Studio 11"
            $global:vsSubdir = "msvc11"
			$global:cmakeCompiler = "-vc110"
        } else {
			if ($global:vsVersion -eq 'Visual Studio 2010') {
				$global:cmakeGenerator = "Visual Studio 10"
				$global:vsSubdir = "msvc10"
				$global:cmakeCompiler = "-vc100"
			} else {
				if ($global:vsVersion -eq 'Visual Studio 2008') {
					$global:cmakeGenerator = "Visual Studio 9 2008"
					$global:vsSubdir = "msvc9"
					$global:cmakeCompiler = "-vc90"
				} else {
					Write-Host "Visual Studio must be 2008, 2010, or 2012"
					exit
				}
			}
		}
        $Form.Close()
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
    $Form.Text = ”Select Visual Studio Version”

    $DropDown          = new-object System.Windows.Forms.ComboBox
    $DropDown.Location = new-object System.Drawing.Size(120,10)
    $DropDown.Size     = new-object System.Drawing.Size(150,30)

    ForEach ($Item in $global:VsVersionCmakeChoiceList) {
        $DropDown.Items.Add($Item)
    }
    $DropDown.SelectedIndex = 0

    $Form.Controls.Add($DropDown)

#    $DropDownLabel.Location = new-object System.Drawing.Size(10,10)
#    $DropDownLabel.size     = new-object System.Drawing.Size(100,20)
#    $DropDownLabel.Text     = ""
#    $Form.Controls.Add($DropDownLabel)

    $Button          = new-object System.Windows.Forms.Button
    $Button.Location = new-object System.Drawing.Size(120,50)
    $Button.Size     = new-object System.Drawing.Size(120,20)
    $Button.Text     = "Select"
    $Button.Add_Click({Return-DropDown})
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
# User dialog to select a version of Visual Studio as CMake generator
#
SelectVisualStudioVersion

#############################
# User dialog to get optional 32-bit boost and build paths
#
$boost32 = Select-Folder -message "Select 32-bit BOOST_ROOT folder for $global:vsVersion build. Press CANCEL to skip 32-bit processing."

$defined32 = ($boost32 -ne $null) -and ($boost32 -ne '')
if ($defined32) {
    $found = SanityCheckBoostPath $boost32
    if (! $found) {
        exit
    }
}

$make32 = $false
if ($defined32) {

    $build32 = Select-Folder -message "Select 32-bit QPID_BUILD_ROOT folder for $global:vsVersion build." -path $projRoot

    $found = ($build32 -ne $null) -and ($build32 -ne '')
    if (! $found) {
        Write-Host "You must select a build root folder for 32-bit builds"
        exit
    }
    $found = SanityCheckBuildPath $build32
    if ($found) {
        Write-Host "Directory ""$build32"" is already set by CMake. CMake will not be re-run."
    } else {
        $make32 = AskYesOrNo "Run CMake in $build32 ?" "32-Bit Builds - Choose CMake run or not"
    }
}

#############################
# User dialog to get optional 64-bit boost and build paths
#
$boost64 = Select-Folder -message "Select 64-bit BOOST_ROOT folder for $global:vsVersion build. Press CANCEL to skip 64-bit processing."

$defined64 = ($boost64 -ne $null) -and ($boost64 -ne '')
if ($defined64) {
    $found = SanityCheckBoostPath $boost64
    if (! $found) {
        exit
    }
}

$make64 = $false
if ($defined64) {
    $build64 = Select-Folder -message "Select 64-bit QPID_BUILD_ROOT folder for $global:vsVersion build." -path $projRoot

    $found = ($build64 -ne $null) -and ($build64 -ne '')
    if (! $found) {
        Write-Host "You must select a build root folder for 64-bit builds"
        exit
    }
    $found = SanityCheckBuildPath $build64
    if ($found) {
        Write-Host "Directory ""$build64"" is already set by CMake. CMake will not be re-run."
    } else {
        $make64 = AskYesOrNo "Run CMake in $build64 ?" "64-Bit Builds - Choose CMake run or not"
    }
}

#############################
# Conditionally run CMake
#
# 32-bit X86
#
if ($make32) {
    $env:BOOST_ROOT = "$boost32"
    cd "$build32"
    Write-Host "Running 32-bit CMake in $build32 ..."
    CMake -G "$global:cmakeGenerator" "-DGEN_DOXYGEN=No" "-DCMAKE_INSTALL_PREFIX=install_x86" "-DBoost_COMPILER=$global:cmakeCompiler" $cppDir
} else {
    Write-Host "Skipped 32-bit CMake."
}

#
# 64-bit X64
#
if ($make64) {
    $env:BOOST_ROOT = "$boost64"
    cd "$build64"
    Write-Host "Running 64-bit CMake in $build64"
    CMake -G "$global:cmakeGenerator Win64" "-DGEN_DOXYGEN=No" "-DCMAKE_INSTALL_PREFIX=install_x64" "-DBoost_COMPILER=$global:cmakeCompiler" $cppDir
} else {
    Write-Host "Skipped 64-bit CMake."
}

#############################
# Emit scripts
#
# 32-bit scripts
#
if ($defined32) {

    Write-Host "Writing 32-bit scripts..."

    ###########
    # Powershell script to launch org.apache.qpid.messaging.sln
    #
    WriteDotnetBindingSlnLauncherPs1     -slnName "org.apache.qpid.messaging.sln" `
                                       -boostRoot "$boost32" `
                                       -buildRoot "$build32" `
                                          -cppDir "$cppDir" `
                                      -vsPlatform "x86" `
                                           -nBits "32" `
                                     -outfileName "start-devenv-messaging-$global:vsSubdir-x86-32bit.ps1" `
                                   -studioVersion "$global:vsVersion" `
                                    -studioSubdir "$global:vsSubdir"


    ###########
    # Batch script (that you doubleclick) to launch powershell script
    # that launches org.apache.qpid.messaging.sln.
    #
    WriteDotnetBindingSlnLauncherBat      -slnName "org.apache.qpid.messaging.sln" `
                                        -buildRoot "$build32" `
                                       -vsPlatform "x86" `
                                            -nBits "32" `
                                     -psScriptName "start-devenv-messaging-$global:vsSubdir-x86-32bit.ps1" `
                                      -outfileName "start-devenv-messaging-$global:vsSubdir-x86-32bit.bat" `
                                    -studioVersion "$global:vsVersion" `
                                     -studioSubdir "$global:vsSubdir"

    ###########
    # Batch script (that you CALL from a command prompt)
    # to establish the org.apache.qpid.messaging.sln build environment.
    #
    WriteDotnetBindingEnvSetupBat     -slnName "org.apache.qpid.messaging.sln" `
                                    -boostRoot "$boost32" `
                                    -buildRoot "$build32" `
                                   -vsPlatform "x86" `
                                        -nBits "32" `
                                  -outfileName "setenv-messaging-$global:vsSubdir-x86-32bit.bat" `
                                -studioVersion "$global:vsVersion" `
                                 -studioSubdir "$global:vsSubdir"

} else {
    Write-Host "Skipped writing 32-bit scripts."
}

#############################
# 64-bit scripts
#
if ($defined64) {

    Write-Host "Writing 64-bit scripts..."

    ###########
    # Powershell script to launch org.apache.qpid.messaging.sln
    #
    WriteDotnetBindingSlnLauncherPs1     -slnName "org.apache.qpid.messaging.sln" `
                                       -boostRoot "$boost64" `
                                       -buildRoot "$build64" `
                                          -cppDir "$cppDir" `
                                      -vsPlatform "x64" `
                                           -nBits "64" `
                                     -outfileName "start-devenv-messaging-$global:vsSubdir-x64-64bit.ps1" `
                                   -studioVersion "$global:vsVersion" `
                                    -studioSubdir "$global:vsSubdir"


    ###########
    # Batch script (that you doubleclick) to launch powershell script
    # that launches org.apache.qpid.messaging.sln.
    #
    WriteDotnetBindingSlnLauncherBat      -slnName "org.apache.qpid.messaging.sln" `
                                        -buildRoot "$build64" `
                                       -vsPlatform "x64" `
                                            -nBits "64" `
                                     -psScriptName "start-devenv-messaging-$global:vsSubdir-x64-64bit.ps1" `
                                      -outfileName "start-devenv-messaging-$global:vsSubdir-x64-64bit.bat" `
                                    -studioVersion "$global:vsVersion" `
                                     -studioSubdir "$global:vsSubdir"

    ###########
    # Batch script (that you CALL from a command prompt)
    # to establish the org.apache.qpid.messaging.sln build environment.
    #
    WriteDotnetBindingEnvSetupBat     -slnName "org.apache.qpid.messaging.sln" `
                                    -boostRoot "$boost64" `
                                    -buildRoot "$build64" `
                                   -vsPlatform "x64" `
                                        -nBits "64" `
                                  -outfileName "setenv-messaging-$global:vsSubdir-x64-64bit.bat" `
                                -studioVersion "$global:vsVersion" `
                                 -studioSubdir "$global:vsSubdir"

} else {
    Write-Host "Skipped writing 64-bit scripts."
}

#############################
# Pause on exit. If user ran this script through a graphical launch and there's
# an error then the window closes and the user never sees the error. This pause
# gives him a chance to figure it out.
#
Write-Host "Press any key to continue ..."
$x = $host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
