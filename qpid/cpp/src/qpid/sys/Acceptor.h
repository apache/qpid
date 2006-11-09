#ifndef _sys_Acceptor_h
#define _sys_Acceptor_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <stdint.h>
#include <qpid/SharedObject.h>

namespace qpid {
namespace sys {

class SessionHandlerFactory;

class Acceptor : public qpid::SharedObject<Acceptor>
{
  public:
    static Acceptor::shared_ptr create(int16_t port, int backlog, int threads);
    virtual ~Acceptor() = 0;
    virtual int16_t getPort() const = 0;
    virtual void run(qpid::sys::SessionHandlerFactory* factory) = 0;
    virtual void shutdown() = 0;
};

}}


    
#endif  /*!_sys_Acceptor_h*/
