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

import datatypes, session
from threading import Thread, Event, RLock
from framer import Closed
from assembler import Assembler, Segment
from codec010 import StringCodec
from session import Session
from invoker import Invoker
from spec010 import Control, Command
import delegates

class Timeout(Exception): pass

class ChannelBusy(Exception): pass

class ChannelsBusy(Exception): pass

class SessionBusy(Exception): pass

def client(*args):
  return delegates.Client(*args)

def server(*args):
  return delegates.Server(*args)

class Connection(Assembler):

  def __init__(self, sock, spec, delegate=client):
    Assembler.__init__(self, sock)
    self.spec = spec
    self.track = self.spec["track"]
    self.delegate = delegate(self)
    self.attached = {}
    self.sessions = {}
    self.lock = RLock()
    self.thread = Thread(target=self.run)
    self.thread.setDaemon(True)
    self.opened = Event()
    self.closed = Event()
    self.channel_max = 65535

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
          ssn = Session(name, self.spec, delegate=delegate)
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
      ssn = self.attach(name, Channel(self, self.__channel()), delegate)
      ssn.channel.session_attach(name)
      ssn.opened.wait(timeout)
      if ssn.opened.isSet():
        return ssn
      else:
        raise Timeout()
    finally:
      self.lock.release()

  def start(self, timeout=None):
    self.delegate.start()
    self.thread.start()
    self.opened.wait(timeout=timeout)
    if not self.opened.isSet():
      raise Timeout()

  def run(self):
    # XXX: we don't really have a good way to exit this loop without
    # getting the other end to kill the socket
    while True:
      try:
        seg = self.read_segment()
      except Closed:
        break
      self.delegate.received(seg)

  def close(self, timeout=None):
    Channel(self, 0).connection_close()
    self.closed.wait(timeout=timeout)
    if not self.closed.isSet():
      raise Timeout()
    self.thread.join(timeout=timeout)

  def __str__(self):
    return "%s:%s" % self.sock.getsockname()

  def __repr__(self):
    return str(self)

class Channel(Invoker):

  def __init__(self, connection, id):
    self.connection = connection
    self.id = id
    self.session = None

  def resolve_method(self, name):
    inst = self.connection.spec.instructions.get(name)
    if inst is not None and isinstance(inst, Control):
      return inst
    else:
      return None

  def invoke(self, type, args, kwargs):
    cntrl = type.new(args, kwargs)
    sc = StringCodec(self.connection.spec)
    sc.write_control(type, cntrl)
    self.connection.write_segment(Segment(True, True, type.segment_type,
                                          type.track, self.id, sc.encoded))

  def __str__(self):
    return "%s[%s]" % (self.connection, self.id)

  def __repr__(self):
    return str(self)
