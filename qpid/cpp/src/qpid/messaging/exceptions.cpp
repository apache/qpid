/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
#include "qpid/messaging/exceptions.h"

namespace qpid {
namespace messaging {

MessagingException::MessagingException(const std::string& msg) : qpid::types::Exception(msg) {}
MessagingException::~MessagingException() throw() {}

InvalidOptionString::InvalidOptionString(const std::string& msg) : MessagingException(msg) {}
KeyError::KeyError(const std::string& msg) : MessagingException(msg) {}


LinkError::LinkError(const std::string& msg) : MessagingException(msg) {}

AddressError::AddressError(const std::string& msg) : LinkError(msg) {}
ResolutionError::ResolutionError(const std::string& msg) : AddressError(msg) {}
MalformedAddress::MalformedAddress(const std::string& msg) : AddressError(msg) {}
AssertionFailed::AssertionFailed(const std::string& msg) : ResolutionError(msg) {}
NotFound::NotFound(const std::string& msg) : ResolutionError(msg) {}

ReceiverError::ReceiverError(const std::string& msg) : LinkError(msg) {}
FetchError::FetchError(const std::string& msg) : ReceiverError(msg) {}
NoMessageAvailable::NoMessageAvailable() : FetchError("No message to fetch") {}

SenderError::SenderError(const std::string& msg) : LinkError(msg) {}
SendError::SendError(const std::string& msg) : SenderError(msg) {}
MessageRejected::MessageRejected(const std::string& msg) : SendError(msg) {}
TargetCapacityExceeded::TargetCapacityExceeded(const std::string& msg) : SendError(msg) {}
OutOfCapacity::OutOfCapacity(const std::string& msg) : SendError(msg) {}

SessionError::SessionError(const std::string& msg) : MessagingException(msg) {}
SessionClosed::SessionClosed() : SessionError("Session Closed") {}

TransactionError::TransactionError(const std::string& msg) : SessionError(msg) {}
TransactionAborted::TransactionAborted(const std::string& msg) : TransactionError(msg) {}
TransactionUnknown::TransactionUnknown(const std::string& msg) : TransactionError(msg) {}
UnauthorizedAccess::UnauthorizedAccess(const std::string& msg) : SessionError(msg) {}

ConnectionError::ConnectionError(const std::string& msg) : MessagingException(msg) {}
ProtocolVersionError::ProtocolVersionError(const std::string& msg) : ConnectionError(msg) {}
AuthenticationFailure::AuthenticationFailure(const std::string& msg) : ConnectionError(msg) {}

TransportFailure::TransportFailure(const std::string& msg) : MessagingException(msg) {}

}} // namespace qpid::messaging
