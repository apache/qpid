#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http:#www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

from xml.dom.minidom import parse, parseString, Node
from cStringIO       import StringIO
from stat            import *
from errno           import *
import os
import os.path
import filecmp

class Template:
  """
  Expandable File Template - This class is instantiated each time a
  template is to be expanded.  It is instantiated with the "filename"
  which is the full path to the template file and the "handler" which
  is an object that is responsible for storing variables (setVariable),
  checking conditions (testCondition), and expanding tags (substHandler).
  """
  def __init__ (self, filename, handler):
    self.filename = filename
    self.handler  = handler
    self.handler.initExpansion ()
    self.writing  = True

  def expandLine (self, line, stream, object):
    cursor = 0
    while 1:
      sub = line.find ("/*MGEN:", cursor)
      if sub == -1:
        if self.writing:
          stream.write (line[cursor:len (line)])
        return

      subend = line.find("*/", sub)
      if self.writing:
        stream.write (line[cursor:sub])
      cursor = subend + 2

      tag = line[sub:subend]

      if tag[7:10] == "IF(":
        close = tag.find(")")
        if close == -1:
          raise ValueError ("Missing ')' on condition")
        cond = tag[10:close]
        dotPos = cond.find (".")
        if dotPos == -1:
          raise ValueError ("Invalid condition tag: %s" % cond)
        tagObject = cond[0:dotPos]
        tagName   = cond[dotPos + 1 : len(cond)]
        if not self.handler.testCondition(object, tagObject, tagName):
          self.writing = False

      elif tag[7:12] == "ENDIF":
        self.writing = True

      else:
        equalPos = tag.find ("=")
        if equalPos == -1:
          dotPos = tag.find (".")
          if dotPos == -1:
            raise ValueError ("Invalid tag: %s" % tag)
          tagObject = tag[7:dotPos]
          tagName   = tag[dotPos + 1:len (tag)]
          if self.writing:
            self.handler.substHandler (object, stream, tagObject, tagName)
        else:
          tagKey = tag[7:equalPos]
          tagVal = tag[equalPos + 1:len (tag)]
          if self.writing:
            self.handler.setVariable (tagKey, tagVal)

  def expand (self, object):
    fd     = open (self.filename)
    stream = StringIO ()

    for line in fd:
      self.expandLine (line, stream, object)
    fd.close ()

    return stream


class Makefile:
  """ Object representing a makefile fragment """
  def __init__ (self, filelists, templateFiles):
    self.filelists     = filelists
    self.templateFiles = templateFiles

  def genGenSources (self, stream, variables):
    mdir = variables["mgenDir"]
    sdir = variables["specDir"]
    stream.write (mdir + "/main.py \\\n")
    stream.write ("    " + mdir + "/generate.py \\\n")
    stream.write ("    " + mdir + "/schema.py \\\n")
    stream.write ("    " + sdir + "/management-types.xml \\\n")
    stream.write ("    " + sdir + "/management-schema.xml \\\n")
    first = True
    for template in self.templateFiles:
      if first:
        first = False
        stream.write ("    ")
      else:
        stream.write (" \\\n    ")
      stream.write (mdir + "/templates/" + template)

  def genGenCppFiles (self, stream, variables):
    first = True
    for file in self.filelists["cpp"]:
      if first:
        first = False
      else:
        stream.write (" \\\n    ")
      stream.write (file)

  def genGenHFiles (self, stream, variables):
    first = True
    for file in self.filelists["h"]:
      if first:
        first = False
      else:
        stream.write (" \\\n    ")
      stream.write (file)


class Generator:
  """
  This class manages code generation using template files.  It is instantiated
  once for an entire code generation session.
  """
  def createPath (self, path):
    exists = True
    try:
      mode = os.stat (path)[ST_MODE]
    except OSError, (err,text):
      if err == ENOENT:
        exists = False
      else:
        raise
    if exists and not S_ISDIR (mode):
      raise ValueError ("path is not directory: %s" % path)
    if not exists:
      pair = os.path.split (path)
      self.createPath (pair[0])
      os.mkdir (path)

  def normalize (self, path):
    newpath = os.path.normcase (os.path.normpath (path))
    self.createPath (newpath)
    return newpath + "/"
  
  def __init__ (self, destDir, templateDir):
    self.dest      = self.normalize (destDir)
    self.input     = self.normalize (templateDir)
    self.filelists = {}
    self.filelists["h"]   = []
    self.filelists["cpp"] = []
    self.filelists["mk"]  = []
    self.templateFiles    = []
    self.variables        = {}

  def genDisclaimer (self, stream, variables):
    prefix = variables["commentPrefix"]
    stream.write (prefix + " This source file was created by a code generator.\n")
    stream.write (prefix + " Please do not edit.")

  def fileExt (self, path):
    dot = path.rfind (".")
    if dot == -1:
      return ""
    return path[dot + 1:]

  def writeIfChanged (self, stream, target, force=False):
    ext = self.fileExt (target)
    self.filelists[ext].append (target)
    tempFile = self.dest + "gen.tmp"
    fd = open (tempFile, "w")
    fd.write  (stream.getvalue ())
    fd.close  ()

    try:
      if not force and filecmp.cmp (target, tempFile):
        os.remove (tempFile)
        return
    except:
      pass

    try:
      os.remove (target)
    except:
      pass

    os.rename (tempFile, target)
    print "Generated:", target

  def targetPackageFile (self, schema, templateFile):
    dot = templateFile.find(".")
    if dot == -1:
      raise ValueError ("Invalid template file name %s" % templateFile)
    extension = templateFile[dot:len (templateFile)]
    path = self.dest + "Package" + schema.getPackageNameCap() + extension
    return path

  def targetClassFile (self, _class, templateFile):
    dot = templateFile.find(".")
    if dot == -1:
      raise ValueError ("Invalid template file name %s" % templateFile)
    extension = templateFile[dot:len (templateFile)]
    path = self.dest + _class.getNameCap () + extension
    return path

  def targetMethodFile (self, method, templateFile):
    """ Return the file name for a method file """
    dot = templateFile.rfind(".")
    if dot == -1:
      raise ValueError ("Invalid template file name %s" % templateFile)
    extension = templateFile[dot:]
    path = self.dest + "Args" + method.getFullName () + extension
    return path

  def initExpansion (self):
    self.variables = {}

  def substHandler (self, object, stream, tagObject, tag):
    if tagObject == "Root":
      obj = "self"
    else:
      obj = "object"  # MUST be the same as the 2nd formal parameter

    call = obj + ".gen" + tag + "(stream, self.variables)"
    eval (call)

  def testCondition (self, object, tagObject, tag):
    if tagObject == "Root":
      obj = "self"
    else:
      obj = "object"  # MUST be the same as the 2nd formal parameter

    call = obj + ".test" + tag + "(self.variables)"
    return eval (call)

  def setVariable (self, key, value):
    self.variables[key] = value

  def makeClassFiles (self, templateFile, schema, force=False):
    """ Generate an expanded template per schema class """
    classes  = schema.getClasses ()
    template = Template (self.input + templateFile, self)
    self.templateFiles.append (templateFile)
    for _class in classes:
      target = self.targetClassFile (_class, templateFile)
      stream = template.expand (_class)
      self.writeIfChanged (stream, target, force)

  def makeMethodFiles (self, templateFile, schema, force=False):
    """ Generate an expanded template per method-with-arguments """
    classes  = schema.getClasses ()
    template = Template (self.input + templateFile, self)
    self.templateFiles.append (templateFile)
    for _class in classes:
      methods = _class.getMethods ()
      for method in methods:
        if method.getArgCount () > 0:
          target = self.targetMethodFile (method, templateFile)
          stream = template.expand (method)
          self.writeIfChanged (stream, target, force)

  def makePackageFile (self, templateFile, schema, force=False):
    """ Generate a package-specific file """
    template = Template (self.input + templateFile, self)
    self.templateFiles.append (templateFile)
    target = self.targetPackageFile (schema, templateFile)
    stream = template.expand (schema)
    self.writeIfChanged (stream, target, force)

  def makeSingleFile (self, templateFile, target, force=False):
    """ Generate a single expanded template """
    makefile = Makefile (self.filelists, self.templateFiles)
    template = Template (self.input + templateFile, self)
    self.templateFiles.append (templateFile)
    stream = template.expand (makefile)
    self.writeIfChanged (stream, target, force)
