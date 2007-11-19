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

#include <sys/Acceptor.h>
#include <Exception.h>

namespace qpid {
namespace sys {

namespace {
void fail() { throw qpid::Exception("PosixAcceptor not implemented"); }
}

class PosixAcceptor : public Acceptor {
  public:
    virtual int16_t getPort() const { fail(); return 0; }
    virtual void run(qpid::sys::SessionHandlerFactory* ) { fail(); }
    virtual void shutdown() { fail(); }
};

// Define generic Acceptor::create() to return APRAcceptor.
    Acceptor::shared_ptr Acceptor::create(int16_t , int, int, bool)
{
    return Acceptor::shared_ptr(new PosixAcceptor());
}

// Must define Acceptor virtual dtor.
Acceptor::~Acceptor() {}

}}
