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
import re

TYPES = []

class Type:

  def __init__(self, name, pattern=None):
    self.name = name
    self.pattern = pattern
    if self.pattern:
      TYPES.append(self)

  def __repr__(self):
    return self.name

LBRACE = Type("LBRACE", r"\{")
RBRACE = Type("RBRACE", r"\}")
COLON = Type("COLON", r":")
COMMA = Type("COMMA", r",")
ID = Type("ID", r'[a-zA-Z_][a-zA-Z0-9_]*')
NUMBER = Type("NUMBER", r'[+-]?[0-9]*\.?[0-9]+')
STRING = Type("STRING", r""""(?:[^\\"]|\\.)*"|'(?:[^\\']|\\.)*'""")
WSPACE = Type("WSPACE", r"[ \n\r\t]+")
EOF = Type("EOF")

class Token:

  def __init__(self, type, value):
    self.type = type
    self.value = value

  def __repr__(self):
    return "%s: %r" % (self.type, self.value)

joined = "|".join(["(%s)" % t.pattern for t in TYPES])
LEXER = re.compile(joined)

def lex(st):
  pos = 0
  while pos < len(st):
    m = LEXER.match(st, pos)
    if m is None:
      raise ValueError(repr(st[pos:]))
    else:
      idx = m.lastindex
      t = Token(TYPES[idx - 1], m.group(idx))
      yield t
    pos = m.end()
  yield Token(EOF, None)

class ParseError(Exception): pass

class EOF(Exception): pass

class Parser:

  def __init__(self, tokens):
    self.tokens = [t for t in tokens if t.type is not WSPACE]
    self.idx = 0

  def next(self):
    return self.tokens[self.idx]

  def matches(self, *types):
    return self.next().type in types

  def eat(self, *types):
    if types and not self.matches(*types):
      raise ParseError("expecting %s -- got %s" % (", ".join(map(str, types)), self.next()))
    else:
      t = self.next()
      self.idx += 1
      return t

  def parse(self):
    result = self.address()
    self.eat(EOF)
    return result

  def address(self):
    name = self.eat(ID).value
    if self.matches(LBRACE):
      options = self.map()
    else:
      options = None
    return name, options

  def map(self):
    self.eat(LBRACE)
    result = {}
    while True:
      if self.matches(RBRACE):
        self.eat(RBRACE)
        break
      else:
        if self.matches(ID):
          n, v = self.nameval()
          result[n] = v
        elif self.matches(COMMA):
          self.eat(COMMA)
        else:
          raise ParseError("expecting (ID, COMMA), got %s" % self.next())
    return result

  def nameval(self):
    name = self.eat(ID).value
    self.eat(COLON)
    val = self.value()
    return (name, val)

  def value(self):
    if self.matches(NUMBER, STRING):
      return eval(self.eat().value)
    elif self.matches(LBRACE):
      return self.map()
    else:
      raise ParseError("expecting (NUMBER, STRING, LBRACE) got %s" % self.next())

def parse(addr):
  return Parser(lex(addr)).parse()

__all__ = ["parse"]
