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

#include "qpid/messaging/ImportExport.h"
#include "qpid/types/Exception.h"
#include "qpid/types/Variant.h"

namespace qpid {
namespace messaging {

/** \ingroup messaging 
 */

struct QPID_MESSAGING_CLASS_EXTERN MessagingException : public qpid::types::Exception 
{
    QPID_MESSAGING_EXTERN MessagingException(const std::string& msg);
    QPID_MESSAGING_EXTERN virtual ~MessagingException() throw();
    
    qpid::types::Variant::Map detail;
    //TODO: override what() to include detail if present
};

struct QPID_MESSAGING_CLASS_EXTERN InvalidOptionString : public MessagingException 
{
    QPID_MESSAGING_EXTERN InvalidOptionString(const std::string& msg);
};

struct QPID_MESSAGING_CLASS_EXTERN KeyError : public MessagingException
{
    QPID_MESSAGING_EXTERN KeyError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN LinkError : public MessagingException
{
    QPID_MESSAGING_EXTERN LinkError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN AddressError : public LinkError
{
    QPID_MESSAGING_EXTERN AddressError(const std::string&);
};

/**
 * Thrown when a syntactically correct address cannot be resolved or
 * used.
 */
struct QPID_MESSAGING_CLASS_EXTERN ResolutionError : public AddressError 
{
    QPID_MESSAGING_EXTERN ResolutionError(const std::string& msg);
};

struct QPID_MESSAGING_CLASS_EXTERN AssertionFailed : public ResolutionError 
{
    QPID_MESSAGING_EXTERN AssertionFailed(const std::string& msg);
};

struct QPID_MESSAGING_CLASS_EXTERN NotFound : public ResolutionError 
{
    QPID_MESSAGING_EXTERN NotFound(const std::string& msg);
};

/**
 * Thrown when an address string with inalid sytanx is used.
 */
struct QPID_MESSAGING_CLASS_EXTERN MalformedAddress : public AddressError 
{
    QPID_MESSAGING_EXTERN MalformedAddress(const std::string& msg);
};

struct QPID_MESSAGING_CLASS_EXTERN ReceiverError : public LinkError
{
    QPID_MESSAGING_EXTERN ReceiverError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN FetchError : public ReceiverError
{
    QPID_MESSAGING_EXTERN FetchError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN NoMessageAvailable : public FetchError
{
    QPID_MESSAGING_EXTERN NoMessageAvailable();
};

struct QPID_MESSAGING_CLASS_EXTERN SenderError : public LinkError
{
    QPID_MESSAGING_EXTERN SenderError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN SendError : public SenderError
{
    QPID_MESSAGING_EXTERN SendError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN TargetCapacityExceeded : public SendError
{
    QPID_MESSAGING_EXTERN TargetCapacityExceeded(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN SessionError : public MessagingException
{
    QPID_MESSAGING_EXTERN SessionError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN TransactionError : public SessionError
{
    QPID_MESSAGING_EXTERN TransactionError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN TransactionAborted : public TransactionError
{
    QPID_MESSAGING_EXTERN TransactionAborted(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN UnauthorizedAccess : public SessionError
{
    QPID_MESSAGING_EXTERN UnauthorizedAccess(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN ConnectionError : public MessagingException
{
    QPID_MESSAGING_EXTERN ConnectionError(const std::string&);
};

struct QPID_MESSAGING_CLASS_EXTERN TransportFailure : public MessagingException
{
    QPID_MESSAGING_EXTERN TransportFailure(const std::string&);
};

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_EXCEPTIONS_H*/
