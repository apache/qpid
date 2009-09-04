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

import compat, connection, socket, sys, time
from concurrency import synchronized
from datatypes import RangedSet, Message as Message010
from exceptions import Timeout
from logging import getLogger
from messaging import get_codec, ConnectError, Message, Pattern, UNLIMITED
from ops import delivery_mode
from session import Client, INCOMPLETE, SessionDetached
from threading import Condition, Thread
from util import connect

log = getLogger("qpid.messaging")

def parse_addr(address):
  parts = address.split("/", 1)
  if len(parts) == 1:
    return parts[0], None
  else:
    return parts[0], parts[i1]

def reply_to2addr(reply_to):
  if reply_to.routing_key is None:
    return reply_to.exchange
  elif reply_to.exchange in (None, ""):
    return reply_to.routing_key
  else:
    return "%s/%s" % (reply_to.exchange, reply_to.routing_key)

class Attachment:

  def __init__(self, target):
    self.target = target

DURABLE_DEFAULT=True

FILTER_DEFAULTS = {
  "topic": Pattern("*")
  }

def delegate(handler, session):
  class Delegate(Client):

    def message_transfer(self, cmd):
      return handler._message_transfer(session, cmd)
  return Delegate

class Driver:

  def __init__(self, connection):
    self.connection = connection
    self._lock = self.connection._lock
    self._wakeup_cond = Condition()
    self._socket = None
    self._conn = None
    self._connected = False
    self._attachments = {}
    self._modcount = self.connection._modcount
    self.thread = Thread(target=self.run)
    self.thread.setDaemon(True)
    # XXX: need to figure out how to join on this thread

  def start(self):
    self.thread.start()

  def wakeup(self):
    self._wakeup_cond.acquire()
    try:
      self._wakeup_cond.notifyAll()
    finally:
      self._wakeup_cond.release()

  def start(self):
    self.thread.start()

  def run(self):
    while True:
      self._wakeup_cond.acquire()
      try:
        if self.connection._modcount <= self._modcount:
          self._wakeup_cond.wait(10)
      finally:
        self._wakeup_cond.release()
      self.dispatch(self.connection._modcount)

  @synchronized
  def dispatch(self, modcount):
    try:
      if self._conn is None and self.connection._connected:
        self.connect()
      elif self._conn is not None and not self.connection._connected:
        self.disconnect()

      if self._conn is not None:
        for ssn in self.connection.sessions.values():
          self.attach(ssn)
          self.process(ssn)

      exi = None
    except:
      exi = sys.exc_info()

    if exi:
      msg = compat.format_exc()
      recoverable = ["aborted", "Connection refused", "SessionDetached", "Connection reset by peer",
                     "Bad file descriptor", "start timed out", "Broken pipe"]
      for r in recoverable:
        if self.connection.reconnect and r in msg:
          print "waiting to retry"
          self.reset()
          time.sleep(3)
          print "retrying..."
          return
      else:
        self.connection.error = (msg,)

    self._modcount = modcount
    self.connection._waiter.notifyAll()

  def connect(self):
    if self._conn is not None:
      return
    try:
      self._socket = connect(self.connection.host, self.connection.port)
    except socket.error, e:
      raise ConnectError(e)
    self._conn = connection.Connection(self._socket)
    try:
      self._conn.start(timeout=10)
      self._connected = True
    except connection.VersionError, e:
      raise ConnectError(e)
    except Timeout:
      print "start timed out"
      raise ConnectError("start timed out")

  def disconnect(self):
    self._conn.close()
    self.reset()

  def reset(self):
    self._conn = None
    self._connected = False
    self._attachments.clear()
    for ssn in self.connection.sessions.values():
      for m in ssn.acked + ssn.unacked + ssn.incoming:
        m._transfer_id = None
      for rcv in ssn.receivers:
        rcv.impending = rcv.received

  def connected(self):
    return self._conn is not None

  def attach(self, ssn):
    _ssn = self._attachments.get(ssn)
    if _ssn is None:
      _ssn = self._conn.session(ssn.name, delegate=delegate(self, ssn))
      _ssn.auto_sync = False
      _ssn.invoke_lock = self._lock
      _ssn.lock = self._lock
      _ssn.condition = self.connection._condition
      if ssn.transactional:
        # XXX: adding an attribute to qpid.session.Session
        _ssn.acked = []
        _ssn.tx_select()
      self._attachments[ssn] = _ssn

    for snd in ssn.senders:
      self.link_out(snd)
    for rcv in ssn.receivers:
      self.link_in(rcv)

    if ssn.closing:
      _ssn.close()
      del self._attachments[ssn]
      ssn.closed = True

  def _exchange_query(self, ssn, address):
    # XXX: auto sync hack is to avoid deadlock on future
    result = ssn.exchange_query(name=address, sync=True)
    ssn.sync()
    return result.get()

  def link_out(self, snd):
    _ssn = self._attachments[snd.session]
    _snd = self._attachments.get(snd)
    if _snd is None:
      _snd = Attachment(snd)
      node, _snd._subject = parse_addr(snd.target)
      result = self._exchange_query(_ssn, node)
      if result.not_found:
        # XXX: should check 'create' option
        _ssn.queue_declare(queue=node, durable=DURABLE_DEFAULT, sync=True)
        _ssn.sync()
        _snd._exchange = ""
        _snd._routing_key = node
      else:
        _snd._exchange = node
        _snd._routing_key = _snd._subject
      self._attachments[snd] = _snd

    if snd.closed:
      del self._attachments[snd]
      return None
    else:
      return _snd

  def link_in(self, rcv):
    _ssn = self._attachments[rcv.session]
    _rcv = self._attachments.get(rcv)
    if _rcv is None:
      _rcv = Attachment(rcv)
      result = self._exchange_query(_ssn, rcv.source)
      if result.not_found:
        _rcv._queue = rcv.source
        # XXX: should check 'create' option
        _ssn.queue_declare(queue=_rcv._queue, durable=DURABLE_DEFAULT)
      else:
        _rcv._queue = "%s.%s" % (rcv.session.name, rcv.destination)
        _ssn.queue_declare(queue=_rcv._queue, durable=DURABLE_DEFAULT, exclusive=True, auto_delete=True)
        if rcv.filter is None:
          f = FILTER_DEFAULTS[result.type]
        else:
          f = rcv.filter
        f._bind(_ssn, rcv.source, _rcv._queue)
      _ssn.message_subscribe(queue=_rcv._queue, destination=rcv.destination)
      _ssn.message_set_flow_mode(rcv.destination, _ssn.flow_mode.credit, sync=True)
      self._attachments[rcv] = _rcv
      # XXX: need to kill syncs
      _ssn.sync()

    if rcv.closing:
      _ssn.message_cancel(rcv.destination, sync=True)
      # XXX: need to kill syncs
      _ssn.sync()
      del self._attachments[rcv]
      rcv.closed = True
      return None
    else:
      return _rcv

  def process(self, ssn):
    if ssn.closing: return

    _ssn = self._attachments[ssn]

    while ssn.outgoing:
      msg = ssn.outgoing[0]
      snd = msg._sender
      self.send(snd, msg)
      ssn.outgoing.pop(0)

    for rcv in ssn.receivers:
      self.process_receiver(rcv)

    if ssn.acked:
      messages = ssn.acked[:]
      ids = RangedSet(*[m._transfer_id for m in messages if m._transfer_id is not None])
      for range in ids:
        _ssn.receiver._completed.add_range(range)
      ch = _ssn.channel
      if ch is None:
        raise SessionDetached()
      ch.session_completed(_ssn.receiver._completed)
      _ssn.message_accept(ids, sync=True)
      # XXX: really need to make this async so that we don't give up the lock
      _ssn.sync()

      # XXX: we're ignoring acks that get lost when disconnected
      for m in messages:
        ssn.acked.remove(m)
        if ssn.transactional:
          _ssn.acked.append(m)

    if ssn.committing:
      _ssn.tx_commit(sync=True)
      # XXX: need to kill syncs
      _ssn.sync()
      del _ssn.acked[:]
      ssn.committing = False
      ssn.committed = True
      ssn.aborting = False
      ssn.aborted = False

    if ssn.aborting:
      for rcv in ssn.receivers:
        _ssn.message_stop(rcv.destination)
      _ssn.sync()

      messages = _ssn.acked + ssn.unacked + ssn.incoming
      ids = RangedSet(*[m._transfer_id for m in messages])
      for range in ids:
        _ssn.receiver._completed.add_range(range)
      _ssn.channel.session_completed(_ssn.receiver._completed)
      _ssn.message_release(ids)
      _ssn.tx_rollback(sync=True)
      _ssn.sync()

      del ssn.incoming[:]
      del ssn.unacked[:]
      del _ssn.acked[:]

      for rcv in ssn.receivers:
        rcv.impending = rcv.received
        rcv.returned = rcv.received
        # XXX: do we need to update granted here as well?

      for rcv in ssn.receivers:
        self.process_receiver(rcv)

      ssn.aborting = False
      ssn.aborted = True
      ssn.committing = False
      ssn.committed = False

  def grant(self, rcv):
    _ssn = self._attachments[rcv.session]
    _rcv = self.link_in(rcv)

    if rcv.granted is UNLIMITED:
      if rcv.impending is UNLIMITED:
        delta = 0
      else:
        delta = UNLIMITED
    elif rcv.impending is UNLIMITED:
      delta = -1
    else:
      delta = max(rcv.granted, rcv.received) - rcv.impending

    if delta is UNLIMITED:
      _ssn.message_flow(rcv.destination, _ssn.credit_unit.byte, UNLIMITED.value)
      _ssn.message_flow(rcv.destination, _ssn.credit_unit.message, UNLIMITED.value)
      rcv.impending = UNLIMITED
    elif delta > 0:
      _ssn.message_flow(rcv.destination, _ssn.credit_unit.byte, UNLIMITED.value)
      _ssn.message_flow(rcv.destination, _ssn.credit_unit.message, delta)
      rcv.impending += delta
    elif delta < 0:
      if rcv.drain:
        _ssn.message_flush(rcv.destination, sync=True)
      else:
        _ssn.message_stop(rcv.destination, sync=True)
      # XXX: need to kill syncs
      _ssn.sync()
      rcv.impending = rcv.received
      self.grant(rcv)

  def process_receiver(self, rcv):
    if rcv.closed: return
    self.grant(rcv)

  def send(self, snd, msg):
    _ssn = self._attachments[snd.session]
    _snd = self.link_out(snd)

    # XXX: what if subject is specified for a normal queue?
    if _snd._routing_key is None:
      rk = msg.subject
    else:
      rk = _snd._routing_key
    # XXX: do we need to query to figure out how to create the reply-to interoperably?
    if msg.reply_to:
      rt = _ssn.reply_to(*parse_addr(msg.reply_to))
    else:
      rt = None
    dp = _ssn.delivery_properties(routing_key=rk)
    mp = _ssn.message_properties(message_id=msg.id,
                                 user_id=msg.user_id,
                                 reply_to=rt,
                                 correlation_id=msg.correlation_id,
                                 content_type=msg.content_type,
                                 application_headers=msg.properties)
    if msg.subject is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers["subject"] = msg.subject
    if msg.to is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers["to"] = msg.to
    if msg.durable:
      dp.delivery_mode = delivery_mode.persistent
    enc, dec = get_codec(msg.content_type)
    body = enc(msg.content)
    _ssn.message_transfer(destination=_snd._exchange,
                          message=Message010(dp, mp, body),
                          sync=True)
    log.debug("SENT [%s] %s", snd.session, msg)
    # XXX: really need to make this async so that we don't give up the lock
    _ssn.sync()
    # XXX: should we log the ack somehow too?
    snd.acked += 1

  @synchronized
  def _message_transfer(self, ssn, cmd):
    m = Message010(cmd.payload)
    m.headers = cmd.headers
    m.id = cmd.id
    msg = self._decode(m)
    rcv = ssn.receivers[int(cmd.destination)]
    msg._receiver = rcv
    if rcv.impending is not UNLIMITED:
      assert rcv.received < rcv.impending
    rcv.received += 1
    log.debug("RECV [%s] %s", ssn, msg)
    ssn.incoming.append(msg)
    self.connection._waiter.notifyAll()
    return INCOMPLETE

  def _decode(self, message):
    dp = message.get("delivery_properties")
    mp = message.get("message_properties")
    ap = mp.application_headers
    enc, dec = get_codec(mp.content_type)
    content = dec(message.body)
    msg = Message(content)
    msg.id = mp.message_id
    if ap is not None:
      msg.to = ap.get("to")
      msg.subject = ap.get("subject")
    msg.user_id = mp.user_id
    if mp.reply_to is not None:
      msg.reply_to = reply_to2addr(mp.reply_to)
    msg.correlation_id = mp.correlation_id
    msg.durable = dp.delivery_mode == delivery_mode.persistent
    msg.properties = mp.application_headers
    msg.content_type = mp.content_type
    msg._transfer_id = message.id
    return msg
