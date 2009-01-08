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

import datatypes, session, socket
from threading import Thread, Condition, RLock
from util import wait, notify
from assembler import Assembler, Segment
from codec010 import StringCodec
from session import Session
from generator import control_invoker
from spec import SPEC
from exceptions import *
from logging import getLogger
import delegates

class ChannelBusy(Exception): pass

class ChannelsBusy(Exception): pass

class SessionBusy(Exception): pass

class ConnectionFailed(Exception): pass

def client(*args, **kwargs):
  return delegates.Client(*args, **kwargs)

def server(*args, **kwargs):
  return delegates.Server(*args, **kwargs)

class SSLWrapper:

  def __init__(self, ssl):
    self.ssl = ssl

  def recv(self, n):
    return self.ssl.read(n)

  def send(self, s):
    return self.ssl.write(s)

def sslwrap(sock):
  if isinstance(sock, socket.SSLType):
    return SSLWrapper(sock)
  else:
    return sock

class Connection(Assembler):

  def __init__(self, sock, spec=SPEC, delegate=client, **args):
    Assembler.__init__(self, sslwrap(sock))
    self.spec = spec

    self.lock = RLock()
    self.attached = {}
    self.sessions = {}

    self.condition = Condition()
    self.opened = False
    self.failed = False
    self.close_code = (None, "connection aborted")

    self.thread = Thread(target=self.run)
    self.thread.setDaemon(True)

    self.channel_max = 65535

    self.delegate = delegate(self, **args)

  def attach(self, name, ch, delegate, force=False):
    self.lock.acquire()
    try:
      ssn = self.attached.get(ch.id)
      if ssn is not None:
        if ssn.name != name:
          raise ChannelBusy(ch, ssn)
      else:
        ssn = self.sessions.get(name)
        if ssn is None:
          ssn = Session(name, delegate=delegate)
          self.sessions[name] = ssn
        elif ssn.channel is not None:
          if force:
            del self.attached[ssn.channel.id]
            ssn.channel = None
          else:
            raise SessionBusy(ssn)
        self.attached[ch.id] = ssn
        ssn.channel = ch
      ch.session = ssn
      return ssn
    finally:
      self.lock.release()

  def detach(self, name, ch):
    self.lock.acquire()
    try:
      self.attached.pop(ch.id, None)
      ssn = self.sessions.pop(name, None)
      if ssn is not None:
        ssn.channel = None
        ssn.closed()
        return ssn
    finally:
      self.lock.release()

  def __channel(self):
    # XXX: ch 0?
    for i in xrange(self.channel_max):
      if not self.attached.has_key(i):
        return i
    else:
      raise ChannelsBusy()

  def session(self, name, timeout=None, delegate=session.client):
    self.lock.acquire()
    try:
      ch = Channel(self, self.__channel())
      ssn = self.attach(name, ch, delegate)
      ssn.channel.session_attach(name)
      if wait(ssn.condition, lambda: ssn.channel is not None, timeout):
        return ssn
      else:
        self.detach(name, ch)
        raise Timeout()
    finally:
      self.lock.release()

  def detach_all(self):
    self.lock.acquire()
    try:
      for ssn in self.attached.values():
        if self.close_code[0] != 200:
          ssn.exceptions.append(self.close_code)
        self.detach(ssn.name, ssn.channel)
    finally:
      self.lock.release()

  def start(self, timeout=None):
    self.delegate.start()
    self.thread.start()
    if not wait(self.condition, lambda: self.opened or self.failed, timeout):
      raise Timeout()
    if self.failed:
      raise ConnectionFailed(*self.close_code)

  def run(self):
    # XXX: we don't really have a good way to exit this loop without
    # getting the other end to kill the socket
    while True:
      try:
        seg = self.read_segment()
      except Closed:
        self.detach_all()
        break
      self.delegate.received(seg)

  def close(self, timeout=None):
    if not self.opened: return
    Channel(self, 0).connection_close(200)
    if not wait(self.condition, lambda: not self.opened, timeout):
      raise Timeout()
    self.thread.join(timeout=timeout)

  def __str__(self):
    return "%s:%s" % self.sock.getsockname()

  def __repr__(self):
    return str(self)

log = getLogger("qpid.io.ctl")

class Channel(control_invoker(SPEC)):

  def __init__(self, connection, id):
    self.connection = connection
    self.id = id
    self.session = None

  def invoke(self, type, args, kwargs):
    ctl = type.new(args, kwargs)
    sc = StringCodec(self.spec)
    sc.write_control(ctl)
    self.connection.write_segment(Segment(True, True, type.segment_type,
                                          type.track, self.id, sc.encoded))
    log.debug("SENT %s", ctl)

  def __str__(self):
    return "%s[%s]" % (self.connection, self.id)

  def __repr__(self):
    return str(self)
