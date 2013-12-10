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

import os, socket, time, textwrap, re, sys

try:
  from ssl import wrap_socket as ssl
except ImportError:
  from socket import ssl as wrap_socket
  class ssl:
    def __init__(self, sock, keyfile=None, certfile=None, trustfile=None):
      # Bug (QPID-4337): this is the "old" version of python SSL.
      # The private key is required. If a certificate is given, but no
      # keyfile, assume the key is contained in the certificate
      if certfile and not keyfile:
        keyfile = certfile
      self.sock = sock
      self.ssl = wrap_socket(sock, keyfile=keyfile, certfile=certfile)

    def recv(self, n):
      return self.ssl.read(n)

    def send(self, s):
      return self.ssl.write(s)

    def close(self):
      self.sock.close()

def get_client_properties_with_defaults(provided_client_properties={}):
  ppid = 0
  try:
    ppid = os.getppid()
  except:
    pass

  client_properties = {"product": "qpid python client",
                       "version": "development",
                       "platform": os.name,
                       "qpid.client_process": os.path.basename(sys.argv[0]),
                       "qpid.client_pid": os.getpid(),
                       "qpid.client_ppid": ppid}

  if provided_client_properties:
    client_properties.update(provided_client_properties)
  return client_properties

def connect(host, port):
  for res in socket.getaddrinfo(host, port, 0, socket.SOCK_STREAM):
    af, socktype, proto, canonname, sa = res
    sock = socket.socket(af, socktype, proto)
    try:
      sock.connect(sa)
      break
    except socket.error, msg:
      sock.close()
  else:
    # If we got here then we couldn't connect (yet)
    raise
  return sock

def listen(host, port, predicate = lambda: True, bound = lambda: None):
  sock = socket.socket()
  sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
  sock.bind((host, port))
  sock.listen(5)
  bound()
  while predicate():
    s, a = sock.accept()
    yield s

def mtime(filename):
  return os.stat(filename).st_mtime

def wait(condition, predicate, timeout=None):
  condition.acquire()
  try:
    passed = 0
    start = time.time()
    while not predicate():
      if timeout is None:
        # using the timed wait prevents keyboard interrupts from being
        # blocked while waiting
        condition.wait(3)
      elif passed < timeout:
        condition.wait(timeout - passed)
      else:
        return False
      passed = time.time() - start
    return True
  finally:
    condition.release()

def notify(condition, action=lambda: None):
  condition.acquire()
  try:
    action()
    condition.notifyAll()
  finally:
    condition.release()

def fill(text, indent, heading = None):
  sub = indent * " "
  if heading:
    if not text:
      return (indent - 2) * " " + heading
    init = (indent - 2) * " " + heading + " -- "
  else:
    init = sub
  w = textwrap.TextWrapper(initial_indent = init, subsequent_indent = sub)
  return w.fill(" ".join(text.split()))

class URL:

  RE = re.compile(r"""
        # [   <scheme>://  ] [    <user>   [   / <password>   ] @]    ( <host4>     | \[    <host6>    \] )  [   :<port>   ]
        ^ (?: ([^:/@]+)://)? (?: ([^:/@]+) (?: / ([^:/@]+)   )? @)? (?: ([^@:/\[]+) | \[ ([a-f0-9:.]+) \] ) (?: :([0-9]+))?$
""", re.X | re.I)

  AMQPS = "amqps"
  AMQP = "amqp"

  def __init__(self, s=None, **kwargs):
    if s is None:
      self.scheme = kwargs.get('scheme', None)
      self.user = kwargs.get('user', None)
      self.password = kwargs.get('password', None)
      self.host = kwargs.get('host', None)
      self.port = kwargs.get('port', None)
      if self.host is None:
        raise ValueError('Host required for url')
    elif isinstance(s, URL):
      self.scheme = s.scheme
      self.user = s.user
      self.password = s.password
      self.host = s.host
      self.port = s.port
    else:
      match = URL.RE.match(s)
      if match is None:
        raise ValueError(s)
      self.scheme, self.user, self.password, host4, host6, port = match.groups()
      self.host = host4 or host6
      if port is None:
        self.port = None
      else:
        self.port = int(port)

  def __repr__(self):
    return "URL(%r)" % str(self)

  def __str__(self):
    s = ""
    if self.scheme:
      s += "%s://" % self.scheme
    if self.user:
      s += self.user
      if self.password:
        s += "/%s" % self.password
      s += "@"
    if ':' not in self.host:
      s += self.host
    else:
      s += "[%s]" % self.host
    if self.port:
      s += ":%s" % self.port
    return s

  def __eq__(self, url):
    if isinstance(url, basestring):
      url = URL(url)
    return \
      self.scheme==url.scheme and \
      self.user==url.user and self.password==url.password and \
      self.host==url.host and self.port==url.port

  def __ne__(self, url):
    return not self.__eq__(url)

def default(value, default):
  if value is None:
    return default
  else:
    return value
