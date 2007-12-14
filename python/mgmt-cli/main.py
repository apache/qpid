#!/usr/bin/env python

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

import os
import getopt
import sys
import socket
from cmd            import Cmd
from managementdata import ManagementData
from shlex          import split
from disp           import Display
from qpid.peer      import Closed

class Mcli (Cmd):
  """ Management Command Interpreter """
  prompt = "qpid: "

  def __init__ (self, dataObject, dispObject):
    Cmd.__init__ (self)
    self.dataObject = dataObject
    self.dispObject = dispObject
    
  def emptyline (self):
    pass

  def do_help (self, data):
    print "Management Tool for QPID"
    print
    print "Commands:"
    print "    list                            - Print summary of existing objects by class"
    print "    list <className>                - Print list of objects of the specified class"
    print "    list <className> all            - Print contents of all objects of specified class"
    print "    list <className> active         - Print contents of all non-deleted objects of specified class"
    print "    list <className> <list-of-IDs>  - Print contents of one or more objects"
    print "        list is space-separated, ranges may be specified (i.e. 1004-1010)"
    print "    call <ID> <methodName> [<args>] - Invoke a method on an object"
    print "    schema                          - Print summary of object classes seen on the target"
    print "    schema <className>              - Print details of an object class"
    print "    set time-format short           - Select short timestamp format (default)"
    print "    set time-format long            - Select long timestamp format"
    print "    quit or ^D                      - Exit the program"
    print

  def complete_set (self, text, line, begidx, endidx):
    """ Command completion for the 'set' command """
    tokens = split (line)
    if len (tokens) < 2:
      return ["time-format "]
    elif tokens[1] == "time-format":
      if len (tokens) == 2:
        return ["long", "short"]
      elif len (tokens) == 3:
        if "long".find (text) == 0:
          return ["long"]
        elif "short".find (text) == 0:
          return ["short"]
    elif "time-format".find (text) == 0:
      return ["time-format "]
    return []

  def do_set (self, data):
    tokens = split (data)
    try:
      if tokens[0] == "time-format":
        self.dispObject.do_setTimeFormat (tokens[1])
    except:
      pass

  def complete_schema (self, text, line, begidx, endidx):
    tokens = split (line)
    if len (tokens) > 2:
      return []
    return self.dataObject.classCompletions (text)

  def do_schema (self, data):
    self.dataObject.do_schema (data)

  def complete_list (self, text, line, begidx, endidx):
    tokens = split (line)
    if len (tokens) > 2:
      return []
    return self.dataObject.classCompletions (text)

  def do_list (self, data):
    self.dataObject.do_list (data)

  def do_call (self, data):
    self.dataObject.do_call (data)

  def do_EOF (self, data):
    print "quit"
    return True

  def do_quit (self, data):
    return True

  def postcmd (self, stop, line):
    return stop

  def postloop (self):
    print "Exiting..."
    self.dataObject.close ()

def Usage ():
  print sys.argv[0], "[<target-host> [<tcp-port>]]"
  print
  sys.exit (1)

#=========================================================
# Main Program
#=========================================================

# Get host name and port if specified on the command line
try:
  (optlist, cargs) = getopt.getopt (sys.argv[1:], 's:')
except:
  Usage ()

specpath = "/usr/share/amqp/amqp.0-10-preview.xml"
host     = "localhost"
port     = 5672

if "s" in optlist:
  specpath = optlist["s"]

if len (cargs) > 0:
  host = cargs[0]

if len (cargs) > 1:
  port = int (cargs[1])

print ("Management Tool for QPID")
disp = Display ()

# Attempt to make a connection to the target broker
try:
  data = ManagementData (disp, host, port, spec=specpath)
except socket.error, e:
  sys.exit (0)
except Closed, e:
  if str(e).find ("Exchange not found") != -1:
    print "Management not enabled on broker:  Use '-m yes' option on broker startup."
  sys.exit (0)

# Instantiate the CLI interpreter and launch it.
cli = Mcli (data, disp)
cli.cmdloop ()
