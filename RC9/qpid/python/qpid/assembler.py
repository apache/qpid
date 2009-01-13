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

from codec010 import StringCodec
from framer import *
from logging import getLogger

log = getLogger("qpid.io.seg")

class Segment:

  def __init__(self, first, last, type, track, channel, payload):
    self.id = None
    self.offset = None
    self.first = first
    self.last = last
    self.type = type
    self.track = track
    self.channel = channel
    self.payload = payload

  def decode(self, spec):
    segs = spec["segment_type"]
    choice = segs.choices[self.type]
    return getattr(self, "decode_%s" % choice.name)(spec)

  def decode_control(self, spec):
    sc = StringCodec(spec, self.payload)
    return sc.read_control()

  def decode_command(self, spec):
    sc = StringCodec(spec, self.payload)
    hdr, cmd = sc.read_command()
    cmd.id = self.id
    return hdr, cmd

  def decode_header(self, spec):
    sc = StringCodec(spec, self.payload)
    values = []
    while len(sc.encoded) > 0:
      values.append(sc.read_struct32())
    return values

  def decode_body(self, spec):
    return self.payload

  def __str__(self):
    return "%s%s %s %s %s %r" % (int(self.first), int(self.last), self.type,
                                 self.track, self.channel, self.payload)

  def __repr__(self):
    return str(self)

class Assembler(Framer):

  def __init__(self, sock, max_payload = Frame.MAX_PAYLOAD):
    Framer.__init__(self, sock)
    self.max_payload = max_payload
    self.fragments = {}

  def read_segment(self):
    while True:
      frame = self.read_frame()

      key = (frame.channel, frame.track)
      seg = self.fragments.get(key)
      if seg == None:
        seg = Segment(frame.isFirstSegment(), frame.isLastSegment(),
                      frame.type, frame.track, frame.channel, "")
        self.fragments[key] = seg

      seg.payload += frame.payload

      if frame.isLastFrame():
        self.fragments.pop(key)
        log.debug("RECV %s", seg)
        return seg

  def write_segment(self, segment):
    remaining = segment.payload

    first = True
    while first or remaining:
      payload = remaining[:self.max_payload]
      remaining = remaining[self.max_payload:]

      flags = 0
      if first:
        flags |= FIRST_FRM
        first = False
      if not remaining:
        flags |= LAST_FRM
      if segment.first:
        flags |= FIRST_SEG
      if segment.last:
        flags |= LAST_SEG

      frame = Frame(flags, segment.type, segment.track, segment.channel,
                    payload)
      self.write_frame(frame)

    log.debug("SENT %s", segment)
