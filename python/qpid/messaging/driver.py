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

import socket, struct, sys, time
from logging import getLogger, DEBUG
from qpid import compat
from qpid import sasl
from qpid.concurrency import synchronized
from qpid.datatypes import RangedSet, Serial
from qpid.framing import OpEncoder, SegmentEncoder, FrameEncoder, \
    FrameDecoder, SegmentDecoder, OpDecoder
from qpid.messaging import address, transports
from qpid.messaging.constants import UNLIMITED, REJECTED, RELEASED
from qpid.messaging.exceptions import *
from qpid.messaging.message import get_codec, Disposition, Message
from qpid.ops import *
from qpid.selector import Selector
from qpid.util import URL, default,get_client_properties_with_defaults
from qpid.validator import And, Context, List, Map, Types, Values
from threading import Condition, Thread

log = getLogger("qpid.messaging")
rawlog = getLogger("qpid.messaging.io.raw")
opslog = getLogger("qpid.messaging.io.ops")

def addr2reply_to(addr):
  name, subject, options = address.parse(addr)
  if options:
    type = options.get("node", {}).get("type")
  else:
    type = None

  if type == "topic":
    return ReplyTo(name, subject)
  else:
    return ReplyTo(None, name)

def reply_to2addr(reply_to):
  if reply_to.exchange in (None, ""):
    return reply_to.routing_key
  elif reply_to.routing_key is None:
    return "%s; {node: {type: topic}}" % reply_to.exchange
  else:
    return "%s/%s; {node: {type: topic}}" % (reply_to.exchange, reply_to.routing_key)

class Attachment:

  def __init__(self, target):
    self.target = target

# XXX

DURABLE_DEFAULT=False

# XXX

class Pattern:
  """
  The pattern filter matches the supplied wildcard pattern against a
  message subject.
  """

  def __init__(self, value):
    self.value = value

  # XXX: this should become part of the driver
  def _bind(self, sst, exchange, queue):
    from qpid.ops import ExchangeBind

    sst.write_cmd(ExchangeBind(exchange=exchange, queue=queue,
                               binding_key=self.value.replace("*", "#")))

SUBJECT_DEFAULTS = {
  "topic": "#"
  }

def noop(): pass
def sync_noop(): pass

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
    self.need_sync = False

    # receiver state
    self.received = None
    self.executed = RangedSet()

    # XXX: need to periodically exchange completion/known_completion

    self.destinations = {}

  def write_query(self, query, handler):
    id = self.sent
    self.write_cmd(query, lambda: handler(self.results.pop(id)))

  def apply_overrides(self, cmd, overrides):
    for k, v in overrides.items():
      cmd[k.replace('-', '_')] = v

  def write_cmd(self, cmd, action=noop, overrides=None, sync=True):
    if overrides:
      self.apply_overrides(cmd, overrides)

    if action != noop:
      cmd.sync = sync
    if self.detached:
      raise Exception("detached")
    cmd.id = self.sent
    self.sent += 1
    self.actions[cmd.id] = action
    self.max_completion = cmd.id
    self.write_op(cmd)
    self.need_sync = not cmd.sync

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

POLICIES = Values("always", "sender", "receiver", "never")
RELIABILITY = Values("unreliable", "at-most-once", "at-least-once",
                     "exactly-once")

DECLARE = Map({}, restricted=False)
BINDINGS = List(Map({
      "exchange": Types(basestring),
      "queue": Types(basestring),
      "key": Types(basestring),
      "arguments": Map({}, restricted=False)
      }))

COMMON_OPTS = {
  "create": POLICIES,
  "delete": POLICIES,
  "assert": POLICIES,
  "node": Map({
      "type": Values("queue", "topic"),
      "durable": Types(bool),
      "x-declare": DECLARE,
      "x-bindings": BINDINGS
      }),
  "link": Map({
      "name": Types(basestring),
      "durable": Types(bool),
      "reliability": RELIABILITY,
      "x-declare": DECLARE,
      "x-bindings": BINDINGS,
      "x-subscribe": Map({}, restricted=False)
      })
  }

RECEIVE_MODES = Values("browse", "consume")

SOURCE_OPTS = COMMON_OPTS.copy()
SOURCE_OPTS.update({
    "mode": RECEIVE_MODES
    })

TARGET_OPTS = COMMON_OPTS.copy()

class LinkIn:

  ADDR_NAME = "source"
  DIR_NAME = "receiver"
  VALIDATOR = Map(SOURCE_OPTS)

  def init_link(self, sst, rcv, _rcv):
    _rcv.destination = str(rcv.id)
    sst.destinations[_rcv.destination] = _rcv
    _rcv.draining = False
    _rcv.bytes_open = False
    _rcv.on_unlink = []

  def do_link(self, sst, rcv, _rcv, type, subtype, action):
    link_opts = _rcv.options.get("link", {})
    if type == "topic":
      default_reliability = "unreliable"
    else:
      default_reliability = "at-least-once"
    reliability = link_opts.get("reliability", default_reliability)
    declare = link_opts.get("x-declare", {})
    subscribe = link_opts.get("x-subscribe", {})
    acq_mode = acquire_mode.pre_acquired
    if reliability in ("unreliable", "at-most-once"):
      rcv._accept_mode = accept_mode.none
    else:
      rcv._accept_mode = accept_mode.explicit

    if type == "topic":
      default_name = "%s.%s" % (rcv.session.name, _rcv.destination)
      _rcv._queue = link_opts.get("name", default_name)
      sst.write_cmd(QueueDeclare(queue=_rcv._queue,
                                 durable=link_opts.get("durable", False),
                                 exclusive=True,
                                 auto_delete=(reliability == "unreliable")),
                    overrides=declare)
      if declare.get("exclusive", True): _rcv.on_unlink = [QueueDelete(_rcv._queue)]
      subject = _rcv.subject or SUBJECT_DEFAULTS.get(subtype)
      bindings = get_bindings(link_opts, _rcv._queue, _rcv.name, subject)
      if not bindings:
        sst.write_cmd(ExchangeBind(_rcv._queue, _rcv.name, subject))

    elif type == "queue":
      _rcv._queue = _rcv.name
      if _rcv.options.get("mode", "consume") == "browse":
        acq_mode = acquire_mode.not_acquired
      bindings = get_bindings(link_opts, queue=_rcv._queue)


    sst.write_cmds(bindings)
    sst.write_cmd(MessageSubscribe(queue=_rcv._queue,
                                   destination=_rcv.destination,
                                   acquire_mode = acq_mode,
                                   accept_mode = rcv._accept_mode),
                  overrides=subscribe)
    sst.write_cmd(MessageSetFlowMode(_rcv.destination, flow_mode.credit), action)

  def do_unlink(self, sst, rcv, _rcv, action=noop):
    link_opts = _rcv.options.get("link", {})
    reliability = link_opts.get("reliability")
    cmds = [MessageCancel(_rcv.destination)]
    cmds.extend(_rcv.on_unlink)
    msgs = [] #release back messages for the closing receiver
    msg = rcv.session._pop(rcv)
    while msg is not None:
      msgs.append(msg)
      msg = rcv.session._pop(rcv)
    if len(msgs) > 0:
      ids = RangedSet(*[m._transfer_id for m in msgs])
      log.debug("releasing back messages: %s, as receiver is closing", ids)
      cmds.append(MessageRelease(ids, True))
    sst.write_cmds(cmds, action)

  def del_link(self, sst, rcv, _rcv):
    del sst.destinations[_rcv.destination]

class LinkOut:

  ADDR_NAME = "target"
  DIR_NAME = "sender"
  VALIDATOR = Map(TARGET_OPTS)

  def init_link(self, sst, snd, _snd):
    _snd.closing = False
    _snd.pre_ack = False

  def do_link(self, sst, snd, _snd, type, subtype, action):
    link_opts = _snd.options.get("link", {})
    reliability = link_opts.get("reliability", "at-least-once")
    _snd.pre_ack = reliability in ("unreliable", "at-most-once")
    if type == "topic":
      _snd._exchange = _snd.name
      _snd._routing_key = _snd.subject
      bindings = get_bindings(link_opts, exchange=_snd.name, key=_snd.subject)
    elif type == "queue":
      _snd._exchange = ""
      _snd._routing_key = _snd.name
      bindings = get_bindings(link_opts, queue=_snd.name)
    sst.write_cmds(bindings, action)

  def do_unlink(self, sst, snd, _snd, action=noop):
    action()

  def del_link(self, sst, snd, _snd):
    pass

class Cache:

  def __init__(self, ttl):
    self.ttl = ttl
    self.entries = {}

  def __setitem__(self, key, value):
    self.entries[key] = time.time(), value

  def __getitem__(self, key):
    tstamp, value = self.entries[key]
    if time.time() - tstamp >= self.ttl:
      del self.entries[key]
      raise KeyError(key)
    else:
      return value

  def __delitem__(self, key):
    del self.entries[key]

# XXX
HEADER="!4s4B"

EMPTY_DP = DeliveryProperties()
EMPTY_MP = MessageProperties()

SUBJECT = "qpid.subject"

CLOSED = "CLOSED"
READ_ONLY = "READ_ONLY"
WRITE_ONLY = "WRITE_ONLY"
OPEN = "OPEN"

class Driver:

  def __init__(self, connection):
    self.connection = connection
    self.log_id = "%x" % id(self.connection)
    self._lock = self.connection._lock

    self._selector = Selector.default()
    self._attempts = 0
    self._delay = self.connection.reconnect_interval_min
    self._reconnect_log = self.connection.reconnect_log
    self._host = 0
    self._retrying = False
    self._next_retry = None
    self._transport = None

    self._timeout = None

    self.engine = None

  def _next_host(self):
    urls = [URL(u) for u in self.connection.reconnect_urls]
    hosts = [(self.connection.host, default(self.connection.port, 5672))] + \
        [(u.host, default(u.port, 5672)) for u in urls]
    if self._host >= len(hosts):
      self._host = 0
    result = hosts[self._host]
    if self._host == 0:
      self._attempts += 1
    self._host = self._host + 1
    return result

  def _num_hosts(self):
    return len(self.connection.reconnect_urls) + 1

  @synchronized
  def wakeup(self):
    self.dispatch()
    self._selector.wakeup()

  def start(self):
    self._selector.register(self)

  def stop(self):
    self._selector.unregister(self)
    if self._transport:
      self.st_closed()

  def fileno(self):
    return self._transport.fileno()

  @synchronized
  def reading(self):
    return self._transport is not None and \
        self._transport.reading(True)

  @synchronized
  def writing(self):
    return self._transport is not None and \
        self._transport.writing(self.engine.pending())

  @synchronized
  def timing(self):
    return self._timeout

  @synchronized
  def readable(self):
    try:
      data = self._transport.recv(64*1024)
      if data is None:
        return
      elif data:
        rawlog.debug("READ[%s]: %r", self.log_id, data)
        self.engine.write(data)
      else:
        self.close_engine()
    except socket.error, e:
      self.close_engine(ConnectionError(text=str(e)))

    self.update_status()

    self._notify()

  def _notify(self):
    if self.connection.error:
      self.connection._condition.gc()
    self.connection._waiter.notifyAll()

  def close_engine(self, e=None):
    if e is None:
      e = ConnectionError(text="connection aborted")

    if (self.connection.reconnect and
        (self.connection.reconnect_limit is None or
         self.connection.reconnect_limit <= 0 or
         self._attempts <= self.connection.reconnect_limit)):
      if self._host < self._num_hosts():
        delay = 0
      else:
        delay = self._delay
        self._delay = min(2*self._delay,
                          self.connection.reconnect_interval_max)
      self._next_retry = time.time() + delay
      if self._reconnect_log:
        log.warn("recoverable error[attempt %s]: %s" % (self._attempts, e))
        if delay > 0:
          log.warn("sleeping %s seconds" % delay)
      self._retrying = True
      self.engine.close()
    else:
      self.engine.close(e)

    self.schedule()

  def update_status(self):
    status = self.engine.status()
    return getattr(self, "st_%s" % status.lower())()

  def st_closed(self):
    # XXX: this log statement seems to sometimes hit when the socket is not connected
    # XXX: rawlog.debug("CLOSE[%s]: %s", self.log_id, self._socket.getpeername())
    self._transport.close()
    self._transport = None
    self.engine = None
    return True

  def st_open(self):
    return False

  @synchronized
  def writeable(self):
    notify = False
    try:
      n = self._transport.send(self.engine.peek())
      if n == 0: return
      sent = self.engine.read(n)
      rawlog.debug("SENT[%s]: %r", self.log_id, sent)
    except socket.error, e:
      self.close_engine(e)
      notify = True

    if self.update_status() or notify:
      self._notify()

  @synchronized
  def timeout(self):
    self.dispatch()
    self._notify()
    self.schedule()

  def schedule(self):
    times = []
    if self.connection.heartbeat:
      times.append(time.time() + self.connection.heartbeat)
    if self._next_retry:
      times.append(self._next_retry)
    if times:
      self._timeout = min(times)
    else:
      self._timeout = None

  def dispatch(self):
    try:
      if self._transport is None:
        if self.connection._connected and not self.connection.error:
          self.connect()
      else:
        self.engine.dispatch()
    except HeartbeatTimeout, e:
      self.close_engine(e)
    except ContentError, e:
      msg = compat.format_exc()
      self.connection.error = ContentError(text=msg)
    except:
      # XXX: Does socket get leaked if this occurs?
      msg = compat.format_exc()
      self.connection.error = InternalError(text=msg)

  def connect(self):
    if self._retrying and time.time() < self._next_retry:
      return

    try:
      # XXX: should make this non blocking
      host, port = self._next_host()
      if self._retrying and self._reconnect_log:
        log.warn("trying: %s:%s", host, port)
      self.engine = Engine(self.connection)
      self.engine.open()
      rawlog.debug("OPEN[%s]: %s:%s", self.log_id, host, port)
      trans = transports.TRANSPORTS.get(self.connection.transport)
      if trans:
        self._transport = trans(self.connection, host, port)
      else:
        raise ConnectError("no such transport: %s" % self.connection.transport)
      if self._retrying and self._reconnect_log:
        log.warn("reconnect succeeded: %s:%s", host, port)
      self._next_retry = None
      self._attempts = 0
      self._delay = self.connection.reconnect_interval_min
      self._retrying = False
      self.schedule()
    except socket.error, e:
      self.close_engine(ConnectError(text=str(e)))

DEFAULT_DISPOSITION = Disposition(None)

def get_bindings(opts, queue=None, exchange=None, key=None):
  bindings = opts.get("x-bindings", [])
  cmds = []
  for b in bindings:
    exchange = b.get("exchange", exchange)
    queue = b.get("queue", queue)
    key = b.get("key", key)
    args = b.get("arguments", {})
    cmds.append(ExchangeBind(queue, exchange, key, args))
  return cmds

CONNECTION_ERRS = {
  # anythong not here (i.e. everything right now) will default to
  # connection error
  }

SESSION_ERRS = {
  # anything not here will default to session error
  error_code.unauthorized_access: UnauthorizedAccess,
  error_code.not_found: NotFound,
  error_code.resource_locked: ReceiverError,
  error_code.resource_limit_exceeded: TargetCapacityExceeded,
  error_code.internal_error: ServerError
  }

class Engine:

  def __init__(self, connection):
    self.connection = connection
    self.log_id = "%x" % id(self.connection)
    self._closing = False
    self._connected = False
    self._attachments = {}

    self._in = LinkIn()
    self._out = LinkOut()

    self._channel_max = 65536
    self._channels = 0
    self._sessions = {}

    self.address_cache = Cache(self.connection.address_ttl)

    self._status = CLOSED
    self._buf = ""
    self._hdr = ""
    self._last_in = None
    self._last_out = None
    self._op_enc = OpEncoder()
    self._seg_enc = SegmentEncoder()
    self._frame_enc = FrameEncoder()
    self._frame_dec = FrameDecoder()
    self._seg_dec = SegmentDecoder()
    self._op_dec = OpDecoder()

    self._sasl = sasl.Client()
    if self.connection.username:
      self._sasl.setAttr("username", self.connection.username)
    if self.connection.password:
      self._sasl.setAttr("password", self.connection.password)
    if self.connection.host:
      self._sasl.setAttr("host", self.connection.host)
    self._sasl.setAttr("service", self.connection.sasl_service)
    if self.connection.sasl_min_ssf is not None:
      self._sasl.setAttr("minssf", self.connection.sasl_min_ssf)
    if self.connection.sasl_max_ssf is not None:
      self._sasl.setAttr("maxssf", self.connection.sasl_max_ssf)
    self._sasl.init()
    self._sasl_encode = False
    self._sasl_decode = False

  def _reset(self):
    self.connection._transport_connected = False

    for ssn in self.connection.sessions.values():
      for m in ssn.acked + ssn.unacked + ssn.incoming:
        m._transfer_id = None
      for snd in ssn.senders:
        snd.linked = False
      for rcv in ssn.receivers:
        rcv.impending = rcv.received
        rcv.linked = False

  def status(self):
    return self._status

  def write(self, data):
    self._last_in = time.time()
    try:
      if self._sasl_decode:
        data = self._sasl.decode(data)

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
      self.dispatch()
    except MessagingError, e:
      self.close(e)
    except:
      self.close(InternalError(text=compat.format_exc()))

  def close(self, e=None):
    self._reset()
    if e:
      self.connection.error = e
    self._status = CLOSED

  def assign_id(self, op):
    if isinstance(op, Command):
      sst = self.get_sst(op)
      op.id = sst.received
      sst.received += 1

  def pending(self):
    return len(self._buf)

  def read(self, n):
    result = self._buf[:n]
    self._buf = self._buf[n:]
    return result

  def peek(self):
    return self._buf

  def write_op(self, op):
    opslog.debug("SENT[%s]: %r", self.log_id, op)
    self._op_enc.write(op)
    self._seg_enc.write(*self._op_enc.read())
    self._frame_enc.write(*self._seg_enc.read())
    bytes = self._frame_enc.read()
    if self._sasl_encode:
      bytes = self._sasl.encode(bytes)
    self._buf += bytes
    self._last_out = time.time()

  def do_header(self, hdr):
    cli_major = 0; cli_minor = 10
    magic, _, _, major, minor = struct.unpack(HEADER, hdr)
    if major != cli_major or minor != cli_minor:
      raise VersionError(text="client: %s-%s, server: %s-%s" %
                         (cli_major, cli_minor, major, minor))

  def do_connection_start(self, start):
    if self.connection.sasl_mechanisms:
      permitted = self.connection.sasl_mechanisms.split()
      mechs = [m for m in start.mechanisms if m in permitted]
    else:
      mechs = start.mechanisms
    try:
      mech, initial = self._sasl.start(" ".join(mechs))
    except sasl.SASLError, e:
      raise AuthenticationFailure(text=str(e))

    client_properties = get_client_properties_with_defaults(provided_client_properties=self.connection.client_properties);
    self.write_op(ConnectionStartOk(client_properties=client_properties,
                                    mechanism=mech, response=initial))

  def do_connection_secure(self, secure):
    resp = self._sasl.step(secure.challenge)
    self.write_op(ConnectionSecureOk(response=resp))

  def do_connection_tune(self, tune):
    # XXX: is heartbeat protocol specific?
    if tune.channel_max is not None:
      self.channel_max = tune.channel_max
    self.write_op(ConnectionTuneOk(heartbeat=self.connection.heartbeat,
                                   channel_max=self.channel_max))
    self.write_op(ConnectionOpen())
    self._sasl_encode = True

  def do_connection_open_ok(self, open_ok):
    self.connection.auth_username = self._sasl.auth_username()
    self._connected = True
    self._sasl_decode = True
    self.connection._transport_connected = True

  def do_connection_heartbeat(self, hrt):
    pass

  def do_connection_close(self, close):
    self.write_op(ConnectionCloseOk())
    if close.reply_code != close_code.normal:
      exc = CONNECTION_ERRS.get(close.reply_code, ConnectionError)
      self.connection.error = exc(close.reply_code, close.reply_text)
    # XXX: should we do a half shutdown on the socket here?
    # XXX: we really need to test this, we may end up reporting a
    # connection abort after this, if we were to do a shutdown on read
    # and stop reading, then we wouldn't report the abort, that's
    # probably the right thing to do

  def do_connection_close_ok(self, close_ok):
    self.close()

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

  def do_session_request_timeout(self, rt):
    sst = self.get_sst(rt)
    sst.write_op(SessionTimeout(timeout=0))

  def do_execution_result(self, er):
    sst = self.get_sst(er)
    sst.results[er.command_id] = er.value
    sst.executed.add(er.id)

  def do_execution_exception(self, ex):
    sst = self.get_sst(ex)
    exc = SESSION_ERRS.get(ex.error_code, SessionError)
    sst.session.error = exc(ex.error_code, ex.description)

  def dispatch(self):
    if not self.connection._connected and not self._closing and self._status != CLOSED:
      self.disconnect()

    if self._connected and not self._closing:
      for ssn in self.connection.sessions.values():
        self.attach(ssn)
        self.process(ssn)

      if self.connection.heartbeat and self._status != CLOSED:
        now = time.time()
        if self._last_in is not None and \
              now - self._last_in > 2*self.connection.heartbeat:
          raise HeartbeatTimeout(text="heartbeat timeout")
        if self._last_out is None or now - self._last_out >= self.connection.heartbeat/2.0:
          self.write_op(ConnectionHeartbeat())

  def open(self):
    self._reset()
    self._status = OPEN
    self._buf += struct.pack(HEADER, "AMQP", 1, 1, 0, 10)

  def disconnect(self):
    self.write_op(ConnectionClose(close_code.normal))
    self._closing = True

  def attach(self, ssn):
    if ssn.closed: return
    sst = self._attachments.get(ssn)
    if sst is None:
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
      sst.acked_idx = 0
      if ssn.transactional:
        sst.write_cmd(TxSelect())
      self._attachments[ssn] = sst
      self._sessions[sst.channel] = sst

    for snd in ssn.senders:
      self.link(snd, self._out, snd.target)
    for rcv in ssn.receivers:
      self.link(rcv, self._in, rcv.source)

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

  def link(self, lnk, dir, addr):
    sst = self._attachments.get(lnk.session)
    _lnk = self._attachments.get(lnk)

    if _lnk is None and not lnk.closed:
      _lnk = Attachment(lnk)
      _lnk.closing = False
      dir.init_link(sst, lnk, _lnk)

      err = self.parse_address(_lnk, dir, addr) or self.validate_options(_lnk, dir)
      if err:
        lnk.error = err
        lnk.closed = True
        return

      def linked():
        lnk.linked = True

      def resolved(type, subtype):
        dir.do_link(sst, lnk, _lnk, type, subtype, linked)

      self.resolve_declare(sst, _lnk, dir.DIR_NAME, resolved)
      self._attachments[lnk] = _lnk

    if lnk.linked and lnk.closing and not lnk.closed:
      if not _lnk.closing:
        def unlinked():
          dir.del_link(sst, lnk, _lnk)
          del self._attachments[lnk]
          lnk.closed = True
        if _lnk.options.get("delete") in ("always", dir.DIR_NAME):
          dir.do_unlink(sst, lnk, _lnk)
          self.delete(sst, _lnk.name, unlinked)
        else:
          dir.do_unlink(sst, lnk, _lnk, unlinked)
        _lnk.closing = True
    elif not lnk.linked and lnk.closing and not lnk.closed:
      if lnk.error: lnk.closed = True

  def parse_address(self, lnk, dir, addr):
    if addr is None:
      return MalformedAddress(text="%s is None" % dir.ADDR_NAME)
    else:
      try:
        lnk.name, lnk.subject, lnk.options = address.parse(addr)
        # XXX: subject
        if lnk.options is None:
          lnk.options = {}
      except address.LexError, e:
        return MalformedAddress(text=str(e))
      except address.ParseError, e:
        return MalformedAddress(text=str(e))

  def validate_options(self, lnk, dir):
    ctx = Context()
    err = dir.VALIDATOR.validate(lnk.options, ctx)
    if err: return InvalidOption(text="error in options: %s" % err)

  def resolve_declare(self, sst, lnk, dir, action):
    declare = lnk.options.get("create") in ("always", dir)
    assrt = lnk.options.get("assert") in ("always", dir)
    def do_resolved(type, subtype):
      err = None
      if type is None:
        if declare:
          err = self.declare(sst, lnk, action)
        else:
          err = NotFound(text="no such queue: %s" % lnk.name)
      else:
        if assrt:
          expected = lnk.options.get("node", {}).get("type")
          if expected and type != expected:
            err = AssertionFailed(text="expected %s, got %s" % (expected, type))
        if err is None:
          action(type, subtype)

      if err:
        tgt = lnk.target
        tgt.error = err
        del self._attachments[tgt]
        tgt.closed = True
        return
    self.resolve(sst, lnk.name, do_resolved, force=declare)

  def resolve(self, sst, name, action, force=False):
    if not force:
      try:
        type, subtype = self.address_cache[name]
        action(type, subtype)
        return
      except KeyError:
        pass

    args = []
    def do_result(r):
      args.append(r)
    def do_action(r):
      do_result(r)
      er, qr = args
      if er.not_found and not qr.queue:
        type, subtype = None, None
      elif qr.queue:
        type, subtype = "queue", None
      else:
        type, subtype = "topic", er.type
      if type is not None:
        self.address_cache[name] = (type, subtype)
      action(type, subtype)
    sst.write_query(ExchangeQuery(name), do_result)
    sst.write_query(QueueQuery(name), do_action)

  def declare(self, sst, lnk, action):
    name = lnk.name
    props = lnk.options.get("node", {})
    durable = props.get("durable", DURABLE_DEFAULT)
    type = props.get("type", "queue")
    declare = props.get("x-declare", {})

    if type == "topic":
      cmd = ExchangeDeclare(exchange=name, durable=durable)
      bindings = get_bindings(props, exchange=name)
    elif type == "queue":
      cmd = QueueDeclare(queue=name, durable=durable)
      bindings = get_bindings(props, queue=name)
    else:
      raise ValueError(type)

    sst.apply_overrides(cmd, declare)

    if type == "topic":
      if cmd.type is None:
        cmd.type = "topic"
      subtype = cmd.type
    else:
      subtype = None

    cmds = [cmd]
    cmds.extend(bindings)

    def declared():
      self.address_cache[name] = (type, subtype)
      action(type, subtype)

    sst.write_cmds(cmds, declared)

  def delete(self, sst, name, action):
    def deleted():
      del self.address_cache[name]
      action()

    def do_delete(type, subtype):
      if type == "topic":
        sst.write_cmd(ExchangeDelete(name), deleted)
      elif type == "queue":
        sst.write_cmd(QueueDelete(name), deleted)
      elif type is None:
        action()
      else:
        raise ValueError(type)
    self.resolve(sst, name, do_delete, force=True)

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

    for snd in ssn.senders:
      # XXX: should included snd.acked in this
      if snd.synced >= snd.queued and sst.need_sync:
        sst.write_cmd(ExecutionSync(), sync_noop)

    for rcv in ssn.receivers:
      self.process_receiver(rcv)

    if ssn.acked:
      messages = ssn.acked[sst.acked_idx:]
      if messages:
        ids = RangedSet()

        disposed = [(DEFAULT_DISPOSITION, [])]
        acked = []
        for m in messages:
          # XXX: we're ignoring acks that get lost when disconnected,
          # could we deal this via some message-id based purge?
          if m._transfer_id is None:
            acked.append(m)
            continue
          ids.add(m._transfer_id)
          if m._receiver._accept_mode is accept_mode.explicit:
            disp = m._disposition or DEFAULT_DISPOSITION
            last, msgs = disposed[-1]
            if disp.type is last.type and disp.options == last.options:
              msgs.append(m)
            else:
              disposed.append((disp, [m]))
          else:
            acked.append(m)

        for range in ids:
          sst.executed.add_range(range)
        sst.write_op(SessionCompleted(sst.executed))

        def ack_acker(msgs):
          def ack_ack():
            for m in msgs:
              ssn.acked.remove(m)
              sst.acked_idx -= 1
              # XXX: should this check accept_mode too?
              if not ssn.transactional:
                sst.acked.remove(m)
          return ack_ack

        for disp, msgs in disposed:
          if not msgs: continue
          if disp.type is None:
            op = MessageAccept
          elif disp.type is RELEASED:
            op = MessageRelease
          elif disp.type is REJECTED:
            op = MessageReject
          sst.write_cmd(op(RangedSet(*[m._transfer_id for m in msgs]),
                           **disp.options),
                        ack_acker(msgs))
          if log.isEnabledFor(DEBUG):
            for m in msgs:
              log.debug("SACK[%s]: %s, %s", ssn.log_id, m, m._disposition)

        sst.acked.extend(messages)
        sst.acked_idx += len(messages)
        ack_acker(acked)()

    if ssn.committing and not sst.committing:
      def commit_ok():
        del sst.acked[:]
        ssn.committing = False
        ssn.committed = True
        ssn.aborting = False
        ssn.aborted = False
        sst.committing = False
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
        sst.write_cmd(MessageRelease(ids, True))
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
    if _rcv is None or not rcv.linked or _rcv.closing or _rcv.draining:
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
      if not _rcv.bytes_open:
        sst.write_cmd(MessageFlow(_rcv.destination, credit_unit.byte, UNLIMITED.value))
        _rcv.bytes_open = True
      sst.write_cmd(MessageFlow(_rcv.destination, credit_unit.message, UNLIMITED.value))
      rcv.impending = UNLIMITED
    elif delta > 0:
      if not _rcv.bytes_open:
        sst.write_cmd(MessageFlow(_rcv.destination, credit_unit.byte, UNLIMITED.value))
        _rcv.bytes_open = True
      sst.write_cmd(MessageFlow(_rcv.destination, credit_unit.message, delta))
      rcv.impending += delta
    elif delta < 0 and not rcv.draining:
      _rcv.draining = True
      def do_stop():
        rcv.impending = rcv.received
        _rcv.draining = False
        _rcv.bytes_open = False
        self.grant(rcv)
      sst.write_cmd(MessageStop(_rcv.destination), do_stop)

    if rcv.draining:
      _rcv.draining = True
      def do_flush():
        rcv.impending = rcv.received
        rcv.granted = rcv.impending
        _rcv.draining = False
        _rcv.bytes_open = False
        rcv.draining = False
      sst.write_cmd(MessageFlush(_rcv.destination), do_flush)


  def process_receiver(self, rcv):
    if rcv.closed: return
    self.grant(rcv)

  def send(self, snd, msg):
    sst = self._attachments[snd.session]
    _snd = self._attachments[snd]

    if msg.subject is None or _snd._exchange == "":
      rk = _snd._routing_key
    else:
      rk = msg.subject

    if msg.subject is None:
      subject = _snd.subject
    else:
      subject = msg.subject

    # XXX: do we need to query to figure out how to create the reply-to interoperably?
    if msg.reply_to:
      rt = addr2reply_to(msg.reply_to)
    else:
      rt = None
    content_encoding = msg.properties.get("x-amqp-0-10.content-encoding")
    dp = DeliveryProperties(routing_key=rk)
    mp = MessageProperties(message_id=msg.id,
                           user_id=msg.user_id,
                           reply_to=rt,
                           correlation_id=msg.correlation_id,
                           app_id = msg.properties.get("x-amqp-0-10.app-id"),
                           content_type=msg.content_type,
                           content_encoding=content_encoding,
                           application_headers=msg.properties)
    if subject is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers[SUBJECT] = subject
    if msg.durable is not None:
      if msg.durable:
        dp.delivery_mode = delivery_mode.persistent
      else:
        dp.delivery_mode = delivery_mode.non_persistent
    if msg.priority is not None:
      dp.priority = msg.priority
    if msg.ttl is not None:
      dp.ttl = long(msg.ttl*1000)
    enc, dec = get_codec(msg.content_type)
    try:
      body = enc(msg.content)
    except AttributeError, e:
      # convert to non-blocking EncodeError
      raise EncodeError(e)

    # XXX: this is not safe for out of order, can this be triggered by pre_ack?
    def msg_acked():
      # XXX: should we log the ack somehow too?
      snd.acked += 1
      m = snd.session.outgoing.pop(0)
      sst.outgoing_idx -= 1
      log.debug("RACK[%s]: %s", sst.session.log_id, msg)
      assert msg == m

    xfr = MessageTransfer(destination=_snd._exchange, headers=(dp, mp),
                          payload=body)

    if _snd.pre_ack:
      sst.write_cmd(xfr)
    else:
      sst.write_cmd(xfr, msg_acked, sync=msg._sync)

    log.debug("SENT[%s]: %s", sst.session.log_id, msg)

    if _snd.pre_ack:
      msg_acked()

  def do_message_transfer(self, xfr):
    sst = self.get_sst(xfr)
    ssn = sst.session

    msg = self._decode(xfr)
    rcv = sst.destinations[xfr.destination].target
    msg._receiver = rcv
    if rcv.closing or rcv.closed: # release message to a closing receiver
      ids = RangedSet(*[msg._transfer_id])
      log.debug("releasing back %s message: %s, as receiver is closing", ids, msg)
      sst.write_cmd(MessageRelease(ids, True))
      return
    if rcv.impending is not UNLIMITED:
      assert rcv.received < rcv.impending, "%s, %s" % (rcv.received, rcv.impending)
    rcv.received += 1
    log.debug("RCVD[%s]: %s", ssn.log_id, msg)
    ssn.incoming.append(msg)

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
    try:
      content = dec(xfr.payload)
    except Exception, e:
      raise DecodeError(e)
    msg = Message(content)
    msg.id = mp.message_id
    if ap is not None:
      msg.subject = ap.get(SUBJECT)
    msg.user_id = mp.user_id
    if mp.reply_to is not None:
      msg.reply_to = reply_to2addr(mp.reply_to)
    msg.correlation_id = mp.correlation_id
    if dp.delivery_mode is not None:
      msg.durable = dp.delivery_mode == delivery_mode.persistent
    msg.priority = dp.priority
    if dp.ttl is not None:
      msg.ttl = dp.ttl/1000.0
    msg.redelivered = dp.redelivered
    msg.properties = mp.application_headers or {}
    if mp.app_id is not None:
      msg.properties["x-amqp-0-10.app-id"] = mp.app_id
    if mp.content_encoding is not None:
      msg.properties["x-amqp-0-10.content-encoding"] = mp.content_encoding
    if dp.routing_key is not None:
      msg.properties["x-amqp-0-10.routing-key"] = dp.routing_key
    if dp.timestamp is not None:
      msg.properties["x-amqp-0-10.timestamp"] = dp.timestamp
    msg.content_type = mp.content_type
    msg._transfer_id = xfr.id
    return msg
