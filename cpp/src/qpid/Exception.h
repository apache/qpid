#ifndef _Exception_
#define _Exception_

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

#include "qpid/framing/amqp_types.h"
#include "qpid/framing/constants.h"
#include "qpid/Msg.h"

#include <memory>
#include <string>

namespace qpid
{

/** Get the error message for a system number err, e.g. errno. */
std::string strError(int err);

/**
 * Base class for Qpid runtime exceptions.
 */
class Exception : public std::exception
{
  public:
    explicit Exception(const std::string& message=std::string()) throw();
    virtual ~Exception() throw();
    virtual const char* what() const throw(); // prefix: message
    virtual std::string getMessage() const; // Unprefixed message
    virtual std::string getPrefix() const;  // Prefix

  private:
    std::string message;
    mutable std::string whatStr;
};

struct SessionException : public Exception {
    const framing::ReplyCode code;
    SessionException(framing::ReplyCode code_, const std::string& message)
        : Exception(message), code(code_) {}
};

struct ChannelException : public Exception {
    const framing::ReplyCode code;
    ChannelException(framing::ReplyCode _code, const std::string& message)
        : Exception(message), code(_code) {}
};

struct ConnectionException : public Exception {
    const framing::ReplyCode code;
    ConnectionException(framing::ReplyCode _code, const std::string& message)
        : Exception(message), code(_code) {}
};

struct ClosedException : public Exception {
    ClosedException(const std::string& msg=std::string());
    std::string getPrefix() const;
};

} // namespace qpid
 
#endif  /*!_Exception_*/
