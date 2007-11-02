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

namespace qpid
{

/** Get the error message for error number err. */
std::string strError(int err);

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

    virtual ~Exception() throw();

    virtual const char* what() const throw();
    virtual std::string toString() const throw();

    virtual Exception* clone() const throw();
    virtual void throwSelf() const;

    typedef boost::shared_ptr<Exception> shared_ptr;
};



}

#endif  /*!_Exception_*/
