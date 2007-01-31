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
#include <exception>
#include <string>
#include <memory>
#include <boost/shared_ptr.hpp>
#include <boost/lexical_cast.hpp>

#include "amqp_types.h"

namespace qpid
{
/**
 * Exception base class for all Qpid exceptions.
 */
class Exception : public std::exception
{
  protected:
    std::string whatStr;

  public:
    Exception() throw();
    Exception(const std::string& str) throw();
    Exception(const char* str) throw();
    Exception(const std::exception&) throw();

    /** Allow any type that has ostream operator<< to act as message */
    template <class T>
    Exception(const T& message)
        : whatStr(boost::lexical_cast<std::string>(message)) {}

    virtual ~Exception() throw();

    virtual const char* what() const throw();
    virtual std::string toString() const throw();

    virtual Exception* clone() const throw();
    virtual void throwSelf() const;

    typedef boost::shared_ptr<Exception> shared_ptr;
};

struct ChannelException : public Exception {
    framing::ReplyCode code;
    template <class T>
    ChannelException(framing::ReplyCode code_, const T& message)
        : Exception(message), code(code_) {}
};

struct ConnectionException : public Exception {
    framing::ReplyCode code;
    template <class T>
    ConnectionException(framing::ReplyCode code_, const T& message)
        : Exception(message), code(code_) {}
};


}

#endif  /*!_Exception_*/
