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
This module loads protocol metadata into python objects. It provides
access to spec metadata via a python object model, and can also
dynamically creating python methods, classes, and modules based on the
spec metadata. All the generated methods have proper signatures and
doc strings based on the spec metadata so the python help system can
be used to browse the spec documentation. The generated methods all
dispatch to the self.invoke(meth, args) callback of the containing
class so that the generated code can be reused in a variety of
situations.
"""

import re, textwrap, new, xmlutil

class SpecContainer:

  def __init__(self):
    self.items = []
    self.byname = {}
    self.byid = {}
    self.indexes = {}
    self.bypyname = {}

  def add(self, item):
    if self.byname.has_key(item.name):
      raise ValueError("duplicate name: %s" % item)
    if self.byid.has_key(item.id):
      raise ValueError("duplicate id: %s" % item)
    pyname = pythonize(item.name)
    if self.bypyname.has_key(pyname):
      raise ValueError("duplicate pyname: %s" % item)
    self.indexes[item] = len(self.items)
    self.items.append(item)
    self.byname[item.name] = item
    self.byid[item.id] = item
    self.bypyname[pyname] = item

  def index(self, item):
    try:
      return self.indexes[item]
    except KeyError:
      raise ValueError(item)

  def __iter__(self):
    return iter(self.items)

  def __len__(self):
    return len(self.items)

class Metadata:

  PRINT = []

  def __init__(self):
    pass

  def __str__(self):
    args = map(lambda f: "%s=%s" % (f, getattr(self, f)), self.PRINT)
    return "%s(%s)" % (self.__class__.__name__, ", ".join(args))

  def __repr__(self):
    return str(self)

class Spec(Metadata):

  PRINT=["major", "minor", "file"]

  def __init__(self, major, minor, file):
    Metadata.__init__(self)
    self.major = major
    self.minor = minor
    self.file = file
    self.constants = SpecContainer()
    self.classes = SpecContainer()

  def post_load(self):
    self.module = self.define_module("amqp%s%s" % (self.major, self.minor))
    self.klass = self.define_class("Amqp%s%s" % (self.major, self.minor))

  def parse_method(self, name):
    parts = re.split(r"\s*\.\s*", name)
    if len(parts) != 2:
      raise ValueError(name)
    klass, meth = parts
    return self.classes.byname[klass].methods.byname[meth]

  def define_module(self, name, doc = None):
    module = new.module(name, doc)
    module.__file__ = self.file
    for c in self.classes:
      classname = pythonize(c.name)
      cls = c.define_class(classname)
      cls.__module__ = module.__name__
      setattr(module, classname, cls)
    return module

  def define_class(self, name):
    methods = {}
    for c in self.classes:
      for m in c.methods:
        meth = pythonize(m.klass.name + "_" + m.name)
        methods[meth] = m.define_method(meth)
    return type(name, (), methods)

class Constant(Metadata):

  PRINT=["name", "id"]

  def __init__(self, spec, name, id, klass, docs):
    Metadata.__init__(self)
    self.spec = spec
    self.name = name
    self.id = id
    self.klass = klass
    self.docs = docs

class Class(Metadata):

  PRINT=["name", "id"]

  def __init__(self, spec, name, id, handler, docs):
    Metadata.__init__(self)
    self.spec = spec
    self.name = name
    self.id = id
    self.handler = handler
    self.fields = SpecContainer()
    self.methods = SpecContainer()
    self.docs = docs

  def define_class(self, name):
    methods = {}
    for m in self.methods:
      meth = pythonize(m.name)
      methods[meth] = m.define_method(meth)
    return type(name, (), methods)

class Method(Metadata):

  PRINT=["name", "id"]

  def __init__(self, klass, name, id, content, responses, synchronous,
               description, docs):
    Metadata.__init__(self)
    self.klass = klass
    self.name = name
    self.id = id
    self.content = content
    self.responses = responses
    self.synchronous = synchronous
    self.fields = SpecContainer()
    self.description = description
    self.docs = docs
    self.response = False

  def docstring(self):
    s = "\n\n".join([fill(d, 2) for d in [self.description] + self.docs])
    for f in self.fields:
      if f.docs:
        s += "\n\n" + "\n\n".join([fill(f.docs[0], 4, pythonize(f.name))] +
                                  [fill(d, 4) for d in f.docs[1:]])
    return s

  METHOD = "__method__"
  DEFAULTS = {"bit": False,
              "shortstr": "",
              "longstr": "",
              "table": {},
              "octet": 0,
              "short": 0,
              "long": 0,
              "longlong": 0,
              "timestamp": 0,
              "content": None}

  def define_method(self, name):
    g = {Method.METHOD: self}
    l = {}
    args = [(pythonize(f.name), Method.DEFAULTS[f.type]) for f in self.fields]
    if self.content:
      args += [("content", None)]
    code = "def %s(self, %s):\n" % \
           (name, ", ".join(["%s = %r" % a for a in args]))
    code += "  %r\n" % self.docstring()
    if self.content:
      methargs = args[:-1]
    else:
      methargs = args
    argnames = ", ".join([a[0] for a in methargs])
    code += "  return self.invoke(%s" % Method.METHOD
    if argnames:
      code += ", (%s,)" % argnames
    else:
      code += ", ()" 
    if self.content:
      code += ", content"
    code += ")"
    exec code in g, l
    return l[name]

class Field(Metadata):

  PRINT=["name", "id", "type"]

  def __init__(self, name, id, type, docs):
    Metadata.__init__(self)
    self.name = name
    self.id = id
    self.type = type
    self.docs = docs

def get_docs(nd):
  return [n.text for n in nd["doc"]]

def load_fields(nd, l, domains):
  for f_nd in nd["field"]:
    try:
      type = f_nd["@domain"]
    except KeyError:
      type = f_nd["@type"]
    while domains.has_key(type) and domains[type] != type:
      type = domains[type]
    l.add(Field(f_nd["@name"], f_nd.index(), type, get_docs(f_nd)))

def load(specfile):
  doc = xmlutil.parse(specfile)
  root = doc["amqp"][0]
  spec = Spec(int(root["@major"]), int(root["@minor"]), specfile)

  # constants
  for nd in root["constant"]:
    const = Constant(spec, nd["@name"], int(nd["@value"]), nd.get("@class"),
                     get_docs(nd))
    spec.constants.add(const)

  # domains are typedefs
  domains = {}
  for nd in root["domain"]:
    domains[nd["@name"]] = nd["@type"]

  # classes
  for c_nd in root["class"]:
    klass = Class(spec, c_nd["@name"], int(c_nd["@index"]), c_nd["@handler"],
                  get_docs(c_nd))
    load_fields(c_nd, klass.fields, domains)
    for m_nd in c_nd["method"]:
      meth = Method(klass, m_nd["@name"],
                    int(m_nd["@index"]),
                    m_nd.get_bool("@content", False),
                    [nd["@name"] for nd in m_nd["response"]],
                    m_nd.get_bool("@synchronous", False),
                    m_nd.text,
                    get_docs(m_nd))
      load_fields(m_nd, meth.fields, domains)
      klass.methods.add(meth)
    # resolve the responses
    for m in klass.methods:
      m.responses = [klass.methods.byname[r] for r in m.responses]
      for resp in m.responses:
        resp.response = True
    spec.classes.add(klass)
  spec.post_load()
  return spec

REPLACE = {" ": "_", "-": "_"}
KEYWORDS = {"global": "global_",
            "return": "return_"}

def pythonize(name):
  name = str(name)
  for key, val in REPLACE.items():
    name = name.replace(key, val)
  try:
    name = KEYWORDS[name]
  except KeyError:
    pass
  return name

def fill(text, indent, heading = None):
  sub = indent * " "
  if heading:
    init = (indent - 2) * " " + heading + " -- "
  else:
    init = sub
  w = textwrap.TextWrapper(initial_indent = init, subsequent_indent = sub)
  return w.fill(" ".join(text.split()))

class Rule(Metadata):

  PRINT = ["text", "implement", "tests"]

  def __init__(self, text, implement, tests, path):
    self.text = text
    self.implement = implement
    self.tests = tests
    self.path = path

def find_rules(node, rules):
  if node.name == "rule":
    rules.append(Rule(node.text, node.get("@implement"),
                      [ch.text for ch in node if ch.name == "test"],
                      node.path()))
  if node.name == "doc" and node.get("@name") == "rule":
    tests = []
    if node.has("@test"):
      tests.append(node["@test"])
    rules.append(Rule(node.text, None, tests, node.path()))
  for child in node:
    find_rules(child, rules)

def load_rules(specfile):
  rules = []
  find_rules(xmlutil.parse(specfile), rules)
  return rules

def test_summary():
  template = """
  <html><head><title>AMQP Tests</title></head>
  <body>
  <table width="80%%" align="center">
  %s
  </table>
  </body>
  </html>
  """
  rows = []
  for rule in load_rules("amqp.org/specs/amqp7.xml"):
    if rule.tests:
      tests = ", ".join(rule.tests)
    else:
      tests = "&nbsp;"
    rows.append('<tr bgcolor="#EEEEEE"><td><b>Path:</b> %s</td>'
                '<td><b>Implement:</b> %s</td>'
                '<td><b>Tests:</b> %s</td></tr>' %
                (rule.path[len("/root/amqp"):], rule.implement, tests))
    rows.append('<tr><td colspan="3">%s</td></tr>' % rule.text)
    rows.append('<tr><td colspan="3">&nbsp;</td></tr>')

  print template % "\n".join(rows)
