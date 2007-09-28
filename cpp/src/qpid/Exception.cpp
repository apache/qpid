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

namespace qpid {

std::string strError(int err) {
    char buf[512];
    return std::string(strerror_r(err, buf, sizeof(buf)));
}

static void ctorLog(const std::exception* e) {
    QPID_LOG(trace, "Exception: " << e->what());
}
    
Exception::Exception() throw() { ctorLog(this); }

Exception::Exception(const std::string& str) throw()
    : whatStr(str) { ctorLog(this); }

Exception::Exception(const char* str) throw() : whatStr(str) { ctorLog(this); }

Exception::Exception(const std::exception& e) throw() : whatStr(e.what()) {}

Exception::~Exception() throw() {}

const char* Exception::what() const throw() { return whatStr.c_str(); }

std::string Exception::toString() const throw() { return whatStr; }

Exception::auto_ptr Exception::clone() const throw() { return Exception::auto_ptr(new Exception(*this)); }

void Exception::throwSelf() const  { throw *this; }

ShutdownException::ShutdownException() : Exception("Shut down.") {}

EmptyException::EmptyException() : Exception("Empty.") {}

const char* Exception::defaultMessage = "Unexpected exception";

void Exception::log(const char* what, const char* message) {
    QPID_LOG(error, message << ": " << what);
}

void Exception::log(const std::exception& e, const char* message) {
    log(e.what(), message);
}

void Exception::logUnknown(const char* message) {
    log("unknown exception.", message);
}

} // namespace qpid
