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

import socket
from qpid.util import connect

TRANSPORTS = {}

class SocketTransport:

  def __init__(self, conn, host, port):
    self.socket = connect(host, port)
    if conn.tcp_nodelay:
      self.socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)

  def fileno(self):
    return self.socket.fileno()

class tcp(SocketTransport):

  def reading(self, reading):
    return reading

  def writing(self, writing):
    return writing

  def send(self, bytes):
    return self.socket.send(bytes)

  def recv(self, n):
    return self.socket.recv(n)

  def close(self):
    self.socket.close()

TRANSPORTS["tcp"] = tcp

try:
  from ssl import wrap_socket, SSLError, SSL_ERROR_WANT_READ, \
      SSL_ERROR_WANT_WRITE
except ImportError:
  pass
else:
  class tls(SocketTransport):

    def __init__(self, conn, host, port):
      SocketTransport.__init__(self, conn, host, port)
      self.tls = wrap_socket(self.socket)
      self.socket.setblocking(0)
      self.state = None

    def reading(self, reading):
      if self.state is None:
        return reading
      else:
        return self.state == SSL_ERROR_WANT_READ

    def writing(self, writing):
      if self.state is None:
        return writing
      else:
        return self.state == SSL_ERROR_WANT_WRITE

    def send(self, bytes):
      self._clear_state()
      try:
        return self.tls.write(bytes)
      except SSLError, e:
        if self._update_state(e.args[0]):
          return 0
        else:
          raise

    def recv(self, n):
      self._clear_state()
      try:
        return self.tls.read(n)
      except SSLError, e:
        if self._update_state(e.args[0]):
          return None
        else:
          raise

    def _clear_state(self):
      self.state = None

    def _update_state(self, code):
      if code in (SSL_ERROR_WANT_READ, SSL_ERROR_WANT_WRITE):
        self.state = code
        return True
      else:
        return False

    def close(self):
      self.socket.setblocking(1)
      # this closes the underlying socket
      self.tls.close()

  TRANSPORTS["ssl"] = tls
  TRANSPORTS["tcp+tls"] = tls
