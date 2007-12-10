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

from qpid.management import ManagedBroker
from threading       import Lock
from disp            import Display
from shlex           import split

class ManagementData:

  #
  # Data Structure:
  #
  # Please note that this data structure holds only the most recent
  # configuration and instrumentation data for each object.  It does
  # not hold the detailed historical data that is sent from the broker.
  # The only historical data it keeps are the high and low watermarks
  # for hi-lo statistics.
  #
  #    tables        :== {<class-name>}
  #                        {<obj-id>}
  #                          (timestamp, config-record, inst-record)
  #    timestamp     :== (<last-interval-time>, <create-time>, <delete-time>)
  #    config-record :== [element]
  #    inst-record   :== [element]
  #    element       :== (<element-name>, <element-value>)
  #

  def dataHandler (self, context, className, list, timestamps):
    """ Callback for configuration and instrumentation data updates """
    self.lock.acquire ()
    try:
      # If this class has not been seen before, create an empty dictionary to
      # hold objects of this class
      if className not in self.tables:
        self.tables[className] = {}

      # Calculate a base-id so displayed IDs are reasonable 4-digit numbers
      id = long (list[0][1])
      if self.baseId == 0:
        self.baseId = id - 1000

      # If this object hasn't been seen before, create a new object record with
      # the timestamps and empty lists for configuration and instrumentation data.
      if id not in self.tables[className]:
        self.tables[className][id] = (timestamps, [], [])

      (unused, oldConf, oldInst) = self.tables[className][id]

      # For config updates, simply replace old config list with the new one.
      if   context == 0: #config
        self.tables[className][id] = (timestamps, list, oldInst)

      # For instrumentation updates, carry the minimum and maximum values for
      # "hi-lo" stats forward.
      elif context == 1: #inst
        if len (oldInst) == 0:
          newInst = list
        else:
          newInst = []
          for idx in range (len (list)):
            (key, value) = list[idx]
            if key.find ("High") == len (key) - 4:
              if oldInst[idx][1] > value:
                value = oldInst[idx][1]
            if key.find ("Low") == len (key) - 3:
              if oldInst[idx][1] < value:
                value = oldInst[idx][1]
            newInst.append ((key, value))
        self.tables[className][id] = (timestamps, oldConf, newInst)
      
    finally:
      self.lock.release ()

  def methodReply (self, broker, methodId, status, sText, args):
    """ Callback for method-reply messages """
    pass

  def schemaHandler (self, context, className, configs, insts, methods, events):
    """ Callback for schema updates """
    if className not in self.schema:
      self.schema[className] = (configs, insts, methods, events)

  def __init__ (self, disp, host, port=5672, username="guest", password="guest",
                spec="../../specs/amqp.0-10-preview.xml"):
    self.broker = ManagedBroker (host, port, username, password, spec)
    self.broker.configListener          (0,    self.dataHandler)
    self.broker.instrumentationListener (1,    self.dataHandler)
    self.broker.methodListener          (None, self.methodReply)
    self.broker.schemaListener          (None, self.schemaHandler)
    self.lock   = Lock ()
    self.tables = {}
    self.schema = {}
    self.baseId = 0
    self.disp   = disp
    self.broker.start ()

  def close (self):
    self.broker.stop ()

  def getObjIndex (self, className, config):
    """ Concatenate the values from index columns to form a unique object name """
    result = ""
    schemaConfig = self.schema[className][0]
    for item in schemaConfig:
      if item[5] == 1 and item[0] != "id":
        if result != "":
          result = result + "."
        for key,val in config:
          if key == item[0]:
            if key.find ("Ref") != -1:
              val = val - self.baseId
            result = result + str (val)
    return result

  def classCompletions (self, prefix):
    """ Provide a list of candidate class names for command completion """
    self.lock.acquire ()
    complist = []
    try:
      for name in self.tables:
        if name.find (prefix) == 0:
          complist.append (name)
    finally:
      self.lock.release ()
    return complist

  def typeName (self, typecode):
    """ Convert type-codes to printable strings """
    if   typecode == 1:
      return "uint8"
    elif typecode == 2:
      return "uint16"
    elif typecode == 3:
      return "uint32"
    elif typecode == 4:
      return "uint64"
    elif typecode == 5:
      return "bool"
    elif typecode == 6:
      return "short-string"
    elif typecode == 7:
      return "long-string"
    else:
      raise ValueError ("Invalid type code: %d" % typecode)

  def accessName (self, code):
    """ Convert element access codes to printable strings """
    if code == 1:
      return "ReadCreate"
    elif code == 2:
      return "ReadWrite"
    elif code == 3:
      return "ReadOnly"
    else:
      raise ValueErrir ("Invalid access code: %d" %code)

  def notNone (self, text):
    if text == None:
      return ""
    else:
      return text

  def listOfIds (self, className, tokens):
    """ Generate a tuple of object ids for a classname based on command tokens. """
    list = []
    if tokens[0] == "all":
      for id in self.tables[className]:
        list.append (id - self.baseId)

    else:
      for token in tokens:
        if token.find ("-") != -1:
          ids = token.split("-", 2)
          for id in range (int (ids[0]), int (ids[1]) + 1):
            if self.getClassForId (long (id) + self.baseId) == className:
              list.append (id)
        else:
          list.append (token)

    list.sort ()
    result = ()
    for item in list:
      result = result + (item,)
    return result

  def listClasses (self):
    """ Generate a display of the list of classes """
    self.lock.acquire ()
    try:
      rows = []
      sorted = self.tables.keys ()
      sorted.sort ()
      for name in sorted:
        active  = 0
        deleted = 0
        for record in self.tables[name]:
          isdel = False
          ts    = self.tables[name][record][0]
          if ts[2] > 0:
            isdel = True
          if isdel:
            deleted = deleted + 1
          else:
            active = active + 1
        rows.append ((name, active, deleted))
      self.disp.table ("Management Object Types:",
                       ("ObjectType", "Active", "Deleted"), rows)
    finally:
      self.lock.release ()

  def listObjects (self, className):
    """ Generate a display of a list of objects in a class """
    self.lock.acquire ()
    try:
      if className not in self.tables:
        print ("Object type %s not known" % className)
      else:
        rows = []
        sorted = self.tables[className].keys ()
        sorted.sort ()
        for objId in sorted:
          (ts, config, inst) = self.tables[className][objId]
          createTime  = self.disp.timestamp (ts[1])
          destroyTime = "-"
          if ts[2] > 0:
            destroyTime = self.disp.timestamp (ts[2])
          objIndex = self.getObjIndex (className, config)
          row = (objId - self.baseId, createTime, destroyTime, objIndex)
          rows.append (row)
        self.disp.table ("Objects of type %s" % className,
                         ("ID", "Created", "Destroyed", "Index"),
                         rows)
    finally:
      self.lock.release ()

  def showObjects (self, tokens):
    """ Generate a display of object data for a particular class """
    self.lock.acquire ()
    try:
      className = tokens[0]
      if className not in self.tables:
        print "Class not known: %s" % className
        raise ValueError ()
        
      userIds = self.listOfIds (className, tokens[1:])
      if len (userIds) == 0:
        print "No object IDs supplied"
        raise ValueError ()

      ids = []
      for id in userIds:
        if self.getClassForId (long (id) + self.baseId) == className:
          ids.append (long (id) + self.baseId)

      rows = []
      config = self.tables[className][ids[0]][1]
      for eIdx in range (len (config)):
        key = config[eIdx][0]
        if key != "id":
          isRef = key.find ("Ref") == len (key) - 3
          row   = ("config", key)
          for id in ids:
            value = self.tables[className][id][1][eIdx][1]
            if isRef:
              value = value - self.baseId
            row = row + (value,)
          rows.append (row)

      inst = self.tables[className][ids[0]][2]
      for eIdx in range (len (inst)):
        key = inst[eIdx][0]
        if key != "id":
          isRef = key.find ("Ref") == len (key) - 3
          row = ("inst", key)
          for id in ids:
            value = self.tables[className][id][2][eIdx][1]
            if isRef:
              value = value - self.baseId
            row = row + (value,)
          rows.append (row)

      titleRow = ("Type", "Element")
      for id in ids:
        titleRow = titleRow + (str (id - self.baseId),)
      self.disp.table ("Object of type %s:" % className, titleRow, rows)

    except:
      pass
    self.lock.release ()

  def schemaSummary (self):
    """ Generate a display of the list of classes in the schema """
    self.lock.acquire ()
    try:
      rows = []
      sorted = self.schema.keys ()
      sorted.sort ()
      for className in sorted:
        tuple = self.schema[className]
        row = (className, len (tuple[0]), len (tuple[1]), len (tuple[2]), len (tuple[3]))
        rows.append (row)
      self.disp.table ("Classes in Schema:",
                       ("Class", "ConfigElements", "InstElements", "Methods", "Events"),
                       rows)
    finally:
      self.lock.release ()

  def schemaTable (self, className):
    """ Generate a display of details of the schema of a particular class """
    self.lock.acquire ()
    try:
      if className not in self.schema:
        print ("Class name %s not known" % className)
        raise ValueError ()

      rows = []
      for config in self.schema[className][0]:
        name     = config[0]
        if name != "id":
          typename = self.typeName(config[1])
          unit     = self.notNone (config[2])
          desc     = self.notNone (config[3])
          access   = self.accessName (config[4])
          extra    = ""
          if config[5] == 1:
            extra = extra + "index "
          if config[6] != None:
            extra = extra + "Min: " + str (config[6])
          if config[7] != None:
            extra = extra + "Max: " + str (config[7])
          if config[8] != None:
            extra = extra + "MaxLen: " + str (config[8])
          rows.append ((name, typename, unit, access, extra, desc))
        
      for config in self.schema[className][1]:
        name     = config[0]
        if name != "id":
          typename = self.typeName(config[1])
          unit     = self.notNone (config[2])
          desc     = self.notNone (config[3])
          rows.append ((name, typename, unit, "", "", desc))

      titles = ("Element", "Type", "Unit", "Access", "Notes", "Description")
      self.disp.table ("Schema for class '%s':" % className, titles, rows)

      for method in self.schema[className][2]:
        mname = method[0]
        mdesc = method[1]
        args  = method[2]
        caption = "\nMethod '%s' %s" % (mname, self.notNone (mdesc))
        rows = []
        for arg in args:
          name     = arg[0]
          typename = self.typeName (arg[1])
          dir      = arg[2]
          unit     = self.notNone (arg[3])
          desc     = self.notNone (arg[4])
          extra    = ""
          if arg[5] != None:
            extra = extra + "Min: " + str (arg[5])
          if arg[6] != None:
            extra = extra + "Max: " + str (arg[6])
          if arg[7] != None:
            extra = extra + "MaxLen: " + str (arg[7])
          if arg[8] != None:
            extra = extra + "Default: " + str (arg[8])
          rows.append ((name, typename, dir, unit, extra, desc))
        titles = ("Argument", "Type", "Direction", "Unit", "Notes", "Description")
        self.disp.table (caption, titles, rows)

    except:
      pass
    self.lock.release ()

  def getClassForId (self, objId):
    """ Given an object ID, return the class name for the referenced object """
    for className in self.tables:
      if objId in self.tables[className]:
        return className
    return None

  def do_list (self, data):
    tokens = data.split ()
    if len (tokens) == 0:
      self.listClasses ()
    elif len (tokens) == 1:
      self.listObjects (data)
    else:
      self.showObjects (tokens)

  def do_schema (self, data):
    if data == "":
      self.schemaSummary ()
    else:
      self.schemaTable (data)

  def do_call (self, data):
    print "Not yet implemented"
