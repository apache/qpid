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

import struct, socket
from exceptions import Closed
from packer import Packer
from threading import RLock
from logging import getLogger

raw = getLogger("qpid.io.raw")
frm = getLogger("qpid.io.frm")

FIRST_SEG = 0x08
LAST_SEG = 0x04
FIRST_FRM = 0x02
LAST_FRM = 0x01

class Frame:

  HEADER = "!2BHxBH4x"
  MAX_PAYLOAD = 65535 - struct.calcsize(HEADER)

  def __init__(self, flags, type, track, channel, payload):
    if len(payload) > Frame.MAX_PAYLOAD:
      raise ValueError("max payload size exceeded: %s" % len(payload))
    self.flags = flags
    self.type = type
    self.track = track
    self.channel = channel
    self.payload = payload

  def isFirstSegment(self):
    return bool(FIRST_SEG & self.flags)

  def isLastSegment(self):
    return bool(LAST_SEG & self.flags)

  def isFirstFrame(self):
    return bool(FIRST_FRM & self.flags)

  def isLastFrame(self):
    return bool(LAST_FRM & self.flags)

  def __str__(self):
    return "%s%s%s%s %s %s %s %r" % (int(self.isFirstSegment()),
                                     int(self.isLastSegment()),
                                     int(self.isFirstFrame()),
                                     int(self.isLastFrame()),
                                     self.type,
                                     self.track,
                                     self.channel,
                                     self.payload)

class FramingError(Exception): pass

class Framer(Packer):

  HEADER="!4s4B"

  def __init__(self, sock):
    self.sock = sock
    self.sock_lock = RLock()
    self._buf = ""

  def aborted(self):
    return False

  def write(self, buf):
    self._buf += buf

  def flush(self):
    self.sock_lock.acquire()
    try:
      self._write(self._buf)
      self._buf = ""
      frm.debug("FLUSHED")
    finally:
      self.sock_lock.release()

  def _write(self, buf):
    while buf:
      try:
        n = self.sock.send(buf)
      except socket.timeout:
        if self.aborted():
          raise Closed()
        else:
          continue
      raw.debug("SENT %r", buf[:n])
      buf = buf[n:]

  def read(self, n):
    data = ""
    while len(data) < n:
      try:
        s = self.sock.recv(n - len(data))
      except socket.timeout:
        if self.aborted():
          raise Closed()
        else:
          continue
      except socket.error, e:
        if data != "":
          raise e
        else:
          raise Closed()
      if len(s) == 0:
        raise Closed()
      data += s
      raw.debug("RECV %r", s)
    return data

  def read_header(self):
    return self.unpack(Framer.HEADER)

  def write_header(self, major, minor):
    self.sock_lock.acquire()
    try:
      self.pack(Framer.HEADER, "AMQP", 1, 1, major, minor)
      self.flush()
    finally:
      self.sock_lock.release()

  def write_frame(self, frame):
    self.sock_lock.acquire()
    try:
      size = len(frame.payload) + struct.calcsize(Frame.HEADER)
      track = frame.track & 0x0F
      self.pack(Frame.HEADER, frame.flags, frame.type, size, track, frame.channel)
      self.write(frame.payload)
      if frame.isLastSegment() and frame.isLastFrame():
        self.flush()
      frm.debug("SENT %s", frame)
    finally:
      self.sock_lock.release()

  def read_frame(self):
    flags, type, size, track, channel = self.unpack(Frame.HEADER)
    if flags & 0xF0: raise FramingError()
    payload = self.read(size - struct.calcsize(Frame.HEADER))
    frame = Frame(flags, type, track, channel, payload)
    frm.debug("RECV %s", frame)
    return frame
