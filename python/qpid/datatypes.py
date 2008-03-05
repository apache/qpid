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

import threading

class Struct:

  def __init__(self, fields):
    self.__dict__ = fields

  def __repr__(self):
    return "Struct(%s)" % ", ".join(["%s=%r" % (k, v)
                                     for k, v in self.__dict__.items()])

  def fields(self):
    return self.__dict__

class Message:

  def __init__(self, *args):
    if args:
      self.body = args[-1]
    else:
      self.body = None
    if len(args) > 1:
      self.headers = args[:-1]
    else:
      self.headers = None

class Range:

  def __init__(self, lower, upper):
    self.lower = lower
    self.upper = upper

  def __contains__(self, n):
    return self.lower <= n and n <= self.upper

  def touches(self, r):
    return (self.lower - 1 in r or
            self.upper + 1 in r or
            r.lower - 1 in self or
            r.upper + 1 in self)

  def span(self, r):
    return Range(min(self.lower, r.lower), max(self.upper, r.upper))

  def __str__(self):
    return "Range(%s, %s)" % (self.lower, self.upper)

  def __repr__(self):
    return str(self)

class RangeSet:

  def __init__(self):
    self.ranges = []

  def __contains__(self, n):
    for r in self.ranges:
      if n in r:
        return True
    return False

  def add_range(self, range):
    idx = 0
    while idx < len(self.ranges):
      r = self.ranges[idx]
      if range.touches(r):
        del self.ranges[idx]
        range = range.span(r)
      elif range.upper < r.lower:
        self.ranges.insert(idx, range)
        return
      else:
        idx += 1
    self.ranges.append(range)

  def add(self, n):
    self.add_range(Range(n, n))

  def __str__(self):
    return "RangeSet(%s)" % str(self.ranges)

  def __repr__(self):
    return str(self)

class Future:
  def __init__(self, initial=None):
    self.value = initial
    self._set = threading.Event()

  def set(self, value):
    self.value = value
    self._set.set()

  def get(self, timeout=None):
    self._set.wait(timeout)
    return self.value

  def is_set(self):
    return self._set.isSet()
