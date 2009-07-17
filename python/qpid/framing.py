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

import struct
from qpid.framer import Frame, FIRST_SEG, LAST_SEG, FIRST_FRM, LAST_FRM
from qpid.assembler import Segment

class FrameDecoder:

  def __init__(self):
    self.input = ""
    self.output = []
    self.parse = self.__frame_header

  def write(self, bytes):
    self.input += bytes
    while True:
      next = self.parse()
      if next is None:
        break
      else:
        self.parse = next

  def __consume(self, n):
    result = self.input[:n]
    self.input = self.input[n:]
    return result

  def __frame_header(self):
    if len(self.input) >= Frame.HEADER_SIZE:
      st = self.__consume(Frame.HEADER_SIZE)
      self.flags, self.type, self.size, self.track, self.channel = \
          struct.unpack(Frame.HEADER, st)
      return self.__frame_body

  def __frame_body(self):
    size = self.size - Frame.HEADER_SIZE
    if len(self.input) >= size:
      payload = self.__consume(size)
      frame = Frame(self.flags, self.type, self.track, self.channel, payload)
      self.output.append(frame)
      return self.__frame_header

  def read(self):
    result = self.output
    self.output = []
    return result

class FrameEncoder:

  def __init__(self):
    self.output = ""

  def write(self, *frames):
    for frame in frames:
      size = len(frame.payload) + Frame.HEADER_SIZE
      track = frame.track & 0x0F
      self.output += struct.pack(Frame.HEADER, frame.flags, frame.type, size,
                                 track, frame.channel)
      self.output += frame.payload

  def read(self):
    result = self.output
    self.output = ""
    return result

class SegmentDecoder:

  def __init__(self):
    self.fragments = {}
    self.segments = []

  def write(self, *frames):
    for frm in frames:
      key = (frm.channel, frm.track)
      seg = self.fragments.get(key)

      if seg == None:
        seg = Segment(frm.isFirstSegment(), frm.isLastSegment(),
                      frm.type, frm.track, frm.channel, "")
        self.fragments[key] = seg

      seg.payload += frm.payload

      if frm.isLastFrame():
        self.fragments.pop(key)
        self.segments.append(seg)

  def read(self):
    result = self.segments
    self.segments = []
    return result

class SegmentEncoder:

  def __init__(self, max_payload=Frame.MAX_PAYLOAD):
    self.max_payload = max_payload
    self.frames = []

  def write(self, *segments):
    for seg in segments:
      remaining = seg.payload

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
        if seg.first:
          flags |= FIRST_SEG
        if seg.last:
          flags |= LAST_SEG

        frm = Frame(flags, seg.type, seg.track, seg.channel, payload)
        self.frames.append(frm)

  def read(self):
    result = self.frames
    self.frames = []
    return result
