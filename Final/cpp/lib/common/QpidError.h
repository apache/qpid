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

namespace qpid {

struct SrcLine {
  public:
    SrcLine(const std::string& file_="", int line_=0) :
        file(file_), line(line_) {}

    std::string file;
    int line;
};
    
class QpidError : public Exception { 
  public:
    const int code;
    const std::string msg;
    const SrcLine location;

    QpidError();
    QpidError(int _code, const std::string& _msg, const SrcLine& _loc) throw();
    ~QpidError() throw();
    Exception* clone() const throw();
    void throwSelf() const;
};


} // namespace qpid

#define SRCLINE ::qpid::SrcLine(__FILE__, __LINE__)

#define QPID_ERROR(CODE, MESSAGE) ::qpid::QpidError((CODE), (MESSAGE), SRCLINE)

#define THROW_QPID_ERROR(CODE, MESSAGE) throw QPID_ERROR(CODE,MESSAGE)

const int PROTOCOL_ERROR = 10000;
const int APR_ERROR = 20000;
const int FRAMING_ERROR = 30000;
const int CLIENT_ERROR = 40000;
const int INTERNAL_ERROR = 50000;

#endif
