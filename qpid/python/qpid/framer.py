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
