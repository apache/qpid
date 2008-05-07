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

import threading, struct

class Struct:

  def __init__(self, _type, *args, **kwargs):
    if len(args) > len(_type.fields):
      raise TypeError("%s() takes at most %s arguments (%s given)" %
                      (_type.name, len(_type.fields), len(args)))

    self._type = _type

    idx = 0
    for field in _type.fields:
      if idx < len(args):
        arg = args[idx]
        if kwargs.has_key(field.name):
          raise TypeError("%s() got multiple values for keyword argument '%s'" %
                          (_type.name, field.name))
      elif kwargs.has_key(field.name):
        arg = kwargs.pop(field.name)
      else:
        arg = field.default()
      setattr(self, field.name, arg)
      idx += 1

    if kwargs:
      unexpected = kwargs.keys()[0]
      raise TypeError("%s() got an unexpected keyword argument '%s'" %
                      (_type.name, unexpected))

  def __getitem__(self, name):
    return getattr(self, name)

  def __setitem__(self, name, value):
    if not hasattr(self, name):
      raise AttributeError("'%s' object has no attribute '%s'" %
                           (self._type.name, name))
    setattr(self, name, value)

  def __repr__(self):
    fields = []
    for f in self._type.fields:
      v = self[f.name]
      if f.type.is_present(v):
        fields.append("%s=%r" % (f.name, v))
    return "%s(%s)" % (self._type.name, ", ".join(fields))

class Message:

  def __init__(self, *args):
    if args:
      self.body = args[-1]
    else:
      self.body = None
    if len(args) > 1:
      self.headers = list(args[:-1])
    else:
      self.headers = None
    self.id = None

  def has(self, name):
    return self.get(name) != None

  def get(self, name):
    if self.headers:
      for h in self.headers:
        if h._type.name == name:
          return h
    return None

  def set(self, header):
    if self.headers is None:
      self.headers = []
    idx = 0
    while idx < len(self.headers):
      if self.headers[idx]._type == header._type:
        self.headers[idx] = header
        return
      idx += 1
    self.headers.append(header)

  def clear(self, name):
    idx = 0
    while idx < len(self.headers):
      if self.headers[idx]._type.name == name:
        del self.headers[idx]
        return
      idx += 1

  def __repr__(self):
    args = []
    if self.headers:
      args.extend(map(repr, self.headers))
    if self.body:
      args.append(repr(self.body))
    if self.id is not None:
      args.append("id=%s" % self.id)
    return "Message(%s)" % ", ".join(args)

class Range:

  def __init__(self, lower, upper = None):
    self.lower = lower
    if upper is None:
      self.upper = lower
    else:
      self.upper = upper

  def __contains__(self, n):
    return self.lower <= n and n <= self.upper

  def touches(self, r):
    # XXX
    return (self.lower - 1 in r or
            self.upper + 1 in r or
            r.lower - 1 in self or
            r.upper + 1 in self or
            self.lower in r or
            self.upper in r or
            r.lower in self or
            r.upper in self)

  def span(self, r):
    return Range(min(self.lower, r.lower), max(self.upper, r.upper))

  def __repr__(self):
    return "%s-%s" % (self.lower, self.upper)

class RangedSet:

  def __init__(self, *args):
    self.ranges = []
    for n in args:
      self.add(n)

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

  def add(self, lower, upper = None):
    self.add_range(Range(lower, upper))

  def __repr__(self):
    return str(self.ranges)

class Future:
  def __init__(self, initial=None, exception=Exception):
    self.value = initial
    self._error = None
    self._set = threading.Event()
    self.exception = exception

  def error(self, error):
    self._error = error
    self._set.set()

  def set(self, value):
    self.value = value
    self._set.set()

  def get(self, timeout=None):
    self._set.wait(timeout)
    if self._error != None:
      raise self.exception(self._error)
    return self.value

  def is_set(self):
    return self._set.isSet()

try:
  import uuid
  def random_uuid():
    return uuid.uuid4().get_bytes()
except ImportError:
  import random
  def random_uuid():
    bytes = [random.randint(0, 255) for i in xrange(16)]

    # From RFC4122, the version bits are set to 0100
    bytes[7] &= 0x0F
    bytes[7] |= 0x40

    # From RFC4122, the top two bits of byte 8 get set to 01
    bytes[8] &= 0x3F
    bytes[8] |= 0x80
    return "".join(map(chr, bytes))

def uuid4():
  return UUID(random_uuid())

class UUID:

  def __init__(self, bytes):
    self.bytes = bytes

  def __cmp__(self, other):
    if isinstance(other, UUID):
      return cmp(self.bytes, other.bytes)
    raise NotImplemented()

  def __str__(self):
    return "%08x-%04x-%04x-%04x-%04x%08x" % struct.unpack("!LHHHHL", self.bytes)

  def __repr__(self):
    return "UUID(%r)" % str(self)
