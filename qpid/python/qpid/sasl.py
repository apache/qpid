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

class SASLError(Exception):
  pass

class WrapperClient:

  def __init__(self):
    self._cli = _Client()

  def setAttr(self, name, value):
    status = self._cli.setAttr(str(name), str(value))
    if status and name == 'username':
      status = self._cli.setAttr('externaluser', str(value))
      
    if not status:
      raise SASLError(self._cli.getError())

  def init(self):
    status = self._cli.init()
    if not status:
      raise SASLError(self._cli.getError())

  def start(self, mechanisms):
    status, mech, initial = self._cli.start(str(mechanisms))
    if status:
      return mech, initial
    else:
      raise SASLError(self._cli.getError())

  def step(self, challenge):
    status, response = self._cli.step(challenge)
    if status:
      return response
    else:
      raise SASLError(self._cli.getError())

  def encode(self, bytes):
    status, result = self._cli.encode(bytes)
    if status:
      return result
    else:
      raise SASLError(self._cli.getError())

  def decode(self, bytes):
    status, result = self._cli.decode(bytes)
    if status:
      return result
    else:
      raise SASLError(self._cli.getError())

  def auth_username(self):
    status, result = self._cli.getUserId()
    if status:
      return result
    else:
      raise SASLError(self._cli.getError())

class PlainClient:

  def __init__(self):
    self.attrs = {}

  def setAttr(self, name, value):
    self.attrs[name] = value

  def init(self):
    pass

  def start(self, mechanisms):
    mechs = mechanisms.split()
    if self.attrs.get("username") and self.attrs.get("password") and "PLAIN" in mechs:
      return "PLAIN", "\0%s\0%s" % (self.attrs.get("username"), self.attrs.get("password"))
    elif "ANONYMOUS" in mechs:
      return "ANONYMOUS", "%s@%s" % (self.attrs.get("username"), socket.gethostname())
    elif "EXTERNAL" in mechs:
      return "EXTERNAL", "%s" % (self.attrs.get("username"))
    else:
      raise SASLError("sasl negotiation failed: no mechanism agreed")

  def step(self, challenge):
    pass

  def encode(self, bytes):
    return bytes

  def decode(self, bytes):
    return bytes

  def auth_username(self):
    return self.attrs.get("username")

try:
  from saslwrapper import Client as _Client
  Client = WrapperClient
except ImportError:
  Client = PlainClient
