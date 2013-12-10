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

class Timeout(Exception):
  pass

## Messaging Errors

class MessagingError(Exception):

  def __init__(self, code=None, text=None, **info):
    self.code = code
    self.text = text
    self.info = info
    if self.code is None:
      msg = self.text
    else:
      msg = "%s(%s)" % (self.text, self.code)
    if info:
      msg += " " + ", ".join(["%s=%r" % (k, v) for k, v in self.info.items()])
    Exception.__init__(self, msg)

class InternalError(MessagingError):
  pass

## Connection Errors

class ConnectionError(MessagingError):
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

class VersionError(ConnectError):
  pass

class AuthenticationFailure(ConnectError):
  pass

class ConnectionClosed(ConnectionError):
  pass

class HeartbeatTimeout(ConnectionError):
  pass

## Session Errors

class SessionError(MessagingError):
  pass

class Detached(SessionError):
  """
  Exception raised when an operation is attempted that is illegal when
  detached.
  """
  pass

class NontransactionalSession(SessionError):
  """
  Exception raised when commit or rollback is attempted on a non
  transactional session.
  """
  pass

class TransactionError(SessionError):
  pass

class TransactionAborted(TransactionError):
  pass

class UnauthorizedAccess(SessionError):
  pass

class ServerError(SessionError):
  pass

class SessionClosed(SessionError):
  pass

## Link Errors

class LinkError(MessagingError):
  pass

class InsufficientCapacity(LinkError):
  pass

class AddressError(LinkError):
  pass

class MalformedAddress(AddressError):
  pass

class InvalidOption(AddressError):
  pass

class ResolutionError(AddressError):
  pass

class AssertionFailed(ResolutionError):
  pass

class NotFound(ResolutionError):
  pass

class LinkClosed(LinkError):
  pass

## Sender Errors

class SenderError(LinkError):
  pass

class SendError(SenderError):
  pass

class TargetCapacityExceeded(SendError):
  pass

## Receiver Errors

class ReceiverError(LinkError):
  pass

class FetchError(ReceiverError):
  pass

class Empty(FetchError):
  """
  Exception raised by L{Receiver.fetch} when there is no message
  available within the alloted time.
  """
  pass

## Message Content errors
class ContentError(MessagingError):
  """
  This type of exception will be returned to the application
  once, and will not block further requests
  """
  pass

class EncodeError(ContentError):
  pass

class DecodeError(ContentError):
  pass
