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

import qpid
from qpid.management import managementChannel, managementClient
from threading       import Lock
from disp            import Display
from shlex           import split
from qpid.client  import Client

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
  #    tables        :== {class-key}
  #                        {<obj-id>}
  #                          (timestamp, config-record, inst-record)
  #    class-key     :== (<package-name>, <class-name>, <class-hash>)
  #    timestamp     :== (<last-interval-time>, <create-time>, <delete-time>)
  #    config-record :== [element]
  #    inst-record   :== [element]
  #    element       :== (<element-name>, <element-value>)
  #

  def registerObjId (self, objId):
    if self.baseId == 0:
      if objId & 0x8000000000000000L == 0:
        self.baseId = objId - 1000

  def displayObjId (self, objId):
    if objId & 0x8000000000000000L == 0:
      return objId - self.baseId
    return (objId & 0x7fffffffffffffffL) + 5000

  def rawObjId (self, displayId):
    if displayId < 5000:
      return displayId + self.baseId
    return displayId - 5000 + 0x8000000000000000L

  def displayClassName (self, cls):
    (packageName, className, hash) = cls
    return packageName + "." + className

  def dataHandler (self, context, className, list, timestamps):
    """ Callback for configuration and instrumentation data updates """
    self.lock.acquire ()
    try:
      # If this class has not been seen before, create an empty dictionary to
      # hold objects of this class
      if className not in self.tables:
        self.tables[className] = {}

      # Register the ID so a more friendly presentation can be displayed
      id = long (list[0][1])
      self.registerObjId (id)

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

  def ctrlHandler (self, context, op, data):
    if op == self.mclient.CTRL_BROKER_INFO:
      pass

  def configHandler (self, context, className, list, timestamps):
    self.dataHandler (0, className, list, timestamps);

  def instHandler (self, context, className, list, timestamps):
    self.dataHandler (1, className, list, timestamps);

  def methodReply (self, broker, sequence, status, sText, args):
    """ Callback for method-reply messages """
    self.lock.acquire ()
    try:
      line = "Call Result: " + self.methodsPending[sequence] + \
             "  " + str (status) + " (" + sText + ")"
      print line, args
      del self.methodsPending[sequence]
    finally:
      self.lock.release ()

  def schemaHandler (self, context, className, configs, insts, methods, events):
    """ Callback for schema updates """
    if className not in self.schema:
      self.schema[className] = (configs, insts, methods, events)

  def __init__ (self, disp, host, port=5672, username="guest", password="guest",
                specfile="../../specs/amqp.0-10-preview.xml"):
    self.spec           = qpid.spec.load (specfile)
    self.lock           = Lock ()
    self.tables         = {}
    self.schema         = {}
    self.baseId         = 0
    self.disp           = disp
    self.lastUnit       = None
    self.methodSeq      = 1
    self.methodsPending = {}

    self.client = Client (host, port, self.spec)
    self.client.start ({"LOGIN": username, "PASSWORD": password})
    self.channel = self.client.channel (1)

    self.mclient = managementClient (self.spec, self.ctrlHandler, self.configHandler,
                                     self.instHandler, self.methodReply)
    self.mclient.schemaListener (self.schemaHandler)
    self.mch = self.mclient.addChannel (self.channel)

  def close (self):
    self.mclient.removeChannel (self.mch)

  def refName (self, oid):
    if oid == 0:
      return "NULL"
    return str (self.displayObjId (oid))

  def valueDisplay (self, classKey, key, value):
    for kind in range (2):
      schema = self.schema[classKey][kind]
      for item in schema:
        if item[0] == key:
          typecode = item[1]
          unit     = item[2]
          if (typecode >= 1 and typecode <= 5) or typecode >= 12:  # numerics
            if unit == None or unit == self.lastUnit:
              return str (value)
            else:
              self.lastUnit = unit
              suffix = ""
              if value != 1:
                suffix = "s"
              return str (value) + " " + unit + suffix
          elif typecode == 6 or typecode == 7: # strings
            return value
          elif typecode == 8:
            if value == 0:
              return "--"
            return self.disp.timestamp (value)
          elif typecode == 9:
            return str (value)
          elif typecode == 10:
            return self.refName (value)
          elif typecode == 11:
            if value == 0:
              return "False"
            else:
              return "True"
          elif typecode == 14:
            return str (UUID (bytes=value))
          elif typecode == 15:
            return str (value)
    return "*type-error*"

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
            result = result + self.valueDisplay (className, key, val)
    return result

  def getClassKey (self, className):
    dotPos = className.find(".")
    if dotPos == -1:
      for key in self.schema:
        if key[1] == className:
          return key
    else:
      package = className[0:dotPos]
      name    = className[dotPos + 1:]
      for key in self.schema:
        if key[0] == package and key[1] == name:
          return key
    return None

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
    elif typecode == 8:
      return "abs-time"
    elif typecode == 9:
      return "delta-time"
    elif typecode == 10:
      return "reference"
    elif typecode == 11:
      return "boolean"
    elif typecode == 12:
      return "float"
    elif typecode == 13:
      return "double"
    elif typecode == 14:
      return "uuid"
    elif typecode == 15:
      return "field-table"
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
      raise ValueError ("Invalid access code: %d" %code)

  def notNone (self, text):
    if text == None:
      return ""
    else:
      return text

  def isOid (self, id):
    for char in str (id):
      if not char.isdigit () and not char == '-':
        return False
    return True

  def listOfIds (self, classKey, tokens):
    """ Generate a tuple of object ids for a classname based on command tokens. """
    list = []
    if tokens[0] == "all":
      for id in self.tables[classKey]:
        list.append (self.displayObjId (id))

    elif tokens[0] == "active":
      for id in self.tables[classKey]:
        if self.tables[classKey][id][0][2] == 0:
          list.append (self.displayObjId (id))

    else:
      for token in tokens:
        if self.isOid (token):
          if token.find ("-") != -1:
            ids = token.split("-", 2)
            for id in range (int (ids[0]), int (ids[1]) + 1):
              if self.getClassForId (self.rawObjId (long (id))) == classKey:
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
        rows.append ((self.displayClassName (name), active, deleted))
      self.disp.table ("Management Object Types:",
                       ("ObjectType", "Active", "Deleted"), rows)
    finally:
      self.lock.release ()

  def listObjects (self, className):
    """ Generate a display of a list of objects in a class """
    self.lock.acquire ()
    try:
      classKey = self.getClassKey (className)
      if classKey == None:
        print ("Object type %s not known" % className)
      else:
        rows = []
        sorted = self.tables[classKey].keys ()
        sorted.sort ()
        for objId in sorted:
          (ts, config, inst) = self.tables[classKey][objId]
          createTime  = self.disp.timestamp (ts[1])
          destroyTime = "-"
          if ts[2] > 0:
            destroyTime = self.disp.timestamp (ts[2])
          objIndex = self.getObjIndex (classKey, config)
          row = (self.refName (objId), createTime, destroyTime, objIndex)
          rows.append (row)
        self.disp.table ("Objects of type %s.%s" % (classKey[0], classKey[1]),
                         ("ID", "Created", "Destroyed", "Index"),
                         rows)
    finally:
      self.lock.release ()

  def showObjects (self, tokens):
    """ Generate a display of object data for a particular class """
    self.lock.acquire ()
    try:
      self.lastUnit = None
      if self.isOid (tokens[0]):
        if tokens[0].find ("-") != -1:
          rootId = int (tokens[0][0:tokens[0].find ("-")])
        else:
          rootId = int (tokens[0])

        classKey  = self.getClassForId (self.rawObjId (rootId))
        remaining = tokens
        if classKey == None:
          print "Id not known: %d" % int (tokens[0])
          raise ValueError ()
      else:
        classKey  = self.getClassKey (tokens[0])
        remaining = tokens[1:]
        if classKey not in self.tables:
          print "Class not known: %s" % tokens[0]
          raise ValueError ()

      userIds = self.listOfIds (classKey, remaining)
      if len (userIds) == 0:
        print "No object IDs supplied"
        raise ValueError ()

      ids = []
      for id in userIds:
        if self.getClassForId (self.rawObjId (long (id))) == classKey:
          ids.append (self.rawObjId (long (id)))

      rows = []
      timestamp = None
      config = self.tables[classKey][ids[0]][1]
      for eIdx in range (len (config)):
        key = config[eIdx][0]
        if key != "id":
          row   = ("config", key)
          for id in ids:
            if timestamp == None or \
               timestamp < self.tables[classKey][id][0][0]:
              timestamp = self.tables[classKey][id][0][0]
            (key, value) = self.tables[classKey][id][1][eIdx]
            row = row + (self.valueDisplay (classKey, key, value),)
          rows.append (row)

      inst = self.tables[classKey][ids[0]][2]
      for eIdx in range (len (inst)):
        key = inst[eIdx][0]
        if key != "id":
          row = ("inst", key)
          for id in ids:
            (key, value) = self.tables[classKey][id][2][eIdx]
            row = row + (self.valueDisplay (classKey, key, value),)
          rows.append (row)

      titleRow = ("Type", "Element")
      for id in ids:
        titleRow = titleRow + (self.refName (id),)
      caption = "Object of type %s.%s:" % (classKey[0], classKey[1])
      if timestamp != None:
        caption = caption + " (last sample time: " + self.disp.timestamp (timestamp) + ")"
      self.disp.table (caption, titleRow, rows)

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
      for classKey in sorted:
        tuple = self.schema[classKey]
        className = classKey[0] + "." + classKey[1]
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
      classKey = self.getClassKey (className)
      if classKey == None:
        print ("Class name %s not known" % className)
        raise ValueError ()

      rows = []
      for config in self.schema[classKey][0]:
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
        
      for config in self.schema[classKey][1]:
        name     = config[0]
        if name != "id":
          typename = self.typeName(config[1])
          unit     = self.notNone (config[2])
          desc     = self.notNone (config[3])
          rows.append ((name, typename, unit, "", "", desc))

      titles = ("Element", "Type", "Unit", "Access", "Notes", "Description")
      self.disp.table ("Schema for class '%s.%s':" % (classKey[0], classKey[1]), titles, rows)

      for mname in self.schema[classKey][2]:
        (mdesc, args) = self.schema[classKey][2][mname]
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
    """ Given an object ID, return the class key for the referenced object """
    for classKey in self.tables:
      if objId in self.tables[classKey]:
        return classKey
    return None

  def callMethod (self, userOid, methodName, args):
    self.lock.acquire ()
    methodOk = True
    try:
      classKey = self.getClassForId (self.rawObjId (userOid))
      if classKey == None:
        raise ValueError ()

      if methodName not in self.schema[classKey][2]:
        print "Method '%s' not valid for class '%s.%s'" % (methodName, classKey[0], classKey[1])
        raise ValueError ()

      schemaMethod = self.schema[classKey][2][methodName]
      if len (args) != len (schemaMethod[1]):
        print "Wrong number of method args: Need %d, Got %d" % (len (schemaMethod[1]), len (args))
        raise ValueError ()

      namedArgs = {}
      for idx in range (len (args)):
        namedArgs[schemaMethod[1][idx][0]] = args[idx]

      self.methodSeq = self.methodSeq + 1
      self.methodsPending[self.methodSeq] = methodName
    except:
      methodOk = False
    self.lock.release ()
    if methodOk:
#      try:
        self.mclient.callMethod (self.mch, self.methodSeq, self.rawObjId (userOid), classKey,
                                 methodName, namedArgs)
#      except ValueError, e:
#        print "Error invoking method:", e

  def do_list (self, data):
    tokens = data.split ()
    if len (tokens) == 0:
      self.listClasses ()
    elif len (tokens) == 1 and not self.isOid (tokens[0]):
      self.listObjects (data)
    else:
      self.showObjects (tokens)

  def do_schema (self, data):
    if data == "":
      self.schemaSummary ()
    else:
      self.schemaTable (data)

  def do_call (self, data):
    tokens = data.split ()
    if len (tokens) < 2:
      print "Not enough arguments supplied"
      return
    
    userOid    = long (tokens[0])
    methodName = tokens[1]
    args       = tokens[2:]
    self.callMethod (userOid, methodName, args)
