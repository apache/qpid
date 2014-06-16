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

"""
A candidate high level messaging API for python.

Areas that still need work:

  - definition of the arguments for L{Session.sender} and L{Session.receiver}
  - standard L{Message} properties
  - L{Message} content encoding
  - protocol negotiation/multiprotocol impl
"""

from logging import getLogger
from math import ceil
from qpid.codec010 import StringCodec
from qpid.concurrency import synchronized, Waiter, Condition
from qpid.datatypes import Serial, uuid4
from qpid.messaging.constants import *
from qpid.messaging.exceptions import *
from qpid.messaging.message import *
from qpid.ops import PRIMITIVE
from qpid.util import default, URL
from threading import Thread, RLock

log = getLogger("qpid.messaging")

static = staticmethod

class Endpoint:

  def _ecwait(self, predicate, timeout=None):
    result = self._ewait(lambda: self.closed or predicate(), timeout)
    self.check_closed()
    return result

class Connection(Endpoint):

  """
  A Connection manages a group of L{Sessions<Session>} and connects
  them with a remote endpoint.
  """

  @static
  def establish(url=None, timeout=None, **options):
    """
    Constructs a L{Connection} with the supplied parameters and opens
    it.
    """
    conn = Connection(url, **options)
    conn.open(timeout=timeout)
    return conn

  def __init__(self, url=None, **options):
    """
    Creates a connection. A newly created connection must be opened
    with the Connection.open() method before it can be used.

    @type url: str
    @param url: [ <username> [ / <password> ] @ ] <host> [ : <port> ]
    @type host: str
    @param host: the name or ip address of the remote host (overriden by url)
    @type port: int
    @param port: the port number of the remote host (overriden by url)
    @type transport: str
    @param transport: one of tcp, tcp+tls, or ssl (alias for tcp+tls)
    @type heartbeat: int
    @param heartbeat: heartbeat interval in seconds

    @type username: str
    @param username: the username for authentication (overriden by url)
    @type password: str
    @param password: the password for authentication (overriden by url)
    @type sasl_mechanisms: str
    @param sasl_mechanisms: space separated list of permitted sasl mechanisms
    @type sasl_service: str
    @param sasl_service: the service name if needed by the SASL mechanism in use
    @type sasl_min_ssf: int
    @param sasl_min_ssf: the minimum acceptable security strength factor
    @type sasl_max_ssf: int
    @param sasl_max_ssf: the maximum acceptable security strength factor

    @type reconnect: bool
    @param reconnect: enable/disable automatic reconnect
    @type reconnect_timeout: float
    @param reconnect_timeout: total time to attempt reconnect
    @type reconnect_interval_min: float
    @param reconnect_interval_min: minimum interval between reconnect attempts
    @type reconnect_interval_max: float
    @param reconnect_interval_max: maximum interval between reconnect attempts
    @type reconnect_interval: float
    @param reconnect_interval: set both min and max reconnect intervals
    @type reconnect_limit: int
    @param reconnect_limit: limit the total number of reconnect attempts
    @type reconnect_urls: list[str]
    @param reconnect_urls: list of backup hosts specified as urls

    @type address_ttl: float
    @param address_ttl: time until cached address resolution expires

    @type ssl_keyfile: str
    @param ssl_keyfile: file with client's private key (PEM format)
    @type ssl_certfile: str
    @param ssl_certfile: file with client's public (eventually priv+pub) key (PEM format)
    @type ssl_trustfile: str
    @param ssl_trustfile: file trusted certificates to validate the server
    @type ssl_skip_hostname_check: bool
    @param ssl_skip_hostname_check: disable verification of hostname in
    certificate. Use with caution - disabling hostname checking leaves you
    vulnerable to Man-in-the-Middle attacks.

    @rtype: Connection
    @return: a disconnected Connection
    """
    if url is None:
      url = options.get("host")
    if isinstance(url, basestring):
      url = URL(url)
    self.host = url.host
    if options.has_key("transport"):
      self.transport = options.get("transport")
    elif url.scheme == url.AMQP:
      self.transport = "tcp"
    elif url.scheme == url.AMQPS:
      self.transport = "ssl"
    else:
      self.transport = "tcp"
    if self.transport in ("ssl", "tcp+tls"):
      self.port = default(url.port, options.get("port", AMQPS_PORT))
    else:
      self.port = default(url.port, options.get("port", AMQP_PORT))
    self.heartbeat = options.get("heartbeat")
    self.username = default(url.user, options.get("username", None))
    self.password = default(url.password, options.get("password", None))
    self.auth_username = None

    self.sasl_mechanisms = options.get("sasl_mechanisms")
    self.sasl_service = options.get("sasl_service", "qpidd")
    self.sasl_min_ssf = options.get("sasl_min_ssf")
    self.sasl_max_ssf = options.get("sasl_max_ssf")

    self.reconnect = options.get("reconnect", False)
    self.reconnect_timeout = options.get("reconnect_timeout")
    reconnect_interval = options.get("reconnect_interval")
    self.reconnect_interval_min = options.get("reconnect_interval_min",
                                              default(reconnect_interval, 1))
    self.reconnect_interval_max = options.get("reconnect_interval_max",
                                              default(reconnect_interval, 2*60))
    self.reconnect_limit = options.get("reconnect_limit")
    self.reconnect_urls = options.get("reconnect_urls", [])
    self.reconnect_log = options.get("reconnect_log", True)

    self.address_ttl = options.get("address_ttl", 60)
    self.tcp_nodelay = options.get("tcp_nodelay", False)

    self.ssl_keyfile = options.get("ssl_keyfile", None)
    self.ssl_certfile = options.get("ssl_certfile", None)
    self.ssl_trustfile = options.get("ssl_trustfile", None)
    self.ssl_skip_hostname_check = options.get("ssl_skip_hostname_check", False)
    self.client_properties = options.get("client_properties", {})

    self.options = options


    self.id = str(uuid4())
    self.session_counter = 0
    self.sessions = {}
    self._open = False
    self._connected = False
    self._transport_connected = False
    self._lock = RLock()
    self._condition = Condition(self._lock)
    self._waiter = Waiter(self._condition)
    self._modcount = Serial(0)
    self.error = None
    from driver import Driver
    self._driver = Driver(self)

  def _wait(self, predicate, timeout=None):
    return self._waiter.wait(predicate, timeout=timeout)

  def _wakeup(self):
    self._modcount += 1
    self._driver.wakeup()

  def check_error(self):
    if self.error:
      self._condition.gc()
      e = self.error
      if isinstance(e, ContentError):
          """ forget the content error. It will be
          raised this time but won't block future calls
          """
          self.error = None
      raise e

  def get_error(self):
    return self.error

  def _ewait(self, predicate, timeout=None):
    result = self._wait(lambda: self.error or predicate(), timeout)
    self.check_error()
    return result

  def check_closed(self):
    if not self._connected:
      self._condition.gc()
      raise ConnectionClosed()

  @synchronized
  def session(self, name=None, transactional=False):
    """
    Creates or retrieves the named session. If the name is omitted or
    None, then a unique name is chosen based on a randomly generated
    uuid.

    @type name: str
    @param name: the session name
    @rtype: Session
    @return: the named Session
    """

    if name is None:
      name = "%s:%s" % (self.id, self.session_counter)
      self.session_counter += 1
    else:
      name = "%s:%s" % (self.id, name)

    if self.sessions.has_key(name):
      return self.sessions[name]
    else:
      ssn = Session(self, name, transactional)
      self.sessions[name] = ssn
      self._wakeup()
      return ssn

  @synchronized
  def _remove_session(self, ssn):
    self.sessions.pop(ssn.name, 0)

  @synchronized
  def open(self, timeout=None):
    """
    Opens a connection.
    """
    if self._open:
      raise ConnectionError("already open")
    self._open = True
    if self.reconnect and self.reconnect_timeout > 0:
        timeout = self.reconnect_timeout
    self.attach(timeout=timeout)

  @synchronized
  def opened(self):
    """
    Return true if the connection is open, false otherwise.
    """
    return self._open

  @synchronized
  def attach(self, timeout=None):
    """
    Attach to the remote endpoint.
    """
    if not self._connected:
      self._connected = True
      self._driver.start()
      self._wakeup()
    if not self._ewait(lambda: self._transport_connected and not self._unlinked(), timeout=timeout):
      self.reconnect = False
      raise Timeout("Connection attach timed out")

  def _unlinked(self):
    return [l
            for ssn in self.sessions.values()
            if not (ssn.error or ssn.closed)
            for l in ssn.senders + ssn.receivers
            if not (l.linked or l.error or l.closed)]

  @synchronized
  def detach(self, timeout=None):
    """
    Detach from the remote endpoint.
    """
    if self._connected:
      self._connected = False
      self._wakeup()
      cleanup = True
    else:
      cleanup = False
    try:
      if not self._wait(lambda: not self._transport_connected, timeout=timeout):
        raise Timeout("detach timed out")
    finally:
      if cleanup:
        self._driver.stop()
      self._condition.gc()

  @synchronized
  def attached(self):
    """
    Return true if the connection is attached, false otherwise.
    """
    return self._connected

  @synchronized
  def close(self, timeout=None):
    """
    Close the connection and all sessions.
    """
    try:
      for ssn in self.sessions.values():
        ssn.close(timeout=timeout)
    finally:
      self.detach(timeout=timeout)
      self._open = False

class Session(Endpoint):

  """
  Sessions provide a linear context for sending and receiving
  L{Messages<Message>}. L{Messages<Message>} are sent and received
  using the L{Sender.send} and L{Receiver.fetch} methods of the
  L{Sender} and L{Receiver} objects associated with a Session.

  Each L{Sender} and L{Receiver} is created by supplying either a
  target or source address to the L{sender} and L{receiver} methods of
  the Session. The address is supplied via a string syntax documented
  below.

  Addresses
  =========

  An address identifies a source or target for messages. In its
  simplest form this is just a name. In general a target address may
  also be used as a source address, however not all source addresses
  may be used as a target, e.g. a source might additionally have some
  filtering criteria that would not be present in a target.

  A subject may optionally be specified along with the name. When an
  address is used as a target, any subject specified in the address is
  used as the default subject of outgoing messages for that target.
  When an address is used as a source, any subject specified in the
  address is pattern matched against the subject of available messages
  as a filter for incoming messages from that source.

  The options map contains additional information about the address
  including:

    - policies for automatically creating, and deleting the node to
      which an address refers

    - policies for asserting facts about the node to which an address
      refers

    - extension points that can be used for sender/receiver
      configuration

  Mapping to AMQP 0-10
  --------------------
  The name is resolved to either an exchange or a queue by querying
  the broker.

  The subject is set as a property on the message. Additionally, if
  the name refers to an exchange, the routing key is set to the
  subject.

  Syntax
  ------
  The following regular expressions define the tokens used to parse
  addresses::
    LBRACE: \\{
    RBRACE: \\}
    LBRACK: \\[
    RBRACK: \\]
    COLON:  :
    SEMI:   ;
    SLASH:  /
    COMMA:  ,
    NUMBER: [+-]?[0-9]*\\.?[0-9]+
    ID:     [a-zA-Z_](?:[a-zA-Z0-9_-]*[a-zA-Z0-9_])?
    STRING: "(?:[^\\\\"]|\\\\.)*"|\'(?:[^\\\\\']|\\\\.)*\'
    ESC:    \\\\[^ux]|\\\\x[0-9a-fA-F][0-9a-fA-F]|\\\\u[0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F]
    SYM:    [.#*%@$^!+-]
    WSPACE: [ \\n\\r\\t]+

  The formal grammar for addresses is given below::
    address = name [ "/" subject ] [ ";" options ]
       name = ( part | quoted )+
    subject = ( part | quoted | "/" )*
     quoted = STRING / ESC
       part = LBRACE / RBRACE / COLON / COMMA / NUMBER / ID / SYM
    options = map
        map = "{" ( keyval ( "," keyval )* )? "}"
     keyval = ID ":" value
      value = NUMBER / STRING / ID / map / list
       list = "[" ( value ( "," value )* )? "]"

  This grammar resuls in the following informal syntax::

    <name> [ / <subject> ] [ ; <options> ]

  Where options is::

    { <key> : <value>, ... }

  And values may be:
    - numbers
    - single, double, or non quoted strings
    - maps (dictionaries)
    - lists

  Options
  -------
  The options map permits the following parameters::

    <name> [ / <subject> ] ; {
      create: always | sender | receiver | never,
      delete: always | sender | receiver | never,
      assert: always | sender | receiver | never,
      mode: browse | consume,
      node: {
        type: queue | topic,
        durable: True | False,
        x-declare: { ... <declare-overrides> ... },
        x-bindings: [<binding_1>, ... <binding_n>]
      },
      link: {
        name: <link-name>,
        durable: True | False,
        reliability: unreliable | at-most-once | at-least-once | exactly-once,
        x-declare: { ... <declare-overrides> ... },
        x-bindings: [<binding_1>, ... <binding_n>],
        x-subscribe: { ... <subscribe-overrides> ... }
      }
    }

  Bindings are specified as a map with the following options::

    {
      exchange: <exchange>,
      queue: <queue>,
      key: <key>,
      arguments: <arguments>
    }

  The create, delete, and assert policies specify who should perfom
  the associated action:

   - I{always}: the action will always be performed
   - I{sender}: the action will only be performed by the sender
   - I{receiver}: the action will only be performed by the receiver
   - I{never}: the action will never be performed (this is the default)

  The node-type is one of:

    - I{topic}: a topic node will default to the topic exchange,
      x-declare may be used to specify other exchange types
    - I{queue}: this is the default node-type

  The x-declare map permits protocol specific keys and values to be
  specified when exchanges or queues are declared. These keys and
  values are passed through when creating a node or asserting facts
  about an existing node.

  Examples
  --------
  A simple name resolves to any named node, usually a queue or a
  topic::

    my-queue-or-topic

  A simple name with a subject will also resolve to a node, but the
  presence of the subject will cause a sender using this address to
  set the subject on outgoing messages, and receivers to filter based
  on the subject::

    my-queue-or-topic/my-subject

  A subject pattern can be used and will cause filtering if used by
  the receiver. If used for a sender, the literal value gets set as
  the subject::

    my-queue-or-topic/my-*

  In all the above cases, the address is resolved to an existing node.
  If you want the node to be auto-created, then you can do the
  following. By default nonexistent nodes are assumed to be queues::

    my-queue; {create: always}

  You can customize the properties of the queue::

    my-queue; {create: always, node: {durable: True}}

  You can create a topic instead if you want::

    my-queue; {create: always, node: {type: topic}}

  You can assert that the address resolves to a node with particular
  properties::

    my-transient-topic; {
      assert: always,
      node: {
        type: topic,
        durable: False
      }
    }
 """

  def __init__(self, connection, name, transactional):
    self.connection = connection
    self.name = name
    self.log_id = "%x" % id(self)

    self.transactional = transactional

    self.committing = False
    self.committed = True
    self.aborting = False
    self.aborted = False

    self.next_sender_id = 0
    self.senders = []
    self.next_receiver_id = 0
    self.receivers = []
    self.outgoing = []
    self.incoming = []
    self.unacked = []
    self.acked = []
    # XXX: I hate this name.
    self.ack_capacity = UNLIMITED

    self.error = None
    self.closing = False
    self.closed = False

    self._lock = connection._lock

  def __repr__(self):
    return "<Session %s>" % self.name

  def _wait(self, predicate, timeout=None):
    return self.connection._wait(predicate, timeout=timeout)

  def _wakeup(self):
    self.connection._wakeup()

  def check_error(self):
    self.connection.check_error()
    if self.error:
      raise self.error

  def get_error(self):
    err = self.connection.get_error()
    if err:
      return err
    else:
      return self.error

  def _ewait(self, predicate, timeout=None):
    result = self.connection._ewait(lambda: self.error or predicate(), timeout)
    self.check_error()
    return result

  def check_closed(self):
    if self.closed:
      raise SessionClosed()

  @synchronized
  def sender(self, target, **options):
    """
    Creates a L{Sender} that may be used to send L{Messages<Message>}
    to the specified target.

    @type target: str
    @param target: the target to which messages will be sent
    @rtype: Sender
    @return: a new Sender for the specified target
    """
    target = _mangle(target)
    sender = Sender(self, self.next_sender_id, target, options)
    self.next_sender_id += 1
    self.senders.append(sender)
    if not self.closed and self.connection._connected:
      self._wakeup()
      try:
        sender._ewait(lambda: sender.linked)
      except LinkError, e:
        sender.close()
        raise e
    return sender

  @synchronized
  def receiver(self, source, **options):
    """
    Creates a receiver that may be used to fetch L{Messages<Message>}
    from the specified source.

    @type source: str
    @param source: the source of L{Messages<Message>}
    @rtype: Receiver
    @return: a new Receiver for the specified source
    """
    source = _mangle(source)
    receiver = Receiver(self, self.next_receiver_id, source, options)
    self.next_receiver_id += 1
    self.receivers.append(receiver)
    if not self.closed and self.connection._connected:
      self._wakeup()
      try:
        receiver._ewait(lambda: receiver.linked)
      except LinkError, e:
        receiver.close()
        raise e
    return receiver

  @synchronized
  def _count(self, predicate):
    result = 0
    for msg in self.incoming:
      if predicate(msg):
        result += 1
    return result

  def _peek(self, receiver):
    for msg in self.incoming:
      if msg._receiver == receiver:
        return msg

  def _pop(self, receiver):
    i = 0
    while i < len(self.incoming):
      msg = self.incoming[i]
      if msg._receiver == receiver:
        del self.incoming[i]
        return msg
      else:
        i += 1

  @synchronized
  def _get(self, receiver, timeout=None):
    if self._ewait(lambda: ((self._peek(receiver) is not None) or
                            self.closing or receiver.closed),
                   timeout):
      msg = self._pop(receiver)
      if msg is not None:
        msg._receiver.returned += 1
        self.unacked.append(msg)
        log.debug("RETR[%s]: %s", self.log_id, msg)
        return msg
    return None

  @synchronized
  def next_receiver(self, timeout=None):
    if self._ecwait(lambda: self.incoming, timeout):
      return self.incoming[0]._receiver
    else:
      raise Empty

  @synchronized
  def acknowledge(self, message=None, disposition=None, sync=True):
    """
    Acknowledge the given L{Message}. If message is None, then all
    unacknowledged messages on the session are acknowledged.

    @type message: Message
    @param message: the message to acknowledge or None
    @type sync: boolean
    @param sync: if true then block until the message(s) are acknowledged
    """
    if message is None:
      messages = self.unacked[:]
    else:
      messages = [message]

    for m in messages:
      if self.ack_capacity is not UNLIMITED:
        if self.ack_capacity <= 0:
          # XXX: this is currently a SendError, maybe it should be a SessionError?
          raise InsufficientCapacity("ack_capacity = %s" % self.ack_capacity)
        self._wakeup()
        self._ecwait(lambda: len(self.acked) < self.ack_capacity)
      m._disposition = disposition
      self.unacked.remove(m)
      self.acked.append(m)

    self._wakeup()
    if sync:
      self._ecwait(lambda: not [m for m in messages if m in self.acked])

  @synchronized
  def commit(self, timeout=None):
    """
    Commit outstanding transactional work. This consists of all
    message sends and receives since the prior commit or rollback.
    """
    if not self.transactional:
      raise NontransactionalSession()
    self.committing = True
    self._wakeup()
    self._ecwait(lambda: not self.committing, timeout=timeout)
    if self.aborted:
      raise TransactionAborted()
    assert self.committed

  @synchronized
  def rollback(self):
    """
    Rollback outstanding transactional work. This consists of all
    message sends and receives since the prior commit or rollback.
    """
    if not self.transactional:
      raise NontransactionalSession()
    self.aborting = True
    self._wakeup()
    self._ecwait(lambda: not self.aborting)
    assert self.aborted

  @synchronized
  def sync(self, timeout=None):
    """
    Sync the session.
    """
    for snd in self.senders:
      snd.sync(timeout=timeout)
    if not self._ewait(lambda: not self.outgoing and not self.acked, timeout=timeout):
      raise Timeout("session sync timed out")

  @synchronized
  def close(self, timeout=None):
    """
    Close the session.
    """
    if self.error: return
    self.sync(timeout=timeout)

    for link in self.receivers + self.senders:
      link.close(timeout=timeout)

    if not self.closing:
      self.closing = True
      self._wakeup()

    try:
      if not self._ewait(lambda: self.closed, timeout=timeout):
        raise Timeout("session close timed out")
    finally:
      self.connection._remove_session(self)

def _mangle(addr):
  if addr and addr.startswith("#"):
    return str(uuid4()) + addr
  else:
    return addr

class Sender(Endpoint):

  """
  Sends outgoing messages.
  """

  def __init__(self, session, id, target, options):
    self.session = session
    self.id = id
    self.target = target
    self.options = options
    self.capacity = options.get("capacity", UNLIMITED)
    self.threshold = 0.5
    self.durable = options.get("durable")
    self.queued = Serial(0)
    self.synced = Serial(0)
    self.acked = Serial(0)
    self.error = None
    self.linked = False
    self.closing = False
    self.closed = False
    self._lock = self.session._lock

  def _wakeup(self):
    self.session._wakeup()

  def check_error(self):
    self.session.check_error()
    if self.error:
      raise self.error

  def get_error(self):
    err = self.session.get_error()
    if err:
      return err
    else:
      return self.error

  def _ewait(self, predicate, timeout=None):
    result = self.session._ewait(lambda: self.error or predicate(), timeout)
    self.check_error()
    return result

  def check_closed(self):
    if self.closed:
      raise LinkClosed()

  @synchronized
  def unsettled(self):
    """
    Returns the number of messages awaiting acknowledgment.
    @rtype: int
    @return: the number of unacknowledged messages
    """
    return self.queued - self.acked

  @synchronized
  def available(self):
    if self.capacity is UNLIMITED:
      return UNLIMITED
    else:
      return self.capacity - self.unsettled()

  @synchronized
  def send(self, object, sync=True, timeout=None):
    """
    Send a message. If the object passed in is of type L{unicode},
    L{str}, L{list}, or L{dict}, it will automatically be wrapped in a
    L{Message} and sent. If it is of type L{Message}, it will be sent
    directly. If the sender capacity is not L{UNLIMITED} then send
    will block until there is available capacity to send the message.
    If the timeout parameter is specified, then send will throw an
    L{InsufficientCapacity} exception if capacity does not become
    available within the specified time.

    @type object: unicode, str, list, dict, Message
    @param object: the message or content to send

    @type sync: boolean
    @param sync: if true then block until the message is sent

    @type timeout: float
    @param timeout: the time to wait for available capacity
    """

    if not self.session.connection._connected or self.session.closing:
      raise Detached()

    self._ecwait(lambda: self.linked, timeout=timeout)

    if isinstance(object, Message):
      message = object
    else:
      message = Message(object)

    if message.durable is None:
      message.durable = self.durable

    if self.capacity is not UNLIMITED:
      if self.capacity <= 0:
        raise InsufficientCapacity("capacity = %s" % self.capacity)
      if not self._ecwait(self.available, timeout=timeout):
        raise InsufficientCapacity("capacity = %s" % self.capacity)

    # XXX: what if we send the same message to multiple senders?
    message._sender = self
    if self.capacity is not UNLIMITED:
      message._sync = sync or self.available() <= int(ceil(self.threshold*self.capacity))
    else:
      message._sync = sync
    self.session.outgoing.append(message)
    self.queued += 1

    if sync:
      self.sync(timeout=timeout)
      assert message not in self.session.outgoing
    else:
      self._wakeup()

  @synchronized
  def sync(self, timeout=None):
    mno = self.queued
    if self.synced < mno:
      self.synced = mno
      self._wakeup()
    try:
      if not self._ewait(lambda: self.acked >= mno, timeout=timeout):
        raise Timeout("sender sync timed out")
    except ContentError:
      # clean bad message so we can continue
      self.acked = mno
      self.session.outgoing.pop(0)
      raise

  @synchronized
  def close(self, timeout=None):
    """
    Close the Sender.
    """
    # avoid erroring out when closing a sender that was never
    # established
    if self.acked < self.queued:
      self.sync(timeout=timeout)

    if not self.closing:
      self.closing = True
      self._wakeup()

    try:
      if not self.session._ewait(lambda: self.closed, timeout=timeout):
        raise Timeout("sender close timed out")
    finally:
      try:
        self.session.senders.remove(self)
      except ValueError:
        pass

class Receiver(Endpoint, object):

  """
  Receives incoming messages from a remote source. Messages may be
  fetched with L{fetch}.
  """

  def __init__(self, session, id, source, options):
    self.session = session
    self.id = id
    self.source = source
    self.options = options

    self.granted = Serial(0)
    self.draining = False
    self.impending = Serial(0)
    self.received = Serial(0)
    self.returned = Serial(0)

    self.error = None
    self.linked = False
    self.closing = False
    self.closed = False
    self._lock = self.session._lock
    self._capacity = 0
    self._set_capacity(options.get("capacity", 0), False)
    self.threshold = 0.5

  @synchronized
  def _set_capacity(self, c, wakeup=True):
    if c is UNLIMITED:
      self._capacity = c.value
    else:
      self._capacity = c
    self._grant()
    if wakeup:
      self._wakeup()

  def _get_capacity(self):
    if self._capacity == UNLIMITED.value:
      return UNLIMITED
    else:
      return self._capacity

  capacity = property(_get_capacity, _set_capacity)

  def _wakeup(self):
    self.session._wakeup()

  def check_error(self):
    self.session.check_error()
    if self.error:
      raise self.error

  def get_error(self):
    err = self.session.get_error()
    if err:
      return err
    else:
      return self.error

  def _ewait(self, predicate, timeout=None):
    result = self.session._ewait(lambda: self.error or predicate(), timeout)
    self.check_error()
    return result

  def check_closed(self):
    if self.closed:
      raise LinkClosed()

  @synchronized
  def unsettled(self):
    """
    Returns the number of acknowledged messages awaiting confirmation.
    """
    return len([m for m in self.session.acked if m._receiver is self])

  @synchronized
  def available(self):
    """
    Returns the number of messages available to be fetched by the
    application.

    @rtype: int
    @return: the number of available messages
    """
    return self.received - self.returned

  @synchronized
  def fetch(self, timeout=None):
    """
    Fetch and return a single message. A timeout of None will block
    forever waiting for a message to arrive, a timeout of zero will
    return immediately if no messages are available.

    @type timeout: float
    @param timeout: the time to wait for a message to be available
    """

    self._ecwait(lambda: self.linked)

    if self._capacity == 0:
      self.granted = self.returned + 1
      self._wakeup()
    self._ecwait(lambda: self.impending >= self.granted)
    msg = self.session._get(self, timeout=timeout)
    if msg is None:
      self.check_closed()
      self.draining = True
      self._wakeup()
      self._ecwait(lambda: not self.draining)
      msg = self.session._get(self, timeout=0)
      self._grant()
      self._wakeup()
      if msg is None:
        raise Empty()
    elif self._capacity not in (0, UNLIMITED.value):
      t = int(ceil(self.threshold * self._capacity))
      if self.received - self.returned <= t:
        self.granted = self.returned + self._capacity
        self._wakeup()
    return msg

  def _grant(self):
    if self._capacity == UNLIMITED.value:
      self.granted = UNLIMITED
    else:
      self.granted = self.returned + self._capacity

  @synchronized
  def close(self, timeout=None):
    """
    Close the receiver.
    """
    if not self.closing:
      self.closing = True
      self._wakeup()

    try:
      if not self.session._ewait(lambda: self.closed, timeout=timeout):
        raise Timeout("receiver close timed out")
    finally:
      try:
        self.session.receivers.remove(self)
      except ValueError:
        pass

__all__ = ["Connection", "Session", "Sender", "Receiver"]
