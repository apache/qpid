#ifndef QPID_MESSAGING_EXCEPTIONS_H
#define QPID_MESSAGING_EXCEPTIONS_H

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

#include "qpid/types/Exception.h"
#include "qpid/types/Variant.h"
#include "qpid/messaging/ImportExport.h"

namespace qpid {
namespace messaging {

struct MessagingException : public qpid::types::Exception 
{
    QPID_CLIENT_EXTERN MessagingException(const std::string& msg);
    QPID_CLIENT_EXTERN virtual ~MessagingException() throw();
    
    qpid::types::Variant::Map detail;
    //TODO: override what() to include detail if present
};

struct InvalidOptionString : public MessagingException 
{
    QPID_CLIENT_EXTERN InvalidOptionString(const std::string& msg);
};

struct KeyError : MessagingException
{
    QPID_CLIENT_EXTERN KeyError(const std::string&);
};

struct LinkError : MessagingException
{
    QPID_CLIENT_EXTERN LinkError(const std::string&);
};

struct AddressError : LinkError
{
    QPID_CLIENT_EXTERN AddressError(const std::string&);
};

/**
 * Thrown when a syntactically correct address cannot be resolved or
 * used.
 */
struct ResolutionError : public AddressError 
{
    QPID_CLIENT_EXTERN ResolutionError(const std::string& msg);
};

struct AssertionFailed : public ResolutionError 
{
    QPID_CLIENT_EXTERN AssertionFailed(const std::string& msg);
};

struct NotFound : public ResolutionError 
{
    QPID_CLIENT_EXTERN NotFound(const std::string& msg);
};

/**
 * Thrown when an address string with inalid sytanx is used.
 */
struct MalformedAddress : public AddressError 
{
    QPID_CLIENT_EXTERN MalformedAddress(const std::string& msg);
};

struct ReceiverError : LinkError
{
    QPID_CLIENT_EXTERN ReceiverError(const std::string&);
};

struct FetchError : ReceiverError
{
    QPID_CLIENT_EXTERN FetchError(const std::string&);
};

struct NoMessageAvailable : FetchError
{
    QPID_CLIENT_EXTERN NoMessageAvailable();
};

struct SenderError : LinkError
{
    QPID_CLIENT_EXTERN SenderError(const std::string&);
};

struct SendError : SenderError
{
    QPID_CLIENT_EXTERN SendError(const std::string&);
};

struct TargetCapacityExceeded : SendError
{
    QPID_CLIENT_EXTERN TargetCapacityExceeded(const std::string&);
};

struct SessionError : MessagingException
{
    QPID_CLIENT_EXTERN SessionError(const std::string&);
};

struct TransactionError : SessionError
{
    QPID_CLIENT_EXTERN TransactionError(const std::string&);
};

struct TransactionAborted : TransactionError
{
    QPID_CLIENT_EXTERN TransactionAborted(const std::string&);
};

struct UnauthorizedAccess : SessionError
{
    QPID_CLIENT_EXTERN UnauthorizedAccess(const std::string&);
};

struct ConnectionError : MessagingException
{
    QPID_CLIENT_EXTERN ConnectionError(const std::string&);
};

struct TransportFailure : MessagingException
{
    QPID_CLIENT_EXTERN TransportFailure(const std::string&);
};

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_EXCEPTIONS_H*/
