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
import os, mllib, cPickle as pickle, sys
from util import fill

class Primitive(object):
  pass

class Enum(object):

  # XXX: for backwards compatibility
  def values(cls):
    print >> sys.stderr, "warning, please use .VALUES instead of .values()"
    return cls.VALUES
  # we can't use the backport preprocessor here because this code gets
  # called by setup.py
  values = classmethod(values)

class Field:

  def __init__(self, name, type, default=None):
    self.name = name
    self.type = type
    self.default = default

  def __repr__(self):
    return "%s: %s" % (self.name, self.type)

class Compound(object):

  UNENCODED=[]

  def __init__(self, *args, **kwargs):
    args = list(args)
    for f in self.ARGS:
      if args:
        a = args.pop(0)
      else:
        a = kwargs.pop(f.name, f.default)
      setattr(self, f.name, a)
    if args:
      raise TypeError("%s takes at most %s arguments (%s given))" %
                      (self.__class__.__name__, len(self.ARGS),
                       len(self.ARGS) + len(args)))
    if kwargs:
      raise TypeError("got unexpected keyword argument '%s'" % kwargs.keys()[0])

  def fields(self):
    result = {}
    for f in self.FIELDS:
      result[f.name] = getattr(self, f.name)
    return result

  def args(self):
    result = {}
    for f in self.ARGS:
      result[f.name] = getattr(self, f.name)
    return result

  def __getitem__(self, attr):
    return getattr(self, attr)

  def __setitem__(self, attr, value):
    setattr(self, attr, value)

  def dispatch(self, target, *args):
    handler = "do_%s" % self.NAME
    getattr(target, handler)(self, *args)

  def __repr__(self, extras=()):
    return "%s(%s)" % (self.__class__.__name__,
                       ", ".join(["%s=%r" % (f.name, getattr(self, f.name))
                                  for f in self.ARGS
                                  if getattr(self, f.name) != f.default]))

class Command(Compound):
  UNENCODED=[Field("channel", "uint16", 0),
             Field("id", "sequence-no", None),
             Field("sync", "bit", False),
             Field("headers", None, None),
             Field("payload", None, None)]

class Control(Compound):
  UNENCODED=[Field("channel", "uint16", 0)]

def pythonize(st):
  if st is None:
    return None
  else:
    return str(st.replace("-", "_"))

def pydoc(op, children=()):
  doc = "\n\n".join([fill(p.text(), 0) for p in op.query["doc"]])
  for ch in children:
    doc += "\n\n  " + pythonize(ch["@name"]) + " -- " + str(ch["@label"])
    ch_descs ="\n\n".join([fill(p.text(), 4) for p in ch.query["doc"]])
    if ch_descs:
      doc += "\n\n" + ch_descs
  return doc

def studly(st):
  return "".join([p.capitalize() for p in st.split("-")])

def klass(nd):
  while nd.parent is not None:
    if hasattr(nd.parent, "name") and nd.parent.name == "class":
      return nd.parent
    else:
      nd = nd.parent

def included(nd):
  cls = klass(nd)
  if cls is None:
    return True
  else:
    return cls["@name"] not in ("file", "stream")

def num(s):
  if s: return int(s, 0)

def code(nd):
  c = num(nd["@code"])
  if c is None:
    return None
  else:
    cls = klass(nd)
    if cls is None:
      return c
    else:
      return c | (num(cls["@code"]) << 8)

def default(f):
  if f["@type"] == "bit":
    return False
  else:
    return None

def make_compound(decl, base, domains):
  dict = {}
  fields = decl.query["field"]
  dict["__doc__"] = pydoc(decl, fields)
  dict["NAME"] = pythonize(decl["@name"])
  dict["SIZE"] = num(decl["@size"])
  dict["CODE"] = code(decl)
  dict["PACK"] = num(decl["@pack"])
  dict["FIELDS"] = [Field(pythonize(f["@name"]), resolve(f, domains),
                          default(f))
                    for f in fields]
  dict["ARGS"] = dict["FIELDS"] + base.UNENCODED
  return str(studly(decl["@name"])), (base,), dict

def make_restricted(decl, domains):
  name = pythonize(decl["@name"])
  dict = {}
  choices = decl.query["choice"]
  dict["__doc__"] = pydoc(decl, choices)
  dict["NAME"] = name
  dict["TYPE"] = str(decl.parent["@type"])
  values = []
  for ch in choices:
    val = int(ch["@value"], 0)
    dict[pythonize(ch["@name"])] = val
    values.append(val)
  dict["VALUES"] = values
  return name, (Enum,), dict

def make_type(decl, domains):
  name = pythonize(decl["@name"])
  dict = {}
  dict["__doc__"] = pydoc(decl)
  dict["NAME"] = name
  dict["CODE"] = code(decl)
  return str(studly(decl["@name"])), (Primitive,), dict

def make_command(decl, domains):
  decl.set_attr("name", "%s-%s" % (decl.parent["@name"], decl["@name"]))
  decl.set_attr("size", "0")
  decl.set_attr("pack", "2")
  name, bases, dict = make_compound(decl, Command, domains)
  dict["RESULT"] = pythonize(decl["result/@type"]) or pythonize(decl["result/struct/@name"])
  return name, bases, dict

def make_control(decl, domains):
  decl.set_attr("name", "%s-%s" % (decl.parent["@name"], decl["@name"]))
  decl.set_attr("size", "0")
  decl.set_attr("pack", "2")
  return make_compound(decl, Control, domains)

def make_struct(decl, domains):
  return make_compound(decl, Compound, domains)

def make_enum(decl, domains):
  decl.set_attr("name", decl.parent["@name"])
  return make_restricted(decl, domains)


vars = globals()

def make(nd, domains):
  return vars["make_%s" % nd.name](nd, domains)

def qualify(nd, field="@name"):
  cls = klass(nd)
  if cls is None:
    return pythonize(nd[field])
  else:
    return pythonize("%s.%s" % (cls["@name"], nd[field]))

def resolve(nd, domains):
  candidates = qualify(nd, "@type"), pythonize(nd["@type"])
  for c in candidates:
    if domains.has_key(c):
      while domains.has_key(c):
        c = domains[c]
      return c
  else:
    return c

def load_types_from_xml(file):
  spec = mllib.xml_parse(file)
  domains = dict([(qualify(d), pythonize(d["@type"]))
                  for d in spec.query["amqp/domain", included] + \
                    spec.query["amqp/class/domain", included]])
  type_decls = \
      spec.query["amqp/class/command", included] + \
      spec.query["amqp/class/control", included] + \
      spec.query["amqp/class/command/result/struct", included] + \
      spec.query["amqp/class/struct", included] + \
      spec.query["amqp/class/domain/enum", included] + \
      spec.query["amqp/domain/enum", included] + \
      spec.query["amqp/type"]
  types = [make(nd, domains) for nd in type_decls]
  return types

def load_types(file):
  base, ext = os.path.splitext(file)
  pclfile = "%s.pcl" % base
  if os.path.exists(pclfile) and \
        os.path.getmtime(pclfile) > os.path.getmtime(file):
    f = open(pclfile, "rb")
    types = pickle.load(f)
    f.close()
  else:
    types = load_types_from_xml(file)
    if os.access(os.path.dirname(os.path.abspath(pclfile)), os.W_OK):
      f = open(pclfile, "wb")
      pickle.dump(types, f)
      f.close()
  return types

from specs_config import amqp_spec as file
types = load_types(file)

ENUMS = {}
PRIMITIVE = {}
COMPOUND = {}
COMMANDS = {}
CONTROLS = {}

for name, bases, _dict in types:
  t = type(name, bases, _dict)
  vars[name] = t

  if issubclass(t, Command):
    COMMANDS[t.NAME] = t
    COMMANDS[t.CODE] = t
  elif issubclass(t, Control):
    CONTROLS[t.NAME] = t
    CONTROLS[t.CODE] = t
  elif issubclass(t, Compound):
    COMPOUND[t.NAME] = t
    if t.CODE is not None:
      COMPOUND[t.CODE] = t
  elif issubclass(t, Primitive):
    PRIMITIVE[t.NAME] = t
    PRIMITIVE[t.CODE] = t
  elif issubclass(t, Enum):
    ENUMS[t.NAME] = t
