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

class ConnectionError(Exception):
  """
  The base class for all connection related exceptions.
  """
  pass

class ConnectError(ConnectionError):
  """
  Exception raised when there is an error connecting to the remote
  peer.
  """
  pass

class SessionError(Exception):
  pass

class Disconnected(SessionError):
  """
  Exception raised when an operation is attempted that is illegal when
  disconnected.
  """
  pass

class NontransactionalSession(SessionError):
  """
  Exception raised when commit or rollback is attempted on a non
  transactional session.
  """
  pass

class TransactionAborted(SessionError):
  pass

class SendError(SessionError):
  pass

class InsufficientCapacity(SendError):
  pass

class ReceiveError(SessionError):
  pass

class Empty(ReceiveError):
  """
  Exception raised by L{Receiver.fetch} when there is no message
  available within the alloted time.
  """
  pass
