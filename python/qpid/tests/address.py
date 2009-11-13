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
from qpid.address import parse, ParseError

class AddressTests(Test):

  def valid(self, addr, name=None, subject=None, options=None):
    expected = (name, subject, options)
    got = parse(addr)
    assert expected == got, "expected %s, got %s" % (expected, got)

  def invalid(self, addr, error=None):
    try:
      p = parse(addr)
      assert False, "invalid address parsed: %s" % p
    except ParseError, e:
      assert error == str(e), "expected %r, got %r" % (error, str(e))

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
    self.valid("name/subject; {key: value,}", "name", "subject", {"key": "value"})

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
    self.valid("foo.bar\\/baz.qux.{moo,arf; {key: value}", "foo.bar/baz.qux.{moo,arf",
               None, {"key": "value"})

  def testSlashEsc(self):
    self.valid("foo.bar\\x00baz.qux.{moo,arf; {key: value}", "foo.bar\x00baz.qux.{moo,arf",
               None, {"key": "value"})

  def testNoName(self):
    self.invalid("; {key: value}", "unexpected token SEMI(';') line:1,0:; {key: value}")

  def testEmpty(self):
    self.invalid("", "unexpected token EOF line:1,0:")

  def testNoNameSlash(self):
    self.invalid("/asdf; {key: value}",
                 "unexpected token SLASH('/') line:1,0:/asdf; {key: value}")

  def testBadOptions1(self):
    self.invalid("name/subject; {",
                 "expecting (ID, RBRACE), got EOF line:1,15:name/subject; {")

  def testBadOptions2(self):
    self.invalid("name/subject; { 3",
                 "expecting (ID, RBRACE), got NUMBER('3') "
                 "line:1,16:name/subject; { 3")

  def testBadOptions3(self):
    self.invalid("name/subject; { key:",
                 "expecting (NUMBER, STRING, ID, LBRACE), got EOF "
                 "line:1,20:name/subject; { key:")

  def testBadOptions4(self):
    self.invalid("name/subject; { key: value",
                 "expecting (COMMA, RBRACE), got EOF "
                 "line:1,26:name/subject; { key: value")

  def testBadOptions5(self):
    self.invalid("name/subject; { key: value asdf",
                 "expecting (COMMA, RBRACE), got ID('asdf') "
                 "line:1,27:name/subject; { key: value asdf")

  def testBadOptions6(self):
    self.invalid("name/subject; { key: value,",
                 "expecting (ID, RBRACE), got EOF "
                 "line:1,27:name/subject; { key: value,")

  def testBadOptions7(self):
    self.invalid("name/subject; { key: value } asdf",
                 "expecting EOF, got ID('asdf') "
                 "line:1,29:name/subject; { key: value } asdf")
