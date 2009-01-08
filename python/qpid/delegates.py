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

import os, connection, session
from util import notify
from datatypes import RangedSet
from logging import getLogger

log = getLogger("qpid.io.ctl")

class Delegate:

  def __init__(self, connection, delegate=session.client):
    self.connection = connection
    self.spec = connection.spec
    self.delegate = delegate
    self.control = self.spec["track.control"].value

  def received(self, seg):
    ssn = self.connection.attached.get(seg.channel)
    if ssn is None:
      ch = connection.Channel(self.connection, seg.channel)
    else:
      ch = ssn.channel

    if seg.track == self.control:
      ctl = seg.decode(self.spec)
      log.debug("RECV %s", ctl)
      attr = ctl._type.qname.replace(".", "_")
      getattr(self, attr)(ch, ctl)
    elif ssn is None:
      ch.session_detached()
    else:
      ssn.received(seg)

  def connection_close(self, ch, close):
    self.connection.close_code = (close.reply_code, close.reply_text)
    ch.connection_close_ok()
    self.connection.sock.close()
    if not self.connection.opened:
      self.connection.failed = True
      notify(self.connection.condition)

  def connection_close_ok(self, ch, close_ok):
    self.connection.opened = False
    notify(self.connection.condition)

  def connection_heartbeat(self, ch, hrt):
    pass

  def session_attach(self, ch, a):
    try:
      self.connection.attach(a.name, ch, self.delegate, a.force)
      ch.session_attached(a.name)
    except connection.ChannelBusy:
      ch.session_detached(a.name)
    except connection.SessionBusy:
      ch.session_detached(a.name)

  def session_attached(self, ch, a):
    notify(ch.session.condition)

  def session_detach(self, ch, d):
    #send back the confirmation of detachment before removing the
    #channel from the attached set; this avoids needing to hold the
    #connection lock during the sending of this control and ensures
    #that if the channel is immediately reused for a new session the
    #attach request will follow the detached notification.
    ch.session_detached(d.name)
    ssn = self.connection.detach(d.name, ch)

  def session_detached(self, ch, d):
    self.connection.detach(d.name, ch)

  def session_request_timeout(self, ch, rt):
    ch.session_timeout(rt.timeout);

  def session_command_point(self, ch, cp):
    ssn = ch.session
    ssn.receiver.next_id = cp.command_id
    ssn.receiver.next_offset = cp.command_offset

  def session_completed(self, ch, cmp):
    ch.session.sender.completed(cmp.commands)
    if cmp.timely_reply:
      ch.session_known_completed(cmp.commands)
    notify(ch.session.condition)

  def session_known_completed(self, ch, kn_cmp):
    ch.session.receiver.known_completed(kn_cmp.commands)

  def session_flush(self, ch, f):
    rcv = ch.session.receiver
    if f.expected:
      if rcv.next_id == None:
        exp = None
      else:
        exp = RangedSet(rcv.next_id)
      ch.session_expected(exp)
    if f.confirmed:
      ch.session_confirmed(rcv._completed)
    if f.completed:
      ch.session_completed(rcv._completed)

class Server(Delegate):

  def start(self):
    self.connection.read_header()
    self.connection.write_header(self.spec.major, self.spec.minor)
    connection.Channel(self.connection, 0).connection_start(mechanisms=["ANONYMOUS"])

  def connection_start_ok(self, ch, start_ok):
    ch.connection_tune(channel_max=65535)

  def connection_tune_ok(self, ch, tune_ok):
    pass

  def connection_open(self, ch, open):
    self.connection.opened = True
    ch.connection_open_ok()
    notify(self.connection.condition)

class Client(Delegate):

  PROPERTIES = {"product": "qpid python client",
                "version": "development",
                "platform": os.name}

  def __init__(self, connection, username="guest", password="guest",
               mechanism="PLAIN", heartbeat=None):
    Delegate.__init__(self, connection)
    self.username = username
    self.password = password
    self.mechanism = mechanism
    self.heartbeat = heartbeat

  def start(self):
    self.connection.write_header(self.spec.major, self.spec.minor)
    self.connection.read_header()

  def connection_start(self, ch, start):
    r = "\0%s\0%s" % (self.username, self.password)
    ch.connection_start_ok(client_properties=Client.PROPERTIES, mechanism=self.mechanism, response=r)

  def connection_tune(self, ch, tune):
    ch.connection_tune_ok(heartbeat=self.heartbeat)
    ch.connection_open()

  def connection_open_ok(self, ch, open_ok):
    self.connection.opened = True
    notify(self.connection.condition)
