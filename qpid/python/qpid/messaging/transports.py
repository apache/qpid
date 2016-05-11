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
      SSL_ERROR_WANT_WRITE, CERT_REQUIRED, CERT_NONE
except ImportError:

  ## try the older python SSL api:
  from socket import ssl

  class old_ssl(SocketTransport):
    def __init__(self, conn, host, port):
      SocketTransport.__init__(self, conn, host, port)
      # Bug (QPID-4337): this is the "old" version of python SSL.
      # The private key is required. If a certificate is given, but no
      # keyfile, assume the key is contained in the certificate
      ssl_keyfile = conn.ssl_keyfile
      ssl_certfile = conn.ssl_certfile
      if ssl_certfile and not ssl_keyfile:
        ssl_keyfile = ssl_certfile

      # this version of SSL does NOT perform certificate validation.  If the
      # connection has been configured with CA certs (via ssl_trustfile), then
      # the application expects the certificate to be validated against the
      # supplied CA certs. Since this version cannot validate, the peer cannot
      # be trusted.
      if conn.ssl_trustfile:
        raise socket.error("This version of Python does not support verification of the peer's certificate.")

      self.ssl = ssl(self.socket, keyfile=ssl_keyfile, certfile=ssl_certfile)
      self.socket.setblocking(1)

    def reading(self, reading):
      return reading

    def writing(self, writing):
      return writing

    def recv(self, n):
      return self.ssl.read(n)

    def send(self, s):
      return self.ssl.write(s)

    def close(self):
      self.socket.close()

  TRANSPORTS["ssl"] = old_ssl
  TRANSPORTS["tcp+tls"] = old_ssl
    
else:
  class tls(SocketTransport):

    def __init__(self, conn, host, port):
      SocketTransport.__init__(self, conn, host, port)
      if conn.ssl_trustfile:
        validate = CERT_REQUIRED
      else:
        validate = CERT_NONE

      # if user manually set flag to false then require cert
      actual = getattr(conn, "_ssl_skip_hostname_check_actual", None)
      if actual is not None and conn.ssl_skip_hostname_check is False:
        validate = CERT_REQUIRED

      self.tls = wrap_socket(self.socket, keyfile=conn.ssl_keyfile,
                             certfile=conn.ssl_certfile,
                             ca_certs=conn.ssl_trustfile,
                             cert_reqs=validate)

      if validate == CERT_REQUIRED and not conn.ssl_skip_hostname_check:
        verify_hostname(self.tls.getpeercert(), host)

      self.socket.setblocking(0)
      self.state = None
      # See qpid-4872: need to store the parameters last passed to tls.write()
      # in case the calls fail with an SSL_ERROR_WANT_* error and we have to
      # retry the call with the same parameters.
      self.write_retry = None   # buffer passed to last call of tls.write()

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
      if self.write_retry is None:
        self.write_retry = bytes
      self._clear_state()
      try:
        n = self.tls.write( self.write_retry )
        self.write_retry = None
        return n
      except SSLError, e:
        if self._update_state(e.args[0]):
          # will retry on next invokation
          return 0
        self.write_retry = None
        raise
      except:
        self.write_retry = None
        raise

    def recv(self, n):
      self._clear_state()
      try:
        return self.tls.read(n)
      except SSLError, e:
        if self._update_state(e.args[0]):
          # will retry later:
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

  def verify_hostname(peer_certificate, hostname):
    match_found = False
    peer_names = []
    if peer_certificate:
      if 'subjectAltName' in peer_certificate:
        for san in peer_certificate['subjectAltName']:
          if san[0] == 'DNS':
            peer_names.append(san[1].lower())
      if 'subject' in peer_certificate:
        for sub in peer_certificate['subject']:
          while isinstance(sub, tuple) and isinstance(sub[0], tuple):
            sub = sub[0]  # why the extra level of indirection???
          if sub[0] == 'commonName':
            peer_names.append(sub[1].lower())
      for pattern in peer_names:
        if _match_dns_pattern(hostname.lower(), pattern):
          match_found = True
          break
    if not match_found:
      raise SSLError("Connection hostname '%s' does not match names from peer certificate: %s" % (hostname, peer_names))

  def _match_dns_pattern( hostname, pattern ):
    """ For checking the hostnames provided by the peer's certificate
    """
    if pattern.find("*") == -1:
      return hostname == pattern

    # DNS wildcarded pattern - see RFC2818
    h_labels = hostname.split(".")
    p_labels = pattern.split(".")

    while h_labels and p_labels:
      if p_labels[0].find("*") == -1:
        if p_labels[0] != h_labels[0]:
          return False
      else:
        p = p_labels[0].split("*")
        if not h_labels[0].startswith(p[0]):
          return False
        if not h_labels[0].endswith(p[1]):
          return False
      h_labels.pop(0)
      p_labels.pop(0)

    return not h_labels and not p_labels


  TRANSPORTS["ssl"] = tls
  TRANSPORTS["tcp+tls"] = tls
