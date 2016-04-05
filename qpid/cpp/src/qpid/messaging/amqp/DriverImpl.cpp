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
#include "DriverImpl.h"
#include "Transport.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/Timer.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace messaging {
namespace amqp {

DriverImpl::DriverImpl() : poller(new qpid::sys::Poller), timer(new qpid::sys::Timer)
{
    start();
}
DriverImpl::~DriverImpl()
{
    stop();
}

void DriverImpl::start()
{
    thread = qpid::sys::Thread(*poller);
    QPID_LOG(debug, "Driver started");
}

void DriverImpl::stop()
{
    QPID_LOG(debug, "Driver stopped");
    if (!poller->hasShutdown()) {
        poller->shutdown();
        thread.join();
        timer->stop();
    }
}

boost::shared_ptr<Transport> DriverImpl::getTransport(const std::string& protocol, TransportContext& connection)
{
    boost::shared_ptr<Transport> t(Transport::create(protocol, connection, poller));
    if (!t) throw qpid::messaging::ConnectionError("No such transport: " + protocol);
    return t;
}


qpid::sys::Mutex DriverImpl::defaultLock;
boost::weak_ptr<DriverImpl> DriverImpl::theDefault;
boost::shared_ptr<DriverImpl> DriverImpl::getDefault()
{
    qpid::sys::Mutex::ScopedLock l(defaultLock);
    boost::shared_ptr<DriverImpl> p = theDefault.lock();
    if (!p) {
        p = boost::shared_ptr<DriverImpl>(new DriverImpl);
        theDefault = p;
    }
    return p;
}

}}} // namespace qpid::messaging::amqp
