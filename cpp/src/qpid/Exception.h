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
    Exception() throw() {}
    Exception(const std::string& str) throw() : whatStr(str) {}
    Exception(const char* str) throw() : whatStr(str) {}
    virtual ~Exception() throw();

    virtual const char* what() const throw() { return whatStr.c_str(); }
    virtual std::string toString() const throw() { return whatStr; }
};

/**
 * Wrapper for heap-allocated exceptions. Use like this:
 * <code>
 * std::auto_ptr<Exception> ex = new SomeEx(...)
 * HeapException hex(ex);       // Takes ownership
 * throw hex;                   // Auto-deletes ex
 * </code>
 */
class HeapException : public Exception, public std::auto_ptr<Exception>
{
  public:
    HeapException() {}
    HeapException(std::auto_ptr<Exception> e) : std::auto_ptr<Exception>(e) {}

    HeapException& operator=(std::auto_ptr<Exception>& e) {
        std::auto_ptr<Exception>::operator=(e);
        return *this;
    }

    ~HeapException() throw() {}
    
    virtual const char* what() const throw() { return (*this)->what(); }
    virtual std::string toString() const throw() {
        return (*this)->toString();
    }
};
    
}

#endif  /*!_Exception_*/
