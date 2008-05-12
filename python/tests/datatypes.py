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

from unittest import TestCase
from qpid.testlib import testrunner
from qpid.spec010 import load
from qpid.datatypes import *

class RangedSetTest(TestCase):

  def check(self, ranges):
    posts = []
    for range in ranges:
      posts.append(range.lower)
      posts.append(range.upper)

    sorted = posts[:]
    sorted.sort()

    assert posts == sorted

    idx = 1
    while idx + 1 < len(posts):
      assert posts[idx] + 1 != posts[idx+1]
      idx += 2

  def test(self):
    rs = RangedSet()

    self.check(rs.ranges)

    rs.add(1)

    assert 1 in rs
    assert 2 not in rs
    assert 0 not in rs
    self.check(rs.ranges)

    rs.add(2)

    assert 0 not in rs
    assert 1 in rs
    assert 2 in rs
    assert 3 not in rs
    self.check(rs.ranges)

    rs.add(0)

    assert -1 not in rs
    assert 0 in rs
    assert 1 in rs
    assert 2 in rs
    assert 3 not in rs
    self.check(rs.ranges)

    rs.add(37)

    assert -1 not in rs
    assert 0 in rs
    assert 1 in rs
    assert 2 in rs
    assert 3 not in rs
    assert 36 not in rs
    assert 37 in rs
    assert 38 not in rs
    self.check(rs.ranges)

    rs.add(-1)
    self.check(rs.ranges)

    rs.add(-3)
    self.check(rs.ranges)

    rs.add(1, 20)
    assert 21 not in rs
    assert 20 in rs
    self.check(rs.ranges)

  def testAddSelf(self):
    a = RangedSet()
    a.add(0, 8)
    self.check(a.ranges)
    a.add(0, 8)
    self.check(a.ranges)
    assert len(a.ranges) == 1
    range = a.ranges[0]
    assert range.lower == 0
    assert range.upper == 8

class RangeTest(TestCase):

  def testIntersect1(self):
    a = Range(0, 10)
    b = Range(9, 20)
    i1 = a.intersect(b)
    i2 = b.intersect(a)
    assert i1.upper == 10
    assert i2.upper == 10
    assert i1.lower == 9
    assert i2.lower == 9

  def testIntersect2(self):
    a = Range(0, 10)
    b = Range(11, 20)
    assert a.intersect(b) == None
    assert b.intersect(a) == None

  def testIntersect3(self):
    a = Range(0, 10)
    b = Range(3, 5)
    i1 = a.intersect(b)
    i2 = b.intersect(a)
    assert i1.upper == 5
    assert i2.upper == 5
    assert i1.lower == 3
    assert i2.lower == 3

class UUIDTest(TestCase):

  def test(self):
    # this test is kind of lame, but it does excercise the basic
    # functionality of the class
    u = uuid4()
    for i in xrange(1024):
      assert u != uuid4()

class MessageTest(TestCase):

  def setUp(self):
    self.spec = load(testrunner.get_spec_file("amqp.0-10-qpid-errata.xml"))
    self.mp = Struct(self.spec["message.message_properties"])
    self.dp = Struct(self.spec["message.delivery_properties"])
    self.fp = Struct(self.spec["message.fragment_properties"])

  def testHas(self):
    m = Message(self.mp, self.dp, self.fp, "body")
    assert m.has("message_properties")
    assert m.has("delivery_properties")
    assert m.has("fragment_properties")

  def testGet(self):
    m = Message(self.mp, self.dp, self.fp, "body")
    assert m.get("message_properties") == self.mp
    assert m.get("delivery_properties") == self.dp
    assert m.get("fragment_properties") == self.fp

  def testSet(self):
    m = Message(self.mp, self.dp, "body")
    assert m.get("fragment_properties") is None
    m.set(self.fp)
    assert m.get("fragment_properties") == self.fp

  def testSetOnEmpty(self):
    m = Message("body")
    assert m.get("delivery_properties") is None
    m.set(self.dp)
    assert m.get("delivery_properties") == self.dp

  def testSetReplace(self):
    m = Message(self.mp, self.dp, self.fp, "body")
    dp = Struct(self.spec["message.delivery_properties"])
    assert m.get("delivery_properties") == self.dp
    assert m.get("delivery_properties") != dp
    m.set(dp)
    assert m.get("delivery_properties") != self.dp
    assert m.get("delivery_properties") == dp

  def testClear(self):
    m = Message(self.mp, self.dp, self.fp, "body")
    assert m.get("message_properties") == self.mp
    assert m.get("delivery_properties") == self.dp
    assert m.get("fragment_properties") == self.fp
    m.clear("fragment_properties")
    assert m.get("fragment_properties") is None
    assert m.get("message_properties") == self.mp
    assert m.get("delivery_properties") == self.dp
