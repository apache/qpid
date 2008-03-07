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

from packer import Packer
from datatypes import RangedSet

class CodecException(Exception): pass

class Codec(Packer):

  def __init__(self, spec):
    self.spec = spec

  def write_void(self, v):
    assert v == None
  def read_void(self):
    return None

  def write_bit(self, b):
    if not b: raise ValueError(b)
  def read_bit(self):
    return True

  def read_uint8(self):
    return self.unpack("!B")
  def write_uint8(self, n):
    return self.pack("!B", n)

  def read_int8(self):
    return self.unpack("!b")
  def write_int8(self, n):
    self.pack("!b", n)

  def read_char(self):
    return self.unpack("!c")
  def write_char(self, c):
    self.pack("!c", c)

  def read_boolean(self):
    return self.read_uint8() != 0
  def write_boolean(self, b):
    if b: n = 1
    else: n = 0
    self.write_uint8(n)


  def read_uint16(self):
    return self.unpack("!H")
  def write_uint16(self, n):
    self.pack("!H", n)

  def read_int16(self):
    return self.unpack("!h")
  def write_int16(self, n):
    return self.unpack("!h", n)


  def read_uint32(self):
    return self.unpack("!L")
  def write_uint32(self, n):
    self.pack("!L", n)

  def read_int32(self):
    return self.unpack("!l")
  def write_int32(self, n):
    self.pack("!l", n)

  def read_float(self):
    return self.unpack("!f")
  def write_float(self, f):
    self.pack("!f", f)

  def read_sequence_no(self):
    return self.read_uint32()
  def write_sequence_no(self, n):
    self.write_uint32(n)


  def read_uint64(self):
    return self.unpack("!Q")
  def write_uint64(self, n):
    self.pack("!Q", n)

  def read_int64(self):
    return self.unpack("!q")
  def write_int64(self, n):
    self.pack("!q", n)

  def read_double(self):
    return self.unpack("!d")
  def write_double(self, d):
    self.pack("!d", d)


  def read_vbin8(self):
    return self.read(self.read_uint8())
  def write_vbin8(self, b):
    self.write_uint8(len(b))
    self.write(b)

  def read_str8(self):
    return self.read_vbin8().decode("utf8")
  def write_str8(self, s):
    self.write_vbin8(s.encode("utf8"))

  def read_str16(self):
    return self.read_vbin16().decode("utf8")
  def write_str16(self, s):
    self.write_vbin16(s.encode("utf8"))


  def read_vbin16(self):
    return self.read(self.read_uint16())
  def write_vbin16(self, b):
    self.write_uint16(len(b))
    self.write(b)

  def read_sequence_set(self):
    result = RangedSet()
    size = self.read_uint16()
    nranges = size/8
    while nranges > 0:
      lower = self.read_sequence_no()
      upper = self.read_sequence_no()
      result.add(lower, upper)
      nranges -= 1
    return result
  def write_sequence_set(self, ss):
    size = 8*len(ss.ranges)
    self.write_uint16(size)
    for range in ss.ranges:
      self.write_sequence_no(range.lower)
      self.write_sequence_no(range.upper)

  def read_vbin32(self):
    return self.read(self.read_uint32())
  def write_vbin32(self, b):
    self.write_uint32(len(b))
    self.write(b)

  def write_map(self, m):
    sc = StringCodec(self.spec)
    for k, v in m.items():
      type = self.spec.encoding(v.__class__)
      if type == None:
        raise CodecException("no encoding for %s" % v.__class__)
      sc.write_str8(k)
      sc.write_uint8(type.code)
      type.encode(sc, v)
    # XXX: need to put in count when CPP supports it
    self.write_vbin32(sc.encoded)
  def read_map(self):
    sc = StringCodec(self.spec, self.read_vbin32())
    result = {}
    while sc.encoded:
      k = sc.read_str8()
      code = sc.read_uint8()
      type = self.spec.types[code]
      v = type.decode(sc)
      result[k] = v
    return result

  def write_array(self, a):
    pass
  def read_array(self):
    pass

  def read_struct32(self):
    size = self.read_uint32()
    code = self.read_uint16()
    struct = self.spec.structs[code]
    return struct.decode_fields(self)
  def write_struct32(self, value):
    sc = StringCodec(self.spec)
    sc.write_uint16(value.type.code)
    value.type.encode_fields(sc, value)
    self.write_vbin32(sc.encoded)

  def read_control(self):
    cntrl = self.spec.controls[self.read_uint16()]
    return cntrl.decode(self)
  def write_control(self, type, ctrl):
    self.write_uint16(type.code)
    type.encode(self, ctrl)

  def read_command(self):
    cmd = self.spec.commands[self.read_uint16()]
    return cmd.decode(self)
  def write_command(self, type, cmd):
    self.write_uint16(type.code)
    type.encode(self, cmd)

  def read_size(self, width):
    if width > 0:
      attr = "read_uint%d" % (width*8)
      return getattr(self, attr)()

  def write_size(self, width, n):
    if width > 0:
      attr = "write_uint%d" % (width*8)
      getattr(self, attr)(n)



class StringCodec(Codec):

  def __init__(self, spec, encoded = ""):
    Codec.__init__(self, spec)
    self.encoded = encoded

  def write(self, s):
    self.encoded += s

  def read(self, n):
    result = self.encoded[:n]
    self.encoded = self.encoded[n:]
    return result
