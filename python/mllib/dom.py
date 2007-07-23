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
Simple DOM for both SGML and XML documents.
"""

from transforms import Text

class Container:

  def __init__(self):
    self.children = []

  def add(self, child):
    child.parent = self
    self.children.append(child)

  def extend(self, children):
    for child in children:
      child.parent = self
      self.children.append(child)

class Component:

  def __init__(self):
    self.parent = None

  def index(self):
    if self.parent:
      return self.parent.children.index(self)
    else:
      return 0

  def _line(self, file, line, column):
    self.file = file
    self.line = line
    self.column = column

class DispatchError(Exception):

  def __init__(self, scope, f):
    msg = "no such attribtue"

class Dispatcher:

  def is_type(self, type):
    cls = self
    while cls != None:
      if cls.type == type:
        return True
      cls = cls.base
    return False

  def dispatch(self, f):
    cls = self
    while cls != None:
      if hasattr(f, cls.type):
        return getattr(f, cls.type)(self)
      else:
        cls = cls.base

    cls = self
    attrs = ""
    while cls != None:
      if attrs:
        sep = ", "
        if cls.base == None:
          sep += "or "
      else:
        sep = ""
      attrs += "%s'%s'" % (sep, cls.type)
      cls = cls.base

    raise AttributeError("'%s' object has no attribute %s" %
                         (f.__class__.__name__, attrs))

class Node(Container, Component, Dispatcher):

  type = "node"
  base = None

  def __init__(self):
    Container.__init__(self)
    Component.__init__(self)
    self.query = Query([self])

  def __getitem__(self, name):
    for nd in self.query[name]:
      return nd

  def text(self):
    return self.dispatch(Text())

  def tag(self, name, *attrs, **kwargs):
    t = Tag(name, *attrs, **kwargs)
    self.add(t)
    return t

  def data(self, s):
    d = Data(s)
    self.add(d)
    return d

  def entity(self, s):
    e = Entity(s)
    self.add(e)
    return e

class Tree(Node):

  type = "tree"
  base = Node

class Tag(Node):

  type = "tag"
  base = Node

  def __init__(self, _name, *attrs, **kwargs):
    Node.__init__(self)
    self.name = _name
    self.attrs = list(attrs)
    self.attrs.extend(kwargs.items())
    self.singleton = False

  def get_attr(self, name):
    for k, v in self.attrs:
      if name == k:
        return v

  def __getitem__(self, name):
    if name and name[0] == "@":
      return self.get_attr(name[1:])
    else:
      for nd in self.query[name]:
        return nd
      return self.get_attr(name)

  def dispatch(self, f):
    try:
      method = getattr(f, "do_" + self.name)
    except AttributeError:
      return Dispatcher.dispatch(self, f)
    return method(self)

class Leaf(Component, Dispatcher):

  type = "leaf"
  base = None

  def __init__(self, data):
    assert isinstance(data, basestring)
    self.data = data

class Data(Leaf):
  type = "data"
  base = Leaf

class Entity(Leaf):
  type = "entity"
  base = Leaf

class Character(Leaf):
  type = "character"
  base = Leaf

class Comment(Leaf):
  type = "comment"
  base = Leaf

###################
## Query Classes ##
###########################################################################

class View:

  def __init__(self, source):
    self.source = source

class Filter(View):

  def __init__(self, predicate, source):
    View.__init__(self, source)
    if callable(predicate):
      self.predicate = predicate
    elif predicate[0] == "#":
      type = predicate[1:]
      self.predicate = lambda x: x.is_type(type)
    else:
      self.predicate = lambda x: isinstance(x, Tag) and x.name == predicate

  def __iter__(self):
    for nd in self.source:
      if self.predicate(nd): yield nd

class Flatten(View):

  def __iter__(self):
    sources = [iter(self.source)]
    while sources:
      try:
        nd = sources[-1].next()
        if isinstance(nd, Tree):
          sources.append(iter(nd.children))
        else:
          yield nd
      except StopIteration:
        sources.pop()

class Children(View):

  def __iter__(self):
    for nd in self.source:
      for child in nd.children:
        yield child

class Query(View):

  def __iter__(self):
    for nd in self.source:
      yield nd

  def __getitem__(self, predicate):
    if isinstance(predicate, basestring):
      path = predicate.split("/")
    else:
      path = [predicate]

    query = self.source
    for p in path:
      query = Query(Filter(p, Flatten(Children(query))))

    return query
