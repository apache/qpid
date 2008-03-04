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

import os, socket

def connect(host, port):
  sock = socket.socket()
  sock.connect((host, port))
  sock.setblocking(1)
  # XXX: we could use this on read, but we'd have to put write in a
  # loop as well
  # sock.settimeout(1)
  return sock

def listen(host, port, predicate = lambda: True, bound = lambda: None):
  sock = socket.socket()
  sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
  sock.bind((host, port))
  bound()
  sock.listen(5)
  while predicate():
    s, a = sock.accept()
    yield s

def mtime(filename):
  return os.stat(filename).st_mtime
