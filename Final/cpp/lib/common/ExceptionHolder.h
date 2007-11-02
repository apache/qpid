#ifndef _qpid_ExceptionHolder_h
#define _qpid_ExceptionHolder_h

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

#include <Exception.h>
#include <boost/shared_ptr.hpp>

namespace qpid {

/**
 * Holder for a heap-allocated exc eption that can be stack allocated
 * and thrown safely.
 *
 * Basically this is a shared_ptr with the Exception functions added
 * so the catcher need not be aware that it is a pointer rather than a
 * reference.
 * 
 * shared_ptr is chosen over auto_ptr because it has normal 
 * copy semantics. 
 */
class ExceptionHolder : public Exception, public boost::shared_ptr<Exception>
{
  public:
    typedef boost::shared_ptr<Exception> shared_ptr;

    ExceptionHolder() throw() {}
    ExceptionHolder(Exception* p) throw() : shared_ptr(p) {}
    ExceptionHolder(shared_ptr p) throw() : shared_ptr(p) {}

    ExceptionHolder(const Exception& e) throw() : shared_ptr(e.clone()) {}
    ExceptionHolder(const std::exception& e);

    ~ExceptionHolder() throw() {}

    const char* what() const throw() { return (*this)->what(); }
    std::string toString() const throw() { return (*this)->toString(); }
    virtual Exception* clone() const throw() { return (*this)->clone(); }
    virtual void throwSelf() const { (*this)->throwSelf(); }
};

} // namespace qpid



#endif  /*!_qpid_ExceptionHolder_h*/
