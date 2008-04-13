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

"""
XML utilities used by spec.py
"""

import xml.sax
from xml.sax.handler import ContentHandler

def parse(file):
  doc = Node("root")
  xml.sax.parse(file, Builder(doc))
  return doc

class Node:

  def __init__(self, name, attrs = None, text = None, parent = None):
    self.name = name
    self.attrs = attrs
    self.text = text
    self.parent = parent
    self.children = []
    if parent != None:
      parent.children.append(self)

  def get_bool(self, key, default = False):
    v = self.get(key)
    if v == None:
      return default
    else:
      return bool(int(v))

  def index(self):
    if self.parent:
      return self.parent.children.index(self)
    else:
      return 0

  def has(self, key):
    try:
      result = self[key]
      return True
    except KeyError:
      return False
    except IndexError:
      return False

  def get(self, key, default = None):
    if self.has(key):
      return self[key]
    else:
      return default

  def __getitem__(self, key):
    if callable(key):
      return filter(key, self.children)
    else:
      t = key.__class__
      meth = "__get%s__" % t.__name__
      if hasattr(self, meth):
        return getattr(self, meth)(key)
      else:
        raise KeyError(key)

  def __getstr__(self, name):
    if name[:1] == "@":
      return self.attrs[name[1:]]
    else:
      return self[lambda nd: nd.name == name]

  def __getint__(self, index):
    return self.children[index]

  def __iter__(self):
    return iter(self.children)

  def path(self):
    if self.parent == None:
      return "/%s" % self.name
    else:
      return "%s/%s" % (self.parent.path(), self.name)

class Builder(ContentHandler):

  def __init__(self, start = None):
    self.node = start

  def __setitem__(self, element, type):
    self.types[element] = type

  def startElement(self, name, attrs):
    self.node = Node(name, attrs, None, self.node)

  def endElement(self, name):
    self.node = self.node.parent

  def characters(self, content):
    if self.node.text == None:
      self.node.text = content
    else:
      self.node.text += content

