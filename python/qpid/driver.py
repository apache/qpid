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

import address, compat, connection, socket, struct, sys, time
from concurrency import synchronized
from datatypes import RangedSet, Serial
from exceptions import Timeout, VersionError
from framing import OpEncoder, SegmentEncoder, FrameEncoder, FrameDecoder, SegmentDecoder, OpDecoder
from logging import getLogger
from messaging import get_codec, ConnectError, Message, Pattern, UNLIMITED
from ops import *
from selector import Selector
from threading import Condition, Thread
from util import connect
from validator import Map, Types, Values

log = getLogger("qpid.messaging")
rawlog = getLogger("qpid.messaging.io.raw")
opslog = getLogger("qpid.messaging.io.ops")

def addr2reply_to(addr):
  name, subject, options = address.parse(addr)
  return ReplyTo(name, subject)

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

# XXX

DURABLE_DEFAULT=True

# XXX

FILTER_DEFAULTS = {
  "topic": Pattern("*")
  }

# XXX
ppid = 0
try:
  ppid = os.getppid()
except:
  pass

CLIENT_PROPERTIES = {"product": "qpid python client",
                     "version": "development",
                     "platform": os.name,
                     "qpid.client_process": os.path.basename(sys.argv[0]),
                     "qpid.client_pid": os.getpid(),
                     "qpid.client_ppid": ppid}

def noop(): pass

class SessionState:

  def __init__(self, driver, session, name, channel):
    self.driver = driver
    self.session = session
    self.name = name
    self.channel = channel
    self.detached = False
    self.committing = False
    self.aborting = False

    # sender state
    self.sent = Serial(0)
    self.acknowledged = RangedSet()
    self.actions = {}
    self.min_completion = self.sent
    self.max_completion = self.sent
    self.results = {}

    # receiver state
    self.received = None
    self.executed = RangedSet()

    # XXX: need to periodically exchange completion/known_completion

    self.destinations = {}

  def write_query(self, query, handler):
    id = self.sent
    self.write_cmd(query, lambda: handler(self.results.pop(id)))

  def write_cmd(self, cmd, action=noop):
    if action != noop:
      cmd.sync = True
    if self.detached:
      raise Exception("detached")
    cmd.id = self.sent
    self.sent += 1
    self.actions[cmd.id] = action
    self.max_completion = cmd.id
    self.write_op(cmd)

  def write_cmds(self, cmds, action=noop):
    if cmds:
      for cmd in cmds[:-1]:
        self.write_cmd(cmd)
      self.write_cmd(cmds[-1], action)
    else:
      action()

  def write_op(self, op):
    op.channel = self.channel
    self.driver.write_op(op)

# XXX
HEADER="!4s4B"

EMPTY_DP = DeliveryProperties()
EMPTY_MP = MessageProperties()

SUBJECT = "qpid.subject"
TO = "qpid.to"

class Driver:

  def __init__(self, connection):
    self.connection = connection
    self.log_id = "%x" % id(self.connection)
    self._lock = self.connection._lock

    self._selector = Selector.default()
    self.reset()

  def reset(self):
    self._opening = False
    self._closing = False
    self._connected = False
    self._attachments = {}

    self._channel_max = 65536
    self._channels = 0
    self._sessions = {}

    self._socket = None
    self._buf = ""
    self._hdr = ""
    self._op_enc = OpEncoder()
    self._seg_enc = SegmentEncoder()
    self._frame_enc = FrameEncoder()
    self._frame_dec = FrameDecoder()
    self._seg_dec = SegmentDecoder()
    self._op_dec = OpDecoder()
    self._timeout = None

    for ssn in self.connection.sessions.values():
      for m in ssn.acked + ssn.unacked + ssn.incoming:
        m._transfer_id = None
      for snd in ssn.senders:
        snd.linked = False
      for rcv in ssn.receivers:
        rcv.impending = rcv.received
        rcv.linked = False

  @synchronized
  def wakeup(self):
    self.dispatch()
    self._selector.wakeup()

  def start(self):
    self._selector.register(self)

  def fileno(self):
    return self._socket.fileno()

  @synchronized
  def reading(self):
    return self._socket is not None

  @synchronized
  def writing(self):
    return self._socket is not None and self._buf

  @synchronized
  def timing(self):
    return self._timeout

  @synchronized
  def readable(self):
    error = None
    recoverable = False
    try:
      data = self._socket.recv(64*1024)
      if data:
        rawlog.debug("READ[%s]: %r", self.log_id, data)
      else:
        rawlog.debug("ABORTED[%s]: %s", self.log_id, self._socket.getpeername())
        error = "connection aborted"
        recoverable = True
    except socket.error, e:
      error = e
      recoverable = True

    if not error:
      try:
        if len(self._hdr) < 8:
          r = 8 - len(self._hdr)
          self._hdr += data[:r]
          data = data[r:]

          if len(self._hdr) == 8:
            self.do_header(self._hdr)

        self._frame_dec.write(data)
        self._seg_dec.write(*self._frame_dec.read())
        self._op_dec.write(*self._seg_dec.read())
        for op in self._op_dec.read():
          self.assign_id(op)
          opslog.debug("RCVD[%s]: %r", self.log_id, op)
          op.dispatch(self)
      except VersionError, e:
        error = e
      except:
        msg = compat.format_exc()
        error = msg

    if error:
      self._error(error, recoverable)
    else:
      self.dispatch()

    self.connection._waiter.notifyAll()

  def assign_id(self, op):
    if isinstance(op, Command):
      sst = self.get_sst(op)
      op.id = sst.received
      sst.received += 1

  @synchronized
  def writeable(self):
    try:
      n = self._socket.send(self._buf)
      rawlog.debug("SENT[%s]: %r", self.log_id, self._buf[:n])
      self._buf = self._buf[n:]
    except socket.error, e:
      self._error(e, True)
      self.connection._waiter.notifyAll()

  @synchronized
  def timeout(self):
    log.warn("retrying ...")
    self.dispatch()
    self.connection._waiter.notifyAll()

  def _error(self, err, recoverable):
    if self._socket is not None:
      self._socket.close()
    self.reset()
    if recoverable and self.connection.reconnect:
      self._timeout = time.time() + 3
      log.warn("recoverable error: %s" % err)
      log.warn("sleeping 3 seconds")
    else:
      self.connection.error = (err,)

  def write_op(self, op):
    opslog.debug("SENT[%s]: %r", self.log_id, op)
    self._op_enc.write(op)
    self._seg_enc.write(*self._op_enc.read())
    self._frame_enc.write(*self._seg_enc.read())
    self._buf += self._frame_enc.read()

  def do_header(self, hdr):
    cli_major = 0; cli_minor = 10
    magic, _, _, major, minor = struct.unpack(HEADER, hdr)
    if major != cli_major or minor != cli_minor:
      raise VersionError("client: %s-%s, server: %s-%s" %
                         (cli_major, cli_minor, major, minor))

  def do_connection_start(self, start):
    # XXX: should we use some sort of callback for this?
    r = "\0%s\0%s" % (self.connection.username, self.connection.password)
    m = self.connection.mechanism
    self.write_op(ConnectionStartOk(client_properties=CLIENT_PROPERTIES,
                                    mechanism=m, response=r))

  def do_connection_tune(self, tune):
    # XXX: is heartbeat protocol specific?
    if tune.channel_max is not None:
      self.channel_max = tune.channel_max
    self.write_op(ConnectionTuneOk(heartbeat=self.connection.heartbeat,
                                   channel_max=self.channel_max))
    self.write_op(ConnectionOpen())

  def do_connection_open_ok(self, open_ok):
    self._connected = True

  def connection_heartbeat(self, hrt):
    self.write_op(ConnectionHeartbeat())

  def do_connection_close(self, close):
    self.write_op(ConnectionCloseOk())
    if close.reply_code != close_code.normal:
      self.connection.error = (close.reply_code, close.reply_text)
    # XXX: should we do a half shutdown on the socket here?
    # XXX: we really need to test this, we may end up reporting a
    # connection abort after this, if we were to do a shutdown on read
    # and stop reading, then we wouldn't report the abort, that's
    # probably the right thing to do

  def do_connection_close_ok(self, close_ok):
    self._socket.close()
    self.reset()

  def do_session_attached(self, atc):
    pass

  def do_session_command_point(self, cp):
    sst = self.get_sst(cp)
    sst.received = cp.command_id

  def do_session_completed(self, sc):
    sst = self.get_sst(sc)
    for r in sc.commands:
      sst.acknowledged.add(r.lower, r.upper)

    if not sc.commands.empty():
      while sst.min_completion in sc.commands:
        if sst.actions.has_key(sst.min_completion):
          sst.actions.pop(sst.min_completion)()
        sst.min_completion += 1

  def session_known_completed(self, kcmp):
    sst = self.get_sst(kcmp)
    executed = RangedSet()
    for e in sst.executed.ranges:
      for ke in kcmp.ranges:
        if e.lower in ke and e.upper in ke:
          break
      else:
        executed.add_range(e)
    sst.executed = completed

  def do_session_flush(self, sf):
    sst = self.get_sst(sf)
    if sf.expected:
      if sst.received is None:
        exp = None
      else:
        exp = RangedSet(sst.received)
      sst.write_op(SessionExpected(exp))
    if sf.confirmed:
      sst.write_op(SessionConfirmed(sst.executed))
    if sf.completed:
      sst.write_op(SessionCompleted(sst.executed))

  def do_execution_result(self, er):
    sst = self.get_sst(er)
    sst.results[er.command_id] = er.value

  def do_execution_exception(self, ex):
    sst = self.get_sst(ex)
    sst.session.error = (ex,)

  def dispatch(self):
    try:
      if self._socket is None and self.connection._connected and not self._opening:
        self.connect()
      elif self._socket is not None and not self.connection._connected and not self._closing:
        self.disconnect()

      if self._connected and not self._closing:
        for ssn in self.connection.sessions.values():
          self.attach(ssn)
          self.process(ssn)
    except:
      msg = compat.format_exc()
      self.connection.error = (msg,)

  def connect(self):
    try:
      # XXX: should make this non blocking
      self._socket = connect(self.connection.host, self.connection.port)
      self._timeout = None
    except socket.error, e:
      if self.connection.reconnect:
        self._error(e, True)
        return
      else:
        raise e
    self._buf += struct.pack(HEADER, "AMQP", 1, 1, 0, 10)
    self._opening = True

  def disconnect(self):
    self.write_op(ConnectionClose(close_code.normal))
    self._closing = True

  def attach(self, ssn):
    sst = self._attachments.get(ssn)
    if sst is None and not ssn.closed:
      for i in xrange(0, self.channel_max):
        if not self._sessions.has_key(i):
          ch = i
          break
      else:
        raise RuntimeError("all channels used")
      sst = SessionState(self, ssn, ssn.name, ch)
      sst.write_op(SessionAttach(name=ssn.name))
      sst.write_op(SessionCommandPoint(sst.sent, 0))
      sst.outgoing_idx = 0
      sst.acked = []
      if ssn.transactional:
        sst.write_cmd(TxSelect())
      self._attachments[ssn] = sst
      self._sessions[sst.channel] = sst

    for snd in ssn.senders:
      self.link_out(snd)
    for rcv in ssn.receivers:
      self.link_in(rcv)

    if sst is not None and ssn.closing and not sst.detached:
      sst.detached = True
      sst.write_op(SessionDetach(name=ssn.name))

  def get_sst(self, op):
    return self._sessions[op.channel]

  def do_session_detached(self, dtc):
    sst = self._sessions.pop(dtc.channel)
    ssn = sst.session
    del self._attachments[ssn]
    ssn.closed = True

  def do_session_detach(self, dtc):
    sst = self.get_sst(dtc)
    sst.write_op(SessionDetached(name=dtc.name))
    self.do_session_detached(dtc)

  def link_out(self, snd):
    sst = self._attachments.get(snd.session)
    _snd = self._attachments.get(snd)
    if _snd is None and not snd.closing and not snd.closed:
      _snd = Attachment(snd)
      _snd.closing = False

      if snd.target is None:
        snd.error = ("target is None",)
        snd.closed = True
        return

      try:
        _snd.name, _snd.subject, _snd.options = address.parse(snd.target)
      except address.LexError, e:
        snd.error = (e,)
        snd.closed = True
        return
      except address.ParseError, e:
        snd.error = (e,)
        snd.closed = True
        return

      # XXX: subject
      if _snd.options is None:
        _snd.options = {}

      if not self.validate_options(_snd):
        return

      def do_link(type, subtype):
        if type == "topic":
          _snd._exchange = _snd.name
          _snd._routing_key = _snd.subject
        elif type == "queue":
          _snd._exchange = ""
          _snd._routing_key = _snd.name

        snd.linked = True

      self.resolve_declare(sst, _snd, "sender", do_link)
      self._attachments[snd] = _snd

    if snd.linked and snd.closing and not (snd.closed or _snd.closing):
      _snd.closing = True
      def do_unlink():
        del self._attachments[snd]
        snd.closed = True
      if _snd.options.get("delete") in ("always", "sender"):
        self.delete(sst, _snd.name, do_unlink)
      else:
        do_unlink()

  def link_in(self, rcv):
    sst = self._attachments.get(rcv.session)
    _rcv = self._attachments.get(rcv)
    if _rcv is None and not rcv.closing and not rcv.closed:
      _rcv = Attachment(rcv)
      _rcv.destination = str(rcv.id)
      sst.destinations[_rcv.destination] = _rcv
      _rcv.canceled = False
      _rcv.draining = False

      if rcv.source is None:
        rcv.error = ("source is None",)
        rcv.closed = True
        return

      try:
        _rcv.name, _rcv.subject, _rcv.options = address.parse(rcv.source)
      except address.LexError, e:
        rcv.error = (e,)
        rcv.closed = True
        return
      except address.ParseError, e:
        rcv.error = (e,)
        rcv.closed = True
        return

      # XXX: subject
      if _rcv.options is None:
        _rcv.options = {}

      if not self.validate_options(_rcv):
        return

      def do_link(type, subtype):
        if type == "topic":
          _rcv._queue = "%s.%s" % (rcv.session.name, _rcv.destination)
          sst.write_cmd(QueueDeclare(queue=_rcv._queue, durable=DURABLE_DEFAULT, exclusive=True, auto_delete=True))
          filter = _rcv.options.get("filter")
          if _rcv.subject is None and filter is None:
            f = FILTER_DEFAULTS[subtype]
          elif _rcv.subject and filter:
            # XXX
            raise Exception("can't supply both subject and filter")
          elif _rcv.subject:
            # XXX
            from messaging import Pattern
            f = Pattern(_rcv.subject)
          else:
            f = filter
          f._bind(sst, _rcv.name, _rcv._queue)
        elif type == "queue":
          _rcv._queue = _rcv.name

        sst.write_cmd(MessageSubscribe(queue=_rcv._queue, destination=_rcv.destination))
        sst.write_cmd(MessageSetFlowMode(_rcv.destination, flow_mode.credit))
        rcv.linked = True

      self.resolve_declare(sst, _rcv, "receiver", do_link)
      self._attachments[rcv] = _rcv

    if rcv.linked and rcv.closing and not rcv.closed:
      if not _rcv.canceled:
        def do_unlink():
          del self._attachments[rcv]
          del sst.destinations[_rcv.destination]
          rcv.closed = True
        if _rcv.options.get("delete") in ("always", "receiver"):
          sst.write_cmd(MessageCancel(_rcv.destination))
          self.delete(sst, _rcv.name, do_unlink)
        else:
          sst.write_cmd(MessageCancel(_rcv.destination), do_unlink)
        _rcv.canceled = True

  POLICIES = Values("always", "sender", "receiver", "never")

  OPTS = Map({
      "create": POLICIES,
      "delete": POLICIES,
      "assert": POLICIES,
      "node-properties": Map({
          "type": Values("queue", "topic"),
          "durable": Types(bool),
          "x-properties": Map({
              "type": Types(basestring),
              "bindings": Types(list)
              },
              restricted=False)
          })
        })

  def validate_options(self, lnk):
    err = Driver.OPTS.validate(lnk.options)
    if err:
      lnk.target.error = ("error in options: %s" % err,)
      lnk.target.closed = True
      return False
    else:
      return True

  def resolve_declare(self, sst, lnk, dir, action):
    def do_resolved(er, qr):
      err = None
      if er.not_found and not qr.queue:
        if lnk.options.get("create") in ("always", dir):
          err = self.declare(sst, lnk, action)
        else:
          err = ("no such queue: %s" % lnk.name,)
      elif qr.queue:
        try:
          cmds = self.bindings(lnk)
          sst.write_cmds(cmds, lambda: action("queue", None))
        except address.ParseError, e:
          err = (e,)
      else:
        action("topic", er.type)

      if err:
        tgt = lnk.target
        tgt.error = err
        del self._attachments[tgt]
        tgt.closed = True
        return
    self.resolve(sst, lnk.name, do_resolved)

  def resolve(self, sst, name, action):
    args = []
    def do_result(r):
      args.append(r)
    def do_action(r):
      do_result(r)
      action(*args)
    sst.write_query(ExchangeQuery(name), do_result)
    sst.write_query(QueueQuery(name), do_action)

  def declare(self, sst, lnk, action):
    name = lnk.name
    props = lnk.options.get("node-properties", {})
    durable = props.get("durable", DURABLE_DEFAULT)
    type = props.get("type", "queue")
    xprops = props.get("x-properties", {})

    if type == "topic":
      cmd = ExchangeDeclare(exchange=name, durable=durable)
    elif type == "queue":
      cmd = QueueDeclare(queue=name, durable=durable)
    else:
      raise ValueError(type)

    for f in cmd.FIELDS:
      if f.name != "arguments" and xprops.has_key(f.name):
        cmd[f.name] = xprops.pop(f.name)
    if xprops:
      cmd.arguments = xprops

    if type == "topic":
      if cmd.type is None:
        cmd.type = "topic"
      subtype = cmd.type
    else:
      subtype = None

    cmds = [cmd]
    if type == "queue":
      try:
        cmds.extend(self.bindings(lnk))
      except address.ParseError, e:
        return (e,)

    sst.write_cmds(cmds, lambda: action(type, subtype))

  def bindings(self, lnk):
    props = lnk.options.get("node-properties", {})
    xprops = props.get("x-properties", {})
    bindings = xprops.get("bindings", [])
    cmds = []
    for b in bindings:
      n, s, o = address.parse(b)
      cmds.append(ExchangeBind(lnk.name, n, s, o))
    return cmds

  def delete(self, sst, name, action):
    def do_delete(er, qr):
      if not er.not_found:
        sst.write_cmd(ExchangeDelete(name), action)
      elif qr.queue:
        sst.write_cmd(QueueDelete(name), action)
      else:
        action()
    self.resolve(sst, name, do_delete)

  def process(self, ssn):
    if ssn.closed or ssn.closing: return

    sst = self._attachments[ssn]

    while sst.outgoing_idx < len(ssn.outgoing):
      msg = ssn.outgoing[sst.outgoing_idx]
      snd = msg._sender
      # XXX: should check for sender error here
      _snd = self._attachments.get(snd)
      if _snd and snd.linked:
        self.send(snd, msg)
        sst.outgoing_idx += 1
      else:
        break

    for rcv in ssn.receivers:
      self.process_receiver(rcv)

    if ssn.acked:
      messages = [m for m in ssn.acked if m not in sst.acked]
      if messages:
        # XXX: we're ignoring acks that get lost when disconnected,
        # could we deal this via some message-id based purge?
        ids = RangedSet(*[m._transfer_id for m in messages if m._transfer_id is not None])
        for range in ids:
          sst.executed.add_range(range)
        sst.write_op(SessionCompleted(sst.executed))
        def ack_ack():
          for m in messages:
            ssn.acked.remove(m)
            if not ssn.transactional:
              sst.acked.remove(m)
        sst.write_cmd(MessageAccept(ids), ack_ack)
        log.debug("SACK[%s]: %s", ssn.log_id, m)
        sst.acked.extend(messages)

    if ssn.committing and not sst.committing:
      def commit_ok():
        del sst.acked[:]
        ssn.committing = False
        ssn.committed = True
        ssn.aborting = False
        ssn.aborted = False
      sst.write_cmd(TxCommit(), commit_ok)
      sst.committing = True

    if ssn.aborting and not sst.aborting:
      sst.aborting = True
      def do_rb():
        messages = sst.acked + ssn.unacked + ssn.incoming
        ids = RangedSet(*[m._transfer_id for m in messages])
        for range in ids:
          sst.executed.add_range(range)
        sst.write_op(SessionCompleted(sst.executed))
        sst.write_cmd(MessageRelease(ids))
        sst.write_cmd(TxRollback(), do_rb_ok)

      def do_rb_ok():
        del ssn.incoming[:]
        del ssn.unacked[:]
        del sst.acked[:]

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
        sst.aborting = False

      for rcv in ssn.receivers:
        _rcv = self._attachments[rcv]
        sst.write_cmd(MessageStop(_rcv.destination))
      sst.write_cmd(ExecutionSync(), do_rb)

  def grant(self, rcv):
    sst = self._attachments[rcv.session]
    _rcv = self._attachments.get(rcv)
    if _rcv is None or not rcv.linked or _rcv.canceled or _rcv.draining:
      return

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
      sst.write_cmd(MessageFlow(_rcv.destination, credit_unit.byte, UNLIMITED.value))
      sst.write_cmd(MessageFlow(_rcv.destination, credit_unit.message, UNLIMITED.value))
      rcv.impending = UNLIMITED
    elif delta > 0:
      sst.write_cmd(MessageFlow(_rcv.destination, credit_unit.byte, UNLIMITED.value))
      sst.write_cmd(MessageFlow(_rcv.destination, credit_unit.message, delta))
      rcv.impending += delta
    elif delta < 0 and not rcv.draining:
      _rcv.draining = True
      def do_stop():
        rcv.impending = rcv.received
        _rcv.draining = False
        self.grant(rcv)
      sst.write_cmd(MessageStop(_rcv.destination), do_stop)

    if rcv.draining:
      _rcv.draining = True
      def do_flush():
        rcv.impending = rcv.received
        rcv.granted = rcv.impending
        _rcv.draining = False
        rcv.draining = False
      sst.write_cmd(MessageFlush(_rcv.destination), do_flush)


  def process_receiver(self, rcv):
    if rcv.closed: return
    self.grant(rcv)

  def send(self, snd, msg):
    sst = self._attachments[snd.session]
    _snd = self._attachments[snd]

    # XXX: what if subject is specified for a normal queue?
    if _snd._routing_key is None:
      rk = msg.subject
    else:
      rk = _snd._routing_key
    # XXX: do we need to query to figure out how to create the reply-to interoperably?
    if msg.reply_to:
      rt = addr2reply_to(msg.reply_to)
    else:
      rt = None
    dp = DeliveryProperties(routing_key=rk)
    mp = MessageProperties(message_id=msg.id,
                           user_id=msg.user_id,
                           reply_to=rt,
                           correlation_id=msg.correlation_id,
                           content_type=msg.content_type,
                           application_headers=msg.properties)
    if msg.subject is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers[SUBJECT] = msg.subject
    if msg.to is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers[TO] = msg.to
    if msg.durable:
      dp.delivery_mode = delivery_mode.persistent
    enc, dec = get_codec(msg.content_type)
    body = enc(msg.content)
    def msg_acked():
      # XXX: should we log the ack somehow too?
      snd.acked += 1
      m = snd.session.outgoing.pop(0)
      sst.outgoing_idx -= 1
      log.debug("RACK[%s]: %s", sst.session.log_id, msg)
      assert msg == m
    sst.write_cmd(MessageTransfer(destination=_snd._exchange, headers=(dp, mp),
                                  payload=body), msg_acked)
    log.debug("SENT[%s]: %s", sst.session.log_id, msg)

  def do_message_transfer(self, xfr):
    sst = self.get_sst(xfr)
    ssn = sst.session

    msg = self._decode(xfr)
    rcv = sst.destinations[xfr.destination].target
    msg._receiver = rcv
    if rcv.impending is not UNLIMITED:
      assert rcv.received < rcv.impending, "%s, %s" % (rcv.received, rcv.impending)
    rcv.received += 1
    log.debug("RCVD[%s]: %s", ssn.log_id, msg)
    ssn.incoming.append(msg)
    self.connection._waiter.notifyAll()

  def _decode(self, xfr):
    dp = EMPTY_DP
    mp = EMPTY_MP

    for h in xfr.headers:
      if isinstance(h, DeliveryProperties):
        dp = h
      elif isinstance(h, MessageProperties):
        mp = h

    ap = mp.application_headers
    enc, dec = get_codec(mp.content_type)
    content = dec(xfr.payload)
    msg = Message(content)
    msg.id = mp.message_id
    if ap is not None:
      msg.to = ap.get(TO)
      msg.subject = ap.get(SUBJECT)
    msg.user_id = mp.user_id
    if mp.reply_to is not None:
      msg.reply_to = reply_to2addr(mp.reply_to)
    msg.correlation_id = mp.correlation_id
    msg.durable = dp.delivery_mode == delivery_mode.persistent
    msg.redelivered = dp.redelivered
    msg.properties = mp.application_headers
    msg.content_type = mp.content_type
    msg._transfer_id = xfr.id
    return msg
