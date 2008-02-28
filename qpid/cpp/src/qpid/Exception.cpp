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

#include "qpid/log/Statement.h"
#include "Exception.h"
#include <typeinfo>
#include <errno.h>
#include <assert.h>

namespace qpid {

std::string strError(int err) {
    char buf[512];
    return std::string(strerror_r(err, buf, sizeof(buf)));
}

Exception::Exception(const std::string& msg,
                     const std::string& nm,
                     uint16_t cd) throw()
    : message(msg), name(nm), code(cd),
      whatStr((name.empty() ? "" : name + ": ")+ msg)
{
    QPID_LOG(warning, "Exception: " << whatStr);
}

Exception::~Exception() throw() {}

std::string Exception::getMessage() const throw() { return message; }

std::string Exception::getName() const throw() {
    return name.empty() ? typeid(*this).name() : name;
}

uint16_t Exception::getCode() const throw() { return code; }

const char* Exception::what() const throw() {
    if (whatStr.empty()) return typeid(*this).name();
    else return whatStr.c_str();
}

ClosedException::ClosedException(const std::string& msg)
  : Exception(msg, "ClosedException") {}

} // namespace qpid
