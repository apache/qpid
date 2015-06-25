#ifndef QPID_MESSAGING_AMQP_SSLTRANSPORT_H
#define QPID_MESSAGING_AMQP_SSLTRANSPORT_H

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
#include "qpid/messaging/amqp/Transport.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/ssl/SslSocket.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace sys {
class ConnectionCodec;
class Poller;
class AsynchConnector;
class AsynchIO;
class AsynchIOBufferBase;
}

namespace messaging {
namespace amqp {
class TransportContext;

class SslTransport : public Transport
{
  public:
    SslTransport(TransportContext&, boost::shared_ptr<qpid::sys::Poller> p);

    void connect(const std::string& host, const std::string& port);

    void activateOutput();
    void abort();
    void connectionEstablished() {};
    void close();
    const qpid::sys::SecuritySettings* getSecuritySettings();

  private:
    qpid::sys::ssl::SslSocket socket;
    TransportContext& context;
    qpid::sys::AsynchConnector* connector;
    qpid::sys::AsynchIO* aio;
    boost::shared_ptr<qpid::sys::Poller> poller;
    bool closed;
    std::string id;
    qpid::sys::SecuritySettings securitySettings;

    void connected(const qpid::sys::Socket&);
    void failed(const std::string& msg);
    void read(qpid::sys::AsynchIO&, qpid::sys::AsynchIOBufferBase*);
    void write(qpid::sys::AsynchIO&);
    void eof(qpid::sys::AsynchIO&);
    void disconnected(qpid::sys::AsynchIO&);
    void socketClosed(qpid::sys::AsynchIO&, const qpid::sys::Socket&);

  friend class DriverImpl;
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_SSLTRANSPORT_H*/
