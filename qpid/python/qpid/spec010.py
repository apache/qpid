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

import os, cPickle, datatypes, datetime
from codec010 import StringCodec
from util import mtime, fill

class Node:

  def __init__(self, children):
    self.children = children
    self.named = {}
    self.docs = []
    self.rules = []

  def register(self):
    for ch in self.children:
      ch.register(self)

  def resolve(self):
    for ch in self.children:
      ch.resolve()

  def __getitem__(self, name):
    path = name.split(".", 1)
    nd = self.named
    for step in path:
      nd = nd[step]
    return nd

  def __iter__(self):
    return iter(self.children)

class Anonymous:

  def __init__(self, children):
    self.children = children

  def register(self, node):
    for ch in self.children:
      ch.register(node)

  def resolve(self):
    for ch in self.children:
      ch.resolve()

class Named:

  def __init__(self, name):
    self.name = name
    self.qname = None

  def register(self, node):
    self.spec = node.spec
    self.klass = node.klass
    node.named[self.name] = self
    if node.qname:
      self.qname = "%s.%s" % (node.qname, self.name)
    else:
      self.qname = self.name

  def __str__(self):
    return self.qname

  def __repr__(self):
    return str(self)

class Lookup:

  def lookup(self, name):
    value = None
    if self.klass:
      try:
        value = self.klass[name]
      except KeyError:
        pass
    if not value:
      value = self.spec[name]
    return value

class Coded:

  def __init__(self, code):
    self.code = code

class Constant(Named, Node):

  def __init__(self, name, value, children):
    Named.__init__(self, name)
    Node.__init__(self, children)
    self.value = value

  def register(self, node):
    Named.register(self, node)
    node.constants.append(self)
    Node.register(self)

class Type(Named, Node):

  def __init__(self, name, children):
    Named.__init__(self, name)
    Node.__init__(self, children)

  def is_present(self, value):
    return value != None

  def register(self, node):
    Named.register(self, node)
    Node.register(self)

class Primitive(Coded, Type):

  def __init__(self, name, code, fixed, variable, children):
    Coded.__init__(self, code)
    Type.__init__(self, name, children)
    self.fixed = fixed
    self.variable = variable

  def register(self, node):
    Type.register(self, node)
    if self.code is not None:
      self.spec.types[self.code] = self

  def is_present(self, value):
    if self.fixed == 0:
      return value
    else:
      return Type.is_present(self, value)

  def encode(self, codec, value):
    getattr(codec, "write_%s" % self.name)(value)

  def decode(self, codec):
    return getattr(codec, "read_%s" % self.name)()

class Domain(Type, Lookup):

  def __init__(self, name, type, children):
    Type.__init__(self, name, children)
    self.type = type
    self.choices = {}

  def resolve(self):
    self.type = self.lookup(self.type)
    Node.resolve(self)

  def encode(self, codec, value):
    self.type.encode(codec, value)

  def decode(self, codec):
    return self.type.decode(codec)

class Enum:

  def __init__(self, name):
    self.name = name
    self._names = ()
    self._values = ()

  def values(self):
    return self._values

  def __repr__(self):
    return "%s(%s)" % (self.name, ", ".join(self._names))

class Choice(Named, Node):

  def __init__(self, name, value, children):
    Named.__init__(self, name)
    Node.__init__(self, children)
    self.value = value

  def register(self, node):
    Named.register(self, node)
    node.choices[self.value] = self
    Node.register(self)
    try:
      enum = node.spec.enums[node.name]
    except KeyError:
      enum = Enum(node.name)
      node.spec.enums[node.name] = enum
    setattr(enum, self.name, self.value)
    enum._names += (self.name,)
    enum._values += (self.value,)

class Composite(Type, Coded):

  def __init__(self, name, label, code, size, pack, children):
    Coded.__init__(self, code)
    Type.__init__(self, name, children)
    self.label = label
    self.fields = []
    self.size = size
    self.pack = pack

  def new(self, args, kwargs):
    return datatypes.Struct(self, *args, **kwargs)

  def decode(self, codec):
    codec.read_size(self.size)
    if self.code is not None:
      code = codec.read_uint16()
      assert self.code == code
    return datatypes.Struct(self, **self.decode_fields(codec))

  def decode_fields(self, codec):
    flags = 0
    for i in range(self.pack):
      flags |= (codec.read_uint8() << 8*i)

    result = {}

    for i in range(len(self.fields)):
      f = self.fields[i]
      if flags & (0x1 << i):
        result[f.name] = f.type.decode(codec)
      else:
        result[f.name] = None
    return result

  def encode(self, codec, value):
    sc = StringCodec(self.spec)
    if self.code is not None:
      sc.write_uint16(self.code)
    self.encode_fields(sc, value)
    codec.write_size(self.size, len(sc.encoded))
    codec.write(sc.encoded)

  def encode_fields(self, codec, values):
    flags = 0
    for i in range(len(self.fields)):
      f = self.fields[i]
      if f.type.is_present(values[f.name]):
        flags |= (0x1 << i)
    for i in range(self.pack):
      codec.write_uint8((flags >> 8*i) & 0xFF)
    for i in range(len(self.fields)):
      f = self.fields[i]
      if flags & (0x1 << i):
        f.type.encode(codec, values[f.name])

  def docstring(self):
    docs = []
    if self.label:
      docs.append(self.label)
    docs += [d.text for d in self.docs]
    s = "\n\n".join([fill(t, 2) for t in docs])
    for f in self.fields:
      fdocs = []
      if f.label:
        fdocs.append(f.label)
      else:
        fdocs.append("")
      fdocs += [d.text for d in f.docs]
      s += "\n\n" + "\n\n".join([fill(fdocs[0], 4, f.name)] +
                                [fill(t, 4) for t in fdocs[1:]])
    return s


class Field(Named, Node, Lookup):

  def __init__(self, name, label, type, children):
    Named.__init__(self, name)
    Node.__init__(self, children)
    self.label = label
    self.type = type
    self.exceptions = []

  def default(self):
    return None

  def register(self, node):
    Named.register(self, node)
    node.fields.append(self)
    Node.register(self)

  def resolve(self):
    self.type = self.lookup(self.type)
    Node.resolve(self)

  def __str__(self):
    return "%s: %s" % (self.qname, self.type.qname)

class Struct(Composite):

  def register(self, node):
    Composite.register(self, node)
    if self.code is not None:
      self.spec.structs[self.code] = self
    self.spec.structs_by_name[self.name] = self
    self.pyname = self.name
    self.pydoc = self.docstring()

  def __str__(self):
    fields = ",\n    ".join(["%s: %s" % (f.name, f.type.qname)
                             for f in self.fields])
    return "%s {\n    %s\n}" % (self.qname, fields)

class Segment:

  def __init__(self):
    self.segment_type = None

  def register(self, node):
    self.spec = node.spec
    self.klass = node.klass
    node.segments.append(self)
    Node.register(self)

class Instruction(Composite, Segment):

  def __init__(self, name, label, code, children):
    Composite.__init__(self, name, label, code, 0, 2, children)
    Segment.__init__(self)
    self.track = None
    self.handlers = []

  def __str__(self):
    return "%s(%s)" % (self.qname, ", ".join(["%s: %s" % (f.name, f.type.qname)
                                              for f in self.fields]))

  def register(self, node):
    Composite.register(self, node)
    self.pyname = self.qname.replace(".", "_")
    self.pydoc = self.docstring()
    self.spec.instructions[self.pyname] = self

class Control(Instruction):

  def __init__(self, name, code, label, children):
    Instruction.__init__(self, name, code, label, children)
    self.response = None

  def register(self, node):
    Instruction.register(self, node)
    node.controls.append(self)
    self.spec.controls[self.code] = self
    self.segment_type = self.spec["segment_type.control"].value
    self.track = self.spec["track.control"].value

class Command(Instruction):

  def __init__(self, name, label, code, children):
    Instruction.__init__(self, name, label, code, children)
    self.result = None
    self.exceptions = []
    self.segments = []

  def register(self, node):
    Instruction.register(self, node)
    node.commands.append(self)
    self.spec.commands[self.code] = self
    self.segment_type = self.spec["segment_type.command"].value
    self.track = self.spec["track.command"].value

class Header(Segment, Node):

  def __init__(self, children):
    Segment.__init__(self)
    Node.__init__(self, children)
    self.entries = []

  def register(self, node):
    Segment.register(self, node)
    self.segment_type = self.spec["segment_type.header"].value
    Node.register(self)

class Entry(Lookup):

  def __init__(self, type):
    self.type = type

  def register(self, node):
    self.spec = node.spec
    self.klass = node.klass
    node.entries.append(self)

  def resolve(self):
    self.type = self.lookup(self.type)

class Body(Segment, Node):

  def __init__(self, children):
    Segment.__init__(self)
    Node.__init__(self, children)

  def register(self, node):
    Segment.register(self, node)
    self.segment_type = self.spec["segment_type.body"].value
    Node.register(self)

  def resolve(self): pass

class Class(Named, Coded, Node):

  def __init__(self, name, code, children):
    Named.__init__(self, name)
    Coded.__init__(self, code)
    Node.__init__(self, children)
    self.controls = []
    self.commands = []

  def register(self, node):
    Named.register(self, node)
    self.klass = self
    node.classes.append(self)
    Node.register(self)

class Doc:

  def __init__(self, type, title, text):
    self.type = type
    self.title = title
    self.text = text

  def register(self, node):
    node.docs.append(self)

  def resolve(self): pass

class Role(Named, Node):

  def __init__(self, name, children):
    Named.__init__(self, name)
    Node.__init__(self, children)

  def register(self, node):
    Named.register(self, node)
    Node.register(self)

class Rule(Named, Node):

  def __init__(self, name, children):
    Named.__init__(self, name)
    Node.__init__(self, children)

  def register(self, node):
    Named.register(self, node)
    node.rules.append(self)
    Node.register(self)

class Exception(Named, Node):

  def __init__(self, name, error_code, children):
    Named.__init__(self, name)
    Node.__init__(self, children)
    self.error_code = error_code

  def register(self, node):
    Named.register(self, node)
    node.exceptions.append(self)
    Node.register(self)

class Spec(Node):

  ENCODINGS = {
    basestring: "vbin16",
    int: "int64",
    long: "int64",
    float: "float",
    None.__class__: "void",
    list: "list",
    tuple: "list",
    dict: "map",
    datatypes.timestamp: "datetime",
    datetime.datetime: "datetime"
    }

  def __init__(self, major, minor, port, children):
    Node.__init__(self, children)
    self.major = major
    self.minor = minor
    self.port = port
    self.constants = []
    self.classes = []
    self.types = {}
    self.qname = None
    self.spec = self
    self.klass = None
    self.instructions = {}
    self.controls = {}
    self.commands = {}
    self.structs = {}
    self.structs_by_name = {}
    self.enums = {}

  def encoding(self, klass):
    if Spec.ENCODINGS.has_key(klass):
      return self.named[Spec.ENCODINGS[klass]]
    for base in klass.__bases__:
      result = self.encoding(base)
      if result != None:
        return result

class Implement:

  def __init__(self, handle):
    self.handle = handle

  def register(self, node):
    node.handlers.append(self.handle)

  def resolve(self): pass

class Response(Node):

  def __init__(self, name, children):
    Node.__init__(self, children)
    self.name = name

  def register(self, node):
    Node.register(self)

class Result(Node, Lookup):

  def __init__(self, type, children):
    self.type = type
    Node.__init__(self, children)

  def register(self, node):
    node.result = self
    self.qname = node.qname
    self.klass = node.klass
    self.spec = node.spec
    Node.register(self)

  def resolve(self):
    self.type = self.lookup(self.type)
    Node.resolve(self)

import mllib

def num(s):
  if s: return int(s, 0)

REPLACE = {" ": "_", "-": "_"}
KEYWORDS = {"global": "global_",
            "return": "return_"}

def id(name):
  name = str(name)
  for key, val in REPLACE.items():
    name = name.replace(key, val)
  try:
    name = KEYWORDS[name]
  except KeyError:
    pass
  return name

class Loader:

  def __init__(self):
    self.class_code = 0

  def code(self, nd):
    c = num(nd["@code"])
    if c is None:
      return None
    else:
      return c | (self.class_code << 8)

  def list(self, q):
    result = []
    for nd in q:
      result.append(nd.dispatch(self))
    return result

  def children(self, n):
    return self.list(n.query["#tag"])

  def data(self, d):
    return d.data

  def do_amqp(self, a):
    return Spec(num(a["@major"]), num(a["@minor"]), num(a["@port"]),
                self.children(a))

  def do_type(self, t):
    return Primitive(id(t["@name"]), self.code(t), num(t["@fixed-width"]),
                     num(t["@variable-width"]), self.children(t))

  def do_constant(self, c):
    return Constant(id(c["@name"]), num(c["@value"]), self.children(c))

  def do_domain(self, d):
    return Domain(id(d["@name"]), id(d["@type"]), self.children(d))

  def do_enum(self, e):
    return Anonymous(self.children(e))

  def do_choice(self, c):
    return Choice(id(c["@name"]), num(c["@value"]), self.children(c))

  def do_class(self, c):
    code = num(c["@code"])
    self.class_code = code
    children = self.children(c)
    children += self.list(c.query["command/result/struct"])
    self.class_code = 0
    return Class(id(c["@name"]), code, children)

  def do_doc(self, doc):
    text = reduce(lambda x, y: x + y, self.list(doc.children))
    return Doc(doc["@type"], doc["@title"], text)

  def do_xref(self, x):
    return x["@ref"]

  def do_role(self, r):
    return Role(id(r["@name"]), self.children(r))

  def do_control(self, c):
    return Control(id(c["@name"]), c["@label"], self.code(c), self.children(c))

  def do_rule(self, r):
    return Rule(id(r["@name"]), self.children(r))

  def do_implement(self, i):
    return Implement(id(i["@handle"]))

  def do_response(self, r):
    return Response(id(r["@name"]), self.children(r))

  def do_field(self, f):
    return Field(id(f["@name"]), f["@label"], id(f["@type"]), self.children(f))

  def do_struct(self, s):
    return Struct(id(s["@name"]), s["@label"], self.code(s), num(s["@size"]),
                  num(s["@pack"]), self.children(s))

  def do_command(self, c):
    return Command(id(c["@name"]), c["@label"], self.code(c), self.children(c))

  def do_segments(self, s):
    return Anonymous(self.children(s))

  def do_header(self, h):
    return Header(self.children(h))

  def do_entry(self, e):
    return Entry(id(e["@type"]))

  def do_body(self, b):
    return Body(self.children(b))

  def do_result(self, r):
    type = r["@type"]
    if not type:
      type = r["struct/@name"]
    return Result(id(type), self.list(r.query["#tag", lambda x: x.name != "struct"]))

  def do_exception(self, e):
    return Exception(id(e["@name"]), id(e["@error-code"]), self.children(e))

def load(xml):
  fname = xml + ".pcl"

  if os.path.exists(fname) and mtime(fname) > mtime(__file__):
    file = open(fname, "r")
    s = cPickle.load(file)
    file.close()
  else:
    doc = mllib.xml_parse(xml)
    s = doc["amqp"].dispatch(Loader())
    s.register()
    s.resolve()

    try:
      file = open(fname, "w")
    except IOError:
      file = None

    if file:
      cPickle.dump(s, file)
      file.close()

  return s
