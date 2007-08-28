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
A Connection class containing socket code that uses the spec metadata
to read and write Frame objects. This could be used by a client,
server, or even a proxy implementation.
"""

import socket, codec, logging, qpid
from cStringIO import StringIO
from spec import load
from codec import EOF

class SockIO:

  def __init__(self, sock):
    self.sock = sock

  def write(self, buf):
#    print "OUT: %r" % buf
    self.sock.sendall(buf)

  def read(self, n):
    data = ""
    while len(data) < n:
      try:
        s = self.sock.recv(n - len(data))
      except socket.error:
        break
      if len(s) == 0:
        break
#      print "IN: %r" % s
      data += s
    return data

  def flush(self):
    pass

  def close(self):
    self.sock.shutdown(socket.SHUT_RDWR)

def connect(host, port):
  sock = socket.socket()
  sock.connect((host, port))
  sock.setblocking(1)
  return SockIO(sock)

def listen(host, port, predicate = lambda: True):
  sock = socket.socket()
  sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
  sock.bind((host, port))
  sock.listen(5)
  while predicate():
    s, a = sock.accept()
    yield SockIO(s)

class Connection:

  def __init__(self, io, spec):
    self.codec = codec.Codec(io, spec)
    self.spec = spec
    self.FRAME_END = self.spec.constants.byname["frame_end"].id

  def flush(self):
    self.codec.flush()

  INIT="!4s4B"

  def init(self):
    self.codec.pack(Connection.INIT, "AMQP", 1, 1, self.spec.major,
                    self.spec.minor)

  def tini(self):
    self.codec.unpack(Connection.INIT)

  def write(self, frame):
    c = self.codec
    c.encode_octet(self.spec.constants.byname[frame.type].id)
    c.encode_short(frame.channel)
    body = StringIO()
    enc = codec.Codec(body, self.spec)
    frame.encode(enc)
    enc.flush()
    c.encode_longstr(body.getvalue())
    c.encode_octet(self.FRAME_END)

  def read(self):
    c = self.codec
    type = self.spec.constants.byid[c.decode_octet()].name
    channel = c.decode_short()
    body = c.decode_longstr()
    dec = codec.Codec(StringIO(body), self.spec)
    frame = Frame.DECODERS[type].decode(self.spec, dec, len(body))
    frame.channel = channel
    end = c.decode_octet()
    if end != self.FRAME_END:
      garbage = ""
      while end != self.FRAME_END:
        garbage += chr(end)
        end = c.decode_octet()
      raise "frame error: expected %r, got %r" % (self.FRAME_END, garbage)
    return frame

class Frame:

  DECODERS = {}

  class __metaclass__(type):

    def __new__(cls, name, bases, dict):
      for attr in ("encode", "decode", "type"):
        if not dict.has_key(attr):
          raise TypeError("%s must define %s" % (name, attr))
      dict["decode"] = staticmethod(dict["decode"])
      if dict.has_key("__init__"):
        __init__ = dict["__init__"]
        def init(self, *args, **kwargs):
          args = list(args)
          self.init(args, kwargs)
          __init__(self, *args, **kwargs)
        dict["__init__"] = init
      t = type.__new__(cls, name, bases, dict)
      if t.type != None:
        Frame.DECODERS[t.type] = t
      return t

  type = None

  def init(self, args, kwargs):
    self.channel = kwargs.pop("channel", 0)

  def encode(self, enc): abstract

  def decode(spec, dec, size): abstract

class Method(Frame):

  type = "frame_method"

  def __init__(self, method, args):
    if len(args) != len(method.fields):
      argspec = ["%s: %s" % (f.name, f.type)
                 for f in method.fields]
      raise TypeError("%s.%s expecting (%s), got %s" %
                      (method.klass.name, method.name, ", ".join(argspec),
                       args))
    self.method = method
    self.method_type = method
    self.args = args

  def encode(self, c):
    c.encode_short(self.method.klass.id)
    c.encode_short(self.method.id)
    for field, arg in zip(self.method.fields, self.args):
      c.encode(field.type, arg)

  def decode(spec, c, size):
    klass = spec.classes.byid[c.decode_short()]
    meth = klass.methods.byid[c.decode_short()]
    args = tuple([c.decode(f.type) for f in meth.fields])
    return Method(meth, args)

  def __str__(self):
    return "[%s] %s %s" % (self.channel, self.method,
                           ", ".join([str(a) for a in self.args]))

class Request(Frame):

  type = "frame_request"

  def __init__(self, id, response_mark, method):
    self.id = id
    self.response_mark = response_mark
    self.method = method
    self.method_type = method.method_type
    self.args = method.args

  def encode(self, enc):
    enc.encode_longlong(self.id)
    enc.encode_longlong(self.response_mark)
    # reserved
    enc.encode_long(0)
    self.method.encode(enc)

  def decode(spec, dec, size):
    id = dec.decode_longlong()
    mark = dec.decode_longlong()
    # reserved
    dec.decode_long()
    method = Method.decode(spec, dec, size - 20)
    return Request(id, mark, method)

  def __str__(self):
    return "[%s] Request(%s) %s" % (self.channel, self.id, self.method)

class Response(Frame):

  type = "frame_response"

  def __init__(self, id, request_id, batch_offset, method):
    self.id = id
    self.request_id = request_id
    self.batch_offset = batch_offset
    self.method = method
    self.method_type = method.method_type
    self.args = method.args

  def encode(self, enc):
    enc.encode_longlong(self.id)
    enc.encode_longlong(self.request_id)
    enc.encode_long(self.batch_offset)
    self.method.encode(enc)

  def decode(spec, dec, size):
    id = dec.decode_longlong()
    request_id = dec.decode_longlong()
    batch_offset = dec.decode_long()
    method = Method.decode(spec, dec, size - 20)
    return Response(id, request_id, batch_offset, method)

  def __str__(self):
    return "[%s] Response(%s,%s,%s) %s" % (self.channel, self.id, self.request_id, self.batch_offset, self.method)

def uses_struct_encoding(spec):
  return (spec.major == 0 and spec.minor == 10)

class Header(Frame):

  type = "frame_header"

  def __init__(self, klass, weight, size, properties):
    self.klass = klass
    self.weight = weight
    self.size = size
    self.properties = properties

  def __getitem__(self, name):
    return self.properties[name]

  def __setitem__(self, name, value):
    self.properties[name] = value

  def __delitem__(self, name):
    del self.properties[name]

  def encode(self, c):
    if uses_struct_encoding(c.spec):
      self.encode_structs(c)
    else:
      self.encode_legacy(c)

  def encode_structs(self, c):
    # XXX
    structs = [qpid.Struct(c.spec.domains.byname["delivery_properties"].type),
               qpid.Struct(c.spec.domains.byname["message_properties"].type)]

    # XXX
    props = self.properties.copy()
    for k in self.properties:
      for s in structs:
        if s.has(k):
          s.set(k, props.pop(k))
    if props:
      raise TypeError("no such property: %s" % (", ".join(props)))

    # message properties store the content-length now, and weight is
    # deprecated
    structs[1].content_length = self.size

    for s in structs:
      c.encode_long_struct(s)

  def encode_legacy(self, c):
    c.encode_short(self.klass.id)
    c.encode_short(self.weight)
    c.encode_longlong(self.size)

    # property flags
    nprops = len(self.klass.fields)
    flags = 0
    for i in range(nprops):
      f = self.klass.fields.items[i]
      flags <<= 1
      if self.properties.get(f.name) != None:
        flags |= 1
      # the last bit indicates more flags
      if i > 0 and (i % 15) == 0:
        flags <<= 1
        if nprops > (i + 1):
          flags |= 1
          c.encode_short(flags)
          flags = 0
    flags <<= ((16 - (nprops % 15)) % 16)
    c.encode_short(flags)

    # properties
    for f in self.klass.fields:
      v = self.properties.get(f.name)
      if v != None:
        c.encode(f.type, v)

  def decode(spec, c, size):
    if uses_struct_encoding(spec):
      return Header.decode_structs(spec, c, size)
    else:
      return Header.decode_legacy(spec, c, size)

  @staticmethod
  def decode_structs(spec, c, size):
    structs = []
    start = c.nread
    while c.nread - start < size:
      structs.append(c.decode_long_struct())

    # XXX
    props = {}
    length = None
    for s in structs:
      for f in s.type.fields:
        props[f.name] = s.get(f.name)
        if f.name == "content_length":
          length = s.get(f.name)
    return Header(None, 0, length, props)

  @staticmethod
  def decode_legacy(spec, c, size):
    klass = spec.classes.byid[c.decode_short()]
    weight = c.decode_short()
    size = c.decode_longlong()

    # property flags
    bits = []
    while True:
      flags = c.decode_short()
      for i in range(15, 0, -1):
        if flags >> i & 0x1 != 0:
          bits.append(True)
        else:
          bits.append(False)
      if flags & 0x1 == 0:
        break

    # properties
    properties = {}
    for b, f in zip(bits, klass.fields):
      if b:
        # Note: decode returns a unicode u'' string but only
        # plain '' strings can be used as keywords so we need to
        # stringify the names.
        properties[str(f.name)] = c.decode(f.type)
    return Header(klass, weight, size, properties)

  def __str__(self):
    return "%s %s %s %s" % (self.klass, self.weight, self.size,
                            self.properties)

class Body(Frame):

  type = "frame_body"

  def __init__(self, content):
    self.content = content

  def encode(self, enc):
    enc.write(self.content)

  def decode(spec, dec, size):
    return Body(dec.read(size))

  def __str__(self):
    return "Body(%r)" % self.content

# TODO:
#  OOB_METHOD = "frame_oob_method"
#  OOB_HEADER = "frame_oob_header"
#  OOB_BODY = "frame_oob_body"
#  TRACE = "frame_trace"
#  HEARTBEAT = "frame_heartbeat"
