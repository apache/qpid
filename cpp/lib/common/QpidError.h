#ifndef __QpidError__
#define __QpidError__
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
#include <string>
#include <memory>
#include <ostream>
#include <Exception.h>
#include <boost/current_function.hpp>

namespace qpid {

class QpidError : public Exception { 
  public:
    // Use macro QPID_LOCATION to construct a location.
    struct Location {
        Location(const char* function_=0, const char* file_=0, int line_=0) :
            function(function_), file(file_), line(line_) {}
        const char* function;
        const char* file;
        int line;
    };

    const int code;
    const std::string msg;
    const Location location;

    QpidError();
    QpidError(int _code, const char* _msg, const Location _loc) throw();
    QpidError(int _code, const std::string& _msg, const Location _loc) throw();
    
    ~QpidError() throw();
    Exception::auto_ptr clone() const throw();
    void throwSelf() const;

  private:
    void setWhat();
};


} // namespace qpid

#define QPID_ERROR_LOCATION \
    ::qpid::QpidError::Location(BOOST_CURRENT_FUNCTION, __FILE__, __LINE__)

#define QPID_ERROR(CODE, MESSAGE) \
    ::qpid::QpidError((CODE), (MESSAGE), QPID_ERROR_LOCATION)

#define THROW_QPID_ERROR(CODE, MESSAGE) throw QPID_ERROR(CODE,MESSAGE)

const int PROTOCOL_ERROR = 10000;
const int APR_ERROR = 20000;
const int FRAMING_ERROR = 30000;
const int CLIENT_ERROR = 40000;
const int INTERNAL_ERROR = 50000;

#endif
