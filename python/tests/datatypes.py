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
