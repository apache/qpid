#ifndef QPID_SYS_TRANSPORTFACTORY_H
#define QPID_SYS_TRANSPORTFACTORY_H

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

#include "qpid/SharedObject.h"
#include "qpid/sys/ConnectionCodec.h"
#include <string>
#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace sys {

class AsynchAcceptor;
class Poller;
class Timer;

class TransportAcceptor : public qpid::SharedObject<TransportAcceptor>
{
  public:
    virtual ~TransportAcceptor() = 0;
    virtual void accept(boost::shared_ptr<Poller>, ConnectionCodec::Factory*) = 0;
};

inline TransportAcceptor::~TransportAcceptor() {}

class TransportConnector : public qpid::SharedObject<TransportConnector>
{
public:
    typedef boost::function2<void, int, std::string> ConnectFailedCallback;

    virtual ~TransportConnector() = 0;
    virtual void connect(
        boost::shared_ptr<Poller>,
        const std::string& name,
        const std::string& host, const std::string& port,
        ConnectionCodec::Factory* codec,
        ConnectFailedCallback failed) = 0;
};

inline TransportConnector::~TransportConnector() {}

}}

#endif
