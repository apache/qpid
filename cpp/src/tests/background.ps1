# From http://ps1.soapyfrog.com/2007/01/22/running-pipelines-in-the-background/
# Copyright © 2006-2009 Adrian Milliner
param(
    [scriptblock] $script,  # scriptblock to run
    [switch] $inconsole      # don't create a new window
)

# break out of the script on any errors
trap { break }

# encode the script to pass to the child process...
$encodedString = [convert]::ToBase64String(
    [Text.Encoding]::Unicode.GetBytes([string] $script))

# create a new process
$p = new-object System.Diagnostics.Process

# create a startinfo object for the process
$si = new-object System.Diagnostics.ProcessStartInfo
$si.WorkingDirectory = $pwd

if ($inconsole)
{ 
    $si.UseShellExecute = $false
}
Else
{
    $si.UseShellExecute = $true
}

# set up the command and arguments to run
$si.FileName = (get-command powershell.exe).Definition
$si.Arguments = "-encodedCommand $encodedString"

# and start the powershell process
[diagnostics.process]::Start($si)
