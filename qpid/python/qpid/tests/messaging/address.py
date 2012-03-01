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


from qpid.tests import Test
from qpid.messaging.address import lex, parse, ParseError, EOF, ID, NUMBER, \
    SYM, WSPACE, LEXER
from qpid.lexer import Token
from qpid.harness import Skipped
from qpid.tests.parser import ParserBase

def indent(st):
  return "  " + st.replace("\n", "\n  ")

def pprint_address(name, subject, options):
  return "NAME: %s\nSUBJECT: %s\nOPTIONS: %s" % \
      (pprint(name), pprint(subject), pprint(options))

def pprint(o):
  if isinstance(o, dict):
    return pprint_map(o)
  elif isinstance(o, list):
    return pprint_list(o)
  elif isinstance(o, basestring):
    return pprint_string(o)
  else:
    return repr(o)

def pprint_map(m):
  items = ["%s: %s" % (pprint(k), pprint(v)) for k, v in m.items()]
  items.sort()
  return pprint_items("{", items, "}")

def pprint_list(l):
  return pprint_items("[", [pprint(x) for x in l], "]")

def pprint_items(start, items, end):
  if items:
    return "%s\n%s\n%s" % (start, ",\n".join([indent(i) for i in items]), end)
  else:
    return "%s%s" % (start, end)

def pprint_string(s):
  result = "'"
  for c in s:
    if c == "'":
      result += "\\'"
    elif c == "\n":
      result += "\\n"
    elif ord(c) >= 0x80:
      result += "\\u%04x" % ord(c)
    else:
      result += c
  result += "'"
  return result

class AddressTests(ParserBase, Test):

  EXCLUDE = (WSPACE, EOF)

  def fields(self, line, n):
    result = line.split(":", n - 1)
    result.extend([None]*(n - len(result)))
    return result

  def call(self, parser, mode, input):
    try:
      from subprocess import Popen, PIPE, STDOUT
      po = Popen([parser, mode], stdin=PIPE, stdout=PIPE, stderr=STDOUT)
    except ImportError, e:
      raise Skipped("%s" % e)
    except OSError, e:
      raise Skipped("%s: %s" % (e, parser))
    out, _ = po.communicate(input=input)
    return out

  def parser(self):
    return self.config.defines.get("address.parser")

  def do_lex(self, st):
    parser = self.parser()
    if parser:
      out = self.call(parser, "lex", st)
      lines = out.split("\n")
      toks = []
      for line in lines:
        if line.strip():
          name, position, value = self.fields(line, 3)
          toks.append(Token(LEXER.type(name), value, position, st))
      return toks
    else:
      return lex(st)

  def do_parse(self, st):
    return parse(st)

  def valid(self, addr, name=None, subject=None, options=None):
    parser = self.parser()
    if parser:
      got = self.call(parser, "parse", addr)
      expected = "%s\n" % pprint_address(name, subject, options)
      assert expected == got, "expected\n<EXP>%s</EXP>\ngot\n<GOT>%s</GOT>" % (expected, got)
    else:
      ParserBase.valid(self, addr, (name, subject, options))

  def invalid(self, addr, error=None):
    parser = self.parser()
    if parser:
      got = self.call(parser, "parse", addr)
      expected = "ERROR: %s\n" % error
      assert expected == got, "expected %r, got %r" % (expected, got)
    else:
      ParserBase.invalid(self, addr, error)

  def testDashInId1(self):
    self.lex("foo-bar", ID)

  def testDashInId2(self):
    self.lex("foo-3", ID)

  def testDashAlone1(self):
    self.lex("foo - bar", ID, SYM, ID)

  def testDashAlone2(self):
    self.lex("foo - 3", ID, SYM, NUMBER)

  def testLeadingDash(self):
    self.lex("-foo", SYM, ID)

  def testTrailingDash(self):
    self.lex("foo-", ID, SYM)

  def testNegativeNum(self):
    self.lex("-3", NUMBER)

  def testIdNum(self):
    self.lex("id1", ID)

  def testIdSpaceNum(self):
    self.lex("id 1", ID, NUMBER)

  def testHash(self):
    self.valid("foo/bar.#", "foo", "bar.#")

  def testStar(self):
    self.valid("foo/bar.*", "foo", "bar.*")

  def testColon(self):
    self.valid("foo.bar/baz.qux:moo:arf", "foo.bar", "baz.qux:moo:arf")

  def testOptions(self):
    self.valid("foo.bar/baz.qux:moo:arf; {key: value}",
               "foo.bar", "baz.qux:moo:arf", {"key": "value"})

  def testOptionsTrailingComma(self):
    self.valid("name/subject; {key: value,}", "name", "subject",
               {"key": "value"})

  def testOptionsNone(self):
    self.valid("name/subject; {key: None}", "name", "subject",
               {"key": None})

  def testSemiSubject(self):
    self.valid("foo.bar/'baz.qux;moo:arf'; {key: value}",
               "foo.bar", "baz.qux;moo:arf", {"key": "value"})

  def testCommaSubject(self):
    self.valid("foo.bar/baz.qux.{moo,arf}", "foo.bar", "baz.qux.{moo,arf}")

  def testCommaSubjectOptions(self):
    self.valid("foo.bar/baz.qux.{moo,arf}; {key: value}", "foo.bar",
               "baz.qux.{moo,arf}", {"key": "value"})

  def testUnbalanced(self):
    self.valid("foo.bar/baz.qux.{moo,arf; {key: value}", "foo.bar",
               "baz.qux.{moo,arf", {"key": "value"})

  def testSlashQuote(self):
    self.valid("foo.bar\\/baz.qux.{moo,arf; {key: value}",
               "foo.bar/baz.qux.{moo,arf",
               None, {"key": "value"})

  def testSlashHexEsc1(self):
    self.valid("foo.bar\\x00baz.qux.{moo,arf; {key: value}",
               "foo.bar\x00baz.qux.{moo,arf",
               None, {"key": "value"})

  def testSlashHexEsc2(self):
    self.valid("foo.bar\\xffbaz.qux.{moo,arf; {key: value}",
               "foo.bar\xffbaz.qux.{moo,arf",
               None, {"key": "value"})

  def testSlashHexEsc3(self):
    self.valid("foo.bar\\xFFbaz.qux.{moo,arf; {key: value}",
               "foo.bar\xFFbaz.qux.{moo,arf",
               None, {"key": "value"})

  def testSlashUnicode1(self):
    self.valid("foo.bar\\u1234baz.qux.{moo,arf; {key: value}",
               u"foo.bar\u1234baz.qux.{moo,arf", None, {"key": "value"})

  def testSlashUnicode2(self):
    self.valid("foo.bar\\u0000baz.qux.{moo,arf; {key: value}",
               u"foo.bar\u0000baz.qux.{moo,arf", None, {"key": "value"})

  def testSlashUnicode3(self):
    self.valid("foo.bar\\uffffbaz.qux.{moo,arf; {key: value}",
               u"foo.bar\uffffbaz.qux.{moo,arf", None, {"key": "value"})

  def testSlashUnicode4(self):
    self.valid("foo.bar\\uFFFFbaz.qux.{moo,arf; {key: value}",
               u"foo.bar\uFFFFbaz.qux.{moo,arf", None, {"key": "value"})

  def testNoName(self):
    self.invalid("; {key: value}",
                 "unexpected token SEMI(;) line:1,0:; {key: value}")

  def testEmpty(self):
    self.invalid("", "unexpected token EOF line:1,0:")

  def testNoNameSlash(self):
    self.invalid("/asdf; {key: value}",
                 "unexpected token SLASH(/) line:1,0:/asdf; {key: value}")

  def testBadOptions1(self):
    self.invalid("name/subject; {",
                 "expecting (NUMBER, STRING, ID, LBRACE, LBRACK, RBRACE), "
                 "got EOF line:1,15:name/subject; {")

  def testBadOptions2(self):
    self.invalid("name/subject; { 3",
                 "expecting COLON, got EOF "
                 "line:1,17:name/subject; { 3")

  def testBadOptions3(self):
    self.invalid("name/subject; { key:",
                 "expecting (NUMBER, STRING, ID, LBRACE, LBRACK), got EOF "
                 "line:1,20:name/subject; { key:")

  def testBadOptions4(self):
    self.invalid("name/subject; { key: value",
                 "expecting (COMMA, RBRACE), got EOF "
                 "line:1,26:name/subject; { key: value")

  def testBadOptions5(self):
    self.invalid("name/subject; { key: value asdf",
                 "expecting (COMMA, RBRACE), got ID(asdf) "
                 "line:1,27:name/subject; { key: value asdf")

  def testBadOptions6(self):
    self.invalid("name/subject; { key: value,",
                 "expecting (NUMBER, STRING, ID, LBRACE, LBRACK, RBRACE), got EOF "
                 "line:1,27:name/subject; { key: value,")

  def testBadOptions7(self):
    self.invalid("name/subject; { key: value } asdf",
                 "expecting EOF, got ID(asdf) "
                 "line:1,29:name/subject; { key: value } asdf")

  def testList1(self):
    self.valid("name/subject; { key: [] }", "name", "subject", {"key": []})

  def testList2(self):
    self.valid("name/subject; { key: ['one'] }", "name", "subject", {"key": ['one']})

  def testList3(self):
    self.valid("name/subject; { key: [1, 2, 3] }", "name", "subject",
               {"key": [1, 2, 3]})

  def testList4(self):
    self.valid("name/subject; { key: [1, [2, 3], 4] }", "name", "subject",
               {"key": [1, [2, 3], 4]})

  def testBadList1(self):
    self.invalid("name/subject; { key: [ }", "expecting (NUMBER, STRING, ID, LBRACE, LBRACK), "
                 "got RBRACE(}) line:1,23:name/subject; { key: [ }")

  def testBadList2(self):
    self.invalid("name/subject; { key: [ 1 }", "expecting (COMMA, RBRACK), "
                 "got RBRACE(}) line:1,25:name/subject; { key: [ 1 }")

  def testBadList3(self):
    self.invalid("name/subject; { key: [ 1 2 }", "expecting (COMMA, RBRACK), "
                 "got NUMBER(2) line:1,25:name/subject; { key: [ 1 2 }")

  def testBadList4(self):
    self.invalid("name/subject; { key: [ 1 2 ] }", "expecting (COMMA, RBRACK), "
                 "got NUMBER(2) line:1,25:name/subject; { key: [ 1 2 ] }")

  def testMap1(self):
    self.valid("name/subject; { 'key': value }",
               "name", "subject", {"key": "value"})

  def testMap2(self):
    self.valid("name/subject; { 1: value }", "name", "subject", {1: "value"})

  def testMap3(self):
    self.valid('name/subject; { "foo.bar": value }',
               "name", "subject", {"foo.bar": "value"})

  def testBoolean(self):
    self.valid("name/subject; { true1: True, true2: true, "
               "false1: False, false2: false }",
               "name", "subject", {"true1": True, "true2": True,
                                   "false1": False, "false2": False})
