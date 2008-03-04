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

import connection010
import session

class Delegate:

  def __init__(self, connection, delegate=session.client):
    self.connection = connection
    self.spec = connection.spec
    self.delegate = delegate
    self.control = self.spec["track.control"].value

  def received(self, seg):
    ssn = self.connection.attached.get(seg.channel)
    if ssn is None:
      ch = connection010.Channel(self.connection, seg.channel)
    else:
      ch = ssn.channel

    if seg.track == self.control:
      cntrl = seg.decode(self.spec)
      attr = cntrl.type.qname.replace(".", "_")
      getattr(self, attr)(ch, cntrl)
    elif ssn is None:
      ch.session_detached()
    else:
      ssn.received(seg)

  def connection_close(self, ch, close):
    ch.connection_close_ok()
    self.connection.sock.close()

  def connection_close_ok(self, ch, close_ok):
    self.connection.closed.set()

  def session_attach(self, ch, a):
    try:
      self.connection.attach(a.name, ch, self.delegate, a.force)
      ch.session_attached(a.name)
    except connection010.ChannelBusy:
      ch.session_detached(a.name)
    except connection010.SessionBusy:
      ch.session_detached(a.name)

  def session_attached(self, ch, a):
    ch.session.opened.set()

  def session_detach(self, ch, d):
    self.connection.detach(d.name, ch)
    ch.session_detached(d.name)

  def session_detached(self, ch, d):
    ssn = self.connection.detach(d.name, ch)
    if ssn is not None:
      ssn.closed.set()

  def session_command_point(self, ch, cp):
    ssn = ch.session
    ssn.receiver.next_id = cp.command_id
    ssn.receiver.next_offset = cp.command_offset

class Server(Delegate):

  def start(self):
    self.connection.read_header()
    self.connection.write_header(self.spec.major, self.spec.minor)
    connection010.Channel(self.connection, 0).connection_start()

  def connection_start_ok(self, ch, start_ok):
    ch.connection_tune()

  def connection_tune_ok(self, ch, tune_ok):
    pass

  def connection_open(self, ch, open):
    self.connection.opened.set()
    ch.connection_open_ok()

class Client(Delegate):

  def start(self):
    self.connection.write_header(self.spec.major, self.spec.minor)
    self.connection.read_header()

  def connection_start(self, ch, start):
    ch.connection_start_ok()

  def connection_tune(self, ch, tune):
    ch.connection_tune_ok()
    ch.connection_open()

  def connection_open_ok(self, ch, open_ok):
    self.connection.opened.set()
