#ifndef _sys_Acceptor_h
#define _sys_Acceptor_h

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

#include <stdint.h>
#include "qpid/SharedObject.h"
#include "ConnectionCodec.h"


namespace qpid {
namespace sys {

class Poller;

class Acceptor : public qpid::SharedObject<Acceptor>
{
  public:
    virtual ~Acceptor() = 0;
    virtual uint16_t getPort() const = 0;
    virtual std::string getHost() const = 0;
    virtual void run(boost::shared_ptr<Poller>, ConnectionCodec::Factory*) = 0;
    virtual void connect(
        boost::shared_ptr<Poller>,
        const std::string& host, int16_t port,
        ConnectionCodec::Factory* codec) = 0;
};

inline Acceptor::~Acceptor() {}

}}


    
#endif  /*!_sys_Acceptor_h*/
