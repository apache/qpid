#!/usr/bin/env python

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

"""
Utility code to translate between python objects and AMQP encoded data
fields.
"""

from cStringIO import StringIO
from struct import *
from reference import ReferenceId

class EOF(Exception):
  pass

class Codec:

  def __init__(self, stream):
    self.stream = stream
    self.nwrote = 0
    self.nread = 0
    self.incoming_bits = []
    self.outgoing_bits = []

  def read(self, n):
    data = self.stream.read(n)
    if n > 0 and len(data) == 0:
      raise EOF()
    self.nread += len(data)
    return data

  def write(self, s):
    self.flushbits()
    self.stream.write(s)
    self.nwrote += len(s)

  def flush(self):
    self.flushbits()
    self.stream.flush()

  def flushbits(self):
    if len(self.outgoing_bits) > 0:
      bytes = []
      index = 0
      for b in self.outgoing_bits:
        if index == 0: bytes.append(0)
        if b: bytes[-1] |= 1 << index
        index = (index + 1) % 8
      del self.outgoing_bits[:]
      for byte in bytes:
        self.encode_octet(byte)

  def pack(self, fmt, *args):
    self.write(pack(fmt, *args))

  def unpack(self, fmt):
    size = calcsize(fmt)
    data = self.read(size)
    values = unpack(fmt, data)
    if len(values) == 1:
      return values[0]
    else:
      return values

  def encode(self, type, value):
    getattr(self, "encode_" + type)(value)

  def decode(self, type):
    return getattr(self, "decode_" + type)()

  # bit
  def encode_bit(self, o):
    if o:
      self.outgoing_bits.append(True)
    else:
      self.outgoing_bits.append(False)

  def decode_bit(self):
    if len(self.incoming_bits) == 0:
      bits = self.decode_octet()
      for i in range(8):
        self.incoming_bits.append(bits >> i & 1 != 0)
    return self.incoming_bits.pop(0)

  # octet
  def encode_octet(self, o):
    self.pack("!B", o)

  def decode_octet(self):
    return self.unpack("!B")

  # short
  def encode_short(self, o):
    self.pack("!H", o)

  def decode_short(self):
    return self.unpack("!H")

  # long
  def encode_long(self, o):
    self.pack("!L", o)

  def decode_long(self):
    return self.unpack("!L")

  # longlong
  def encode_longlong(self, o):
    self.pack("!Q", o)

  def decode_longlong(self):
    return self.unpack("!Q")

  def enc_str(self, fmt, s):
    size = len(s)
    self.pack(fmt, size)
    self.write(s)

  def dec_str(self, fmt):
    size = self.unpack(fmt)
    return self.read(size)

  # shortstr
  def encode_shortstr(self, s):
    self.enc_str("!B", s)

  def decode_shortstr(self):
    return self.dec_str("!B")

  # longstr
  def encode_longstr(self, s):
    if isinstance(s, dict):
      self.encode_table(s)
    else:
      self.enc_str("!L", s)

  def decode_longstr(self):
    return self.dec_str("!L")

  # table
  def encode_table(self, tbl):
    enc = StringIO()
    codec = Codec(enc)
    if tbl:
      for key, value in tbl.items():
        codec.encode_shortstr(key)
        if isinstance(value, basestring):
          codec.write("S")
          codec.encode_longstr(value)
        else:
          codec.write("I")
          codec.encode_long(value)
    s = enc.getvalue()
    self.encode_long(len(s))
    self.write(s)

  def decode_table(self):
    size = self.decode_long()
    start = self.nread
    result = {}
    while self.nread - start < size:
      key = self.decode_shortstr()
      type = self.read(1)
      if type == "S":
        value = self.decode_longstr()
      elif type == "I":
        value = self.decode_long()
      else:
        raise ValueError(repr(type))
      result[key] = value
    return result

  def encode_timestamp(self, t):
    # XXX
    self.encode_longlong(t)

  def decode_timestamp(self):
    # XXX
    return self.decode_longlong()

  def encode_content(self, s):
    # content can be passed as a string in which case it is assumed to
    # be inline data, or as an instance of ReferenceId indicating it is
    # a reference id    
    if isinstance(s, ReferenceId):
      self.encode_octet(1)
      self.encode_longstr(s.id)
    else:      
      self.encode_octet(0)
      self.encode_longstr(s)

  def decode_content(self):    
    # return a string for inline data and a ReferenceId instance for
    # references
    type = self.decode_octet()
    if type == 0:
      return self.decode_longstr()
    else:
      return ReferenceId(self.decode_longstr())
