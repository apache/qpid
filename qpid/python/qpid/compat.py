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

import sys
import errno
import time
from logging import getLogger
log = getLogger("qpid.messaging")

try:
  set = set
except NameError:
  from sets import Set as set

try:
  from socket import SHUT_RDWR
except ImportError:
  SHUT_RDWR = 2

try:
  from traceback import format_exc
except ImportError:
  import traceback
  def format_exc():
    return "".join(traceback.format_exception(*sys.exc_info()))

# QPID-5588: prefer poll() to select(), as it allows file descriptors with
# values > FD_SETSIZE
import select as _select_mod
try:
  # QPID-5790: unless eventlet/greenthreads have monkey-patched the select
  # module, as to date poll() is not properly supported by eventlet
  import eventlet
  _is_patched = eventlet.patcher.is_monkey_patched("select")
except ImportError:
  _is_patched = False

if hasattr(_select_mod, "poll") and not _is_patched:
  from select import error as SelectError
  def select(rlist, wlist, xlist, timeout=None):
    fd_count = 0
    rset = set(rlist)
    wset = set(wlist)
    xset = set(xlist)
    if timeout:
      # select expects seconds, poll milliseconds
      timeout = float(timeout) * 1000
    poller = _select_mod.poll()

    rwset = rset.intersection(wset)
    for rw in rwset:
      poller.register(rw, (_select_mod.POLLIN | _select_mod.POLLOUT))
      fd_count += 1
    for ro in rset.difference(rwset):
      poller.register(ro, _select_mod.POLLIN)
      fd_count += 1
    for wo in wset.difference(rwset):
      poller.register(wo, _select_mod.POLLOUT)
      fd_count += 1
    for x in xset:
      poller.register(x, _select_mod.POLLPRI)
      fd_count += 1

    # select returns the objects passed in, but poll gives us back only the
    # integer fds.  Maintain a map to get back:
    fd_map = {}
    for o in rset | wset | xset:
      if hasattr(o, "fileno"):
        fd_map[o.fileno()] = o

    log.debug("poll(%d fds, timeout=%s)", fd_count, timeout)
    active = poller.poll(timeout)
    log.debug("poll() returned %s fds", len(active))

    rfds = []
    wfds = []
    xfds = []
    # set the error conditions so we do a read(), which will report the error
    rflags = (_select_mod.POLLIN | _select_mod.POLLERR | _select_mod.POLLHUP)
    for fds, flags in active:
      if fds in fd_map:
        fds = fd_map[fds]
      if (flags & rflags):
        rfds.append(fds)
      if (flags & _select_mod.POLLOUT):
        wfds.append(fds)
      if (flags & _select_mod.POLLPRI):
        xfds.append(fds)
    return (rfds, wfds, xfds)
else:
  if tuple(sys.version_info[0:2]) < (2, 4):
    from select import error as SelectError
    from select import select as old_select
    def select(rlist, wlist, xlist, timeout=None):
      return old_select(list(rlist), list(wlist), list(xlist), timeout)
  else:
    from select import select
    from select import error as SelectError

class BaseWaiter:

  def wakeup(self):
    self._do_write()

  def wait(self, timeout=None):
    start = time.time()
    if timeout is not None:
      ready = False
      while timeout > 0:
        try:
          ready, _, _ = select([self], [], [], timeout)
          break
        except SelectError, e:
          if e[0] == errno.EINTR:
            elapsed = time.time() - start
            timeout = timeout - elapsed
          else:
            raise e
    else:
      ready = True

    if ready:
      self._do_read()
      return True
    else:
      return False

  def reading(self):
    return True

  def readable(self):
    self._do_read()

if sys.platform in ('win32', 'cygwin'):
  import socket

  class SockWaiter(BaseWaiter):

    def __init__(self, read_sock, write_sock):
      self.read_sock = read_sock
      self.write_sock = write_sock

    def _do_write(self):
      self.write_sock.send("\0")

    def _do_read(self):
      self.read_sock.recv(65536)

    def fileno(self):
      return self.read_sock.fileno()

    def close(self):
      if self.write_sock is not None:
        self.write_sock.close()
        self.write_sock = None
        self.read_sock.close()
        self.read_sock = None

    def __del__(self):
      self.close()

  def __repr__(self):
    return "SockWaiter(%r, %r)" % (self.read_sock, self.write_sock)

  def selectable_waiter():
    listener = socket.socket()
    listener.bind(('', 0))
    listener.listen(1)
    _, port = listener.getsockname()
    write_sock = socket.socket()
    write_sock.connect(("127.0.0.1", port))
    read_sock, _ = listener.accept()
    listener.close()
    return SockWaiter(read_sock, write_sock)
else:
  import os

  class PipeWaiter(BaseWaiter):

    def __init__(self):
      self.read_fd, self.write_fd = os.pipe()

    def _do_write(self):
      os.write(self.write_fd, "\0")

    def _do_read(self):
      os.read(self.read_fd, 65536)

    def fileno(self):
      return self.read_fd

    def close(self):
      if self.write_fd is not None:
        os.close(self.write_fd)
        self.write_fd = None
        os.close(self.read_fd)
        self.read_fd = None

    def __del__(self):
      self.close()

    def __repr__(self):
      return "PipeWaiter(%r, %r)" % (self.read_fd, self.write_fd)

  def selectable_waiter():
    return PipeWaiter()
