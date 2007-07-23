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

The unit test for this module is located in tests/codec.py
"""

import re
from cStringIO import StringIO
from struct import *
from reference import ReferenceId

class EOF(Exception):
  pass

class Codec:

  """
  class that handles encoding/decoding of AMQP primitives
  """

  def __init__(self, stream):
    """
    initializing the stream/fields used
    """
    self.stream = stream
    self.nwrote = 0
    self.nread = 0
    self.incoming_bits = []
    self.outgoing_bits = []

  def read(self, n):
    """
    reads in 'n' bytes from the stream. Can raise EFO exception
    """
    data = self.stream.read(n)
    if n > 0 and len(data) == 0:
      raise EOF()
    self.nread += len(data)
    return data

  def write(self, s):
    """
    writes data 's' to the stream
    """
    self.flushbits()
    self.stream.write(s)
    self.nwrote += len(s)

  def flush(self):
    """
    flushes the bits and data present in the stream
    """
    self.flushbits()
    self.stream.flush()

  def flushbits(self):
    """
    flushes the bits(compressed into octets) onto the stream
    """
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
    """
    packs the data 'args' as per the format 'fmt' and writes it to the stream
    """
    self.write(pack(fmt, *args))

  def unpack(self, fmt):
    """
    reads data from the stream and unpacks it as per the format 'fmt'
    """
    size = calcsize(fmt)
    data = self.read(size)
    values = unpack(fmt, data)
    if len(values) == 1:
      return values[0]
    else:
      return values

  def encode(self, type, value):
    """
    calls the appropriate encode function e.g. encode_octet, encode_short etc.
    """
    getattr(self, "encode_" + type)(value)

  def decode(self, type):
    """
    calls the appropriate decode function e.g. decode_octet, decode_short etc.
    """
    return getattr(self, "decode_" + type)()

  def encode_bit(self, o):
    """
    encodes a bit
    """
    if o:
      self.outgoing_bits.append(True)
    else:
      self.outgoing_bits.append(False)

  def decode_bit(self):
    """
    decodes a bit
    """
    if len(self.incoming_bits) == 0:
      bits = self.decode_octet()
      for i in range(8):
        self.incoming_bits.append(bits >> i & 1 != 0)
    return self.incoming_bits.pop(0)

  def encode_octet(self, o):
    """
    encodes octet (8 bits) data 'o' in network byte order
    """

    # octet's valid range is [0,255]
    if (o < 0 or o > 255):
        raise ValueError('Valid range of octet is [0,255]')

    self.pack("!B", o)

  def decode_octet(self):
    """
    decodes a octet (8 bits) encoded in network byte order
    """
    return self.unpack("!B")

  def encode_short(self, o):
    """
    encodes short (16 bits) data 'o' in network byte order
    """

    # short int's valid range is [0,65535]
    if (o < 0 or o > 65535):
        raise ValueError('Valid range of short int is [0,65535]')

    self.pack("!H", o)

  def decode_short(self):
    """
    decodes a short (16 bits) in network byte order
    """
    return self.unpack("!H")

  def encode_long(self, o):
    """
    encodes long (32 bits) data 'o' in network byte order
    """

    if (o < 0):
        raise ValueError('unsinged long int cannot be less than 0')

    self.pack("!L", o)

  def decode_long(self):
    """
    decodes a long (32 bits) in network byte order
    """
    return self.unpack("!L")

  def encode_longlong(self, o):
    """
    encodes long long (64 bits) data 'o' in network byte order
    """
    self.pack("!Q", o)

  def decode_longlong(self):
    """
    decodes a long long (64 bits) in network byte order
    """
    return self.unpack("!Q")

  def enc_str(self, fmt, s):
    """
    encodes a string 's' in network byte order as per format 'fmt'
    """
    size = len(s)
    self.pack(fmt, size)
    self.write(s)

  def dec_str(self, fmt):
    """
    decodes a string in network byte order as per format 'fmt'
    """
    size = self.unpack(fmt)
    return self.read(size)

  def encode_shortstr(self, s):
    """
    encodes a short string 's' in network byte order
    """

    # short strings are limited to 255 octets
    if len(s) > 255:
        raise ValueError('Short strings are limited to 255 octets')

    self.enc_str("!B", s)

  def decode_shortstr(self):
    """
    decodes a short string in network byte order
    """
    return self.dec_str("!B")

  def encode_longstr(self, s):
    """
    encodes a long string 's' in network byte order
    """
    if isinstance(s, dict):
      self.encode_table(s)
    else:
      self.enc_str("!L", s)

  def decode_longstr(self):
    """
    decodes a long string 's' in network byte order
    """
    return self.dec_str("!L")

  KEY_CHECK = re.compile(r"[\$#A-Za-z][\$#A-Za-z0-9_]*")

  def encode_table(self, tbl):
    """
    encodes a table data structure in network byte order
    """
    enc = StringIO()
    codec = Codec(enc)
    if tbl:
      for key, value in tbl.items():
        # Field names MUST start with a letter, '$' or '#' and may
        # continue with letters, '$' or '#', digits, or underlines, to
        # a maximum length of 128 characters.

        if len(key) > 128:
          raise ValueError("field table key too long: '%s'" % key)

        m = Codec.KEY_CHECK.match(key)
        if m == None or m.end() != len(key):
          raise ValueError("invalid field table key: '%s'" % key)

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
    """
    decodes a table data structure in network byte order
    """
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
    """
    encodes a timestamp data structure in network byte order
    """
    self.encode_longlong(t)

  def decode_timestamp(self):
    """
    decodes a timestamp data structure in network byte order
    """
    return self.decode_longlong()

  def encode_content(self, s):
    """
    encodes a content data structure in network byte order

    content can be passed as a string in which case it is assumed to
    be inline data, or as an instance of ReferenceId indicating it is
    a reference id
    """
    if isinstance(s, ReferenceId):
      self.encode_octet(1)
      self.encode_longstr(s.id)
    else:
      self.encode_octet(0)
      self.encode_longstr(s)

  def decode_content(self):
    """
    decodes a content data structure in network byte order

    return a string for inline data and a ReferenceId instance for
    references
    """
    type = self.decode_octet()
    if type == 0:
      return self.decode_longstr()
    else:
      return ReferenceId(self.decode_longstr())
