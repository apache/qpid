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
    explicit Exception(const std::string& message=std::string(),
                       const std::string& name=std::string(),
                       uint16_t code=0) throw();
    
    virtual ~Exception() throw();

    // returns "name: message"
    virtual const char* what() const throw();

    virtual std::string getName() const throw();
    virtual std::string getMessage() const throw();
    virtual uint16_t getCode() const throw();

    // FIXME aconway 2008-02-21: backwards compat, remove?
    std::string str() const throw() { return getMessage(); } 
    
  private:
    const std::string message;
    const std::string name;
    const uint16_t code;
    const std::string whatStr;
};

/**
 * I have made SessionException a common base for Channel- and
 * Connection- Exceptions. This is not strictly correct but allows all
 * model layer exceptions to be handled as SessionExceptions which is
 * how they are classified in the final 0-10 specification. I.e. this
 * is a temporary hack to allow the preview and final codepaths to
 * co-exist with minimal changes.
 */

struct SessionException : public Exception {
    const framing::ReplyCode code;
    SessionException(framing::ReplyCode code_, const std::string& message)
        : Exception(message), code(code_) {}
};

struct ChannelException : public SessionException {
    ChannelException(framing::ReplyCode code, const std::string& message)
        : SessionException(code, message) {}
};

struct ConnectionException : public SessionException {
    ConnectionException(framing::ReplyCode code, const std::string& message)
        : SessionException(code, message) {}
};

struct ClosedException : public Exception {
    ClosedException(const std::string& msg=std::string());
};

} // namespace qpid
 
#endif  /*!_Exception_*/
