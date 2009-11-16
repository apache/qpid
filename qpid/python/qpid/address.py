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
SEMI = Type("SEMI", r";")
SLASH = Type("SLASH", r"/")
COMMA = Type("COMMA", r",")
NUMBER = Type("NUMBER", r'[+-]?[0-9]*\.?[0-9]+')
ID = Type("ID", r'[a-zA-Z_](?:[a-zA-Z0-9_-]*[a-zA-Z0-9_])?')
STRING = Type("STRING", r""""(?:[^\\"]|\\.)*"|'(?:[^\\']|\\.)*'""")
ESC = Type("ESC", r"\\[^ux]|\\x[0-9a-fA-F][0-9a-fA-F]|\\u[0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F]")
SYM = Type("SYM", r"[.#*%@$^!+-]")
WSPACE = Type("WSPACE", r"[ \n\r\t]+")
EOF = Type("EOF")

class Token:

  def __init__(self, type, value, input, position):
    self.type = type
    self.value = value
    self.input = input
    self.position = position

  def line_info(self):
    return line_info(self.input, self.position)

  def __repr__(self):
    if self.value is None:
      return repr(self.type)
    else:
      return "%s(%r)" % (self.type, self.value)

joined = "|".join(["(%s)" % t.pattern for t in TYPES])
LEXER = re.compile(joined)

class LexError(Exception):
  pass

def line_info(st, pos):
  idx = 0
  lineno = 1
  column = 0
  line_pos = 0
  while idx < pos:
    if st[idx] == "\n":
      lineno += 1
      column = 0
      line_pos = idx
    column += 1
    idx += 1

  end = st.find("\n", line_pos)
  if end < 0:
    end = len(st)
  line = st[line_pos:end]

  return line, lineno, column

def lex(st):
  pos = 0
  while pos < len(st):
    m = LEXER.match(st, pos)
    if m is None:
      line, ln, col = line_info(st, pos)
      raise LexError("unrecognized characters line:%s,%s: %s" % (ln, col, line))
    else:
      idx = m.lastindex
      t = Token(TYPES[idx - 1], m.group(idx), st, pos)
      yield t
    pos = m.end()
  yield Token(EOF, None, st, pos)

def tok2str(tok):
  if tok.type is STRING:
    return eval(tok.value)
  elif tok.type is ESC:
    if tok.value[1] == "x":
      return eval('"%s"' % tok.value)
    elif tok.value[1] == "u":
      return eval('u"%s"' % tok.value)
    else:
      return tok.value[1]
  else:
    return tok.value

def tok2obj(tok):
  if tok.type in (STRING, NUMBER):
    return eval(tok.value)
  else:
    return tok.value

def toks2str(toks):
  if toks:
    return "".join(map(tok2str, toks))
  else:
    return None

class ParseError(Exception):

  def __init__(self, token, *expected):
    line, ln, col = token.line_info()
    exp = ", ".join(map(str, expected))
    if len(expected) > 1:
      exp = "(%s)" % exp
    if expected:
      msg = "expecting %s, got %s line:%s,%s:%s" % (exp, token, ln, col, line)
    else:
      msg = "unexpected token %s line:%s,%s:%s" % (token, ln, col, line)
    Exception.__init__(self, msg)
    self.token = token
    self.expected = expected

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
      raise ParseError(self.next(), *types)
    else:
      t = self.next()
      self.idx += 1
      return t

  def eat_until(self, *types):
    result = []
    while not self.matches(*types):
      result.append(self.eat())
    return result

  def parse(self):
    result = self.address()
    self.eat(EOF)
    return result

  def address(self):
    name = toks2str(self.eat_until(SLASH, SEMI, EOF))

    if name is None:
      raise ParseError(self.next())

    if self.matches(SLASH):
      self.eat(SLASH)
      subject = toks2str(self.eat_until(SEMI, EOF))
    else:
      subject = None

    if self.matches(SEMI):
      self.eat(SEMI)
      options = self.map()
    else:
      options = None
    return name, subject, options

  def map(self):
    self.eat(LBRACE)

    result = {}
    while True:
      if self.matches(ID):
        n, v = self.nameval()
        result[n] = v
        if self.matches(COMMA):
          self.eat(COMMA)
        elif self.matches(RBRACE):
          break
        else:
          raise ParseError(self.next(), COMMA, RBRACE)
      elif self.matches(RBRACE):
        break
      else:
        raise ParseError(self.next(), ID, RBRACE)

    self.eat(RBRACE)
    return result

  def nameval(self):
    name = self.eat(ID).value
    self.eat(COLON)
    val = self.value()
    return (name, val)

  def value(self):
    if self.matches(NUMBER, STRING, ID):
      return tok2obj(self.eat())
    elif self.matches(LBRACE):
      return self.map()
    else:
      raise ParseError(self.next(), NUMBER, STRING, ID, LBRACE)

def parse(addr):
  return Parser(lex(addr)).parse()

__all__ = ["parse", "ParseError"]
