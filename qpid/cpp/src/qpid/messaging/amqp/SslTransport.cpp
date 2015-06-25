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
#include "SslTransport.h"
#include "TransportContext.h"
#include "qpid/messaging/ConnectionOptions.h"
#include "qpid/sys/ssl/SslSocket.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/Poller.h"
#include "qpid/client/ssl.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>
#include <boost/format.hpp>

using namespace qpid::sys;
using namespace qpid::sys::ssl;

namespace qpid {
namespace messaging {
namespace amqp {

// Static constructor which registers connector here
namespace {
Transport* create(TransportContext& c, Poller::shared_ptr p)
{
    qpid::client::initialiseSSL();
    return new SslTransport(c, p);
}

struct StaticInit
{
    StaticInit()
    {
        Transport::add("ssl", &create);
    };

    ~StaticInit()
    {
        qpid::client::shutdownSSL();
    }
} init;
}


SslTransport::SslTransport(TransportContext& c, boost::shared_ptr<Poller> p) : context(c), connector(0), aio(0), poller(p)
{
    const ConnectionOptions* options = context.getOptions();
    options->configureSocket(socket);
    if (options->sslCertName != "") {
        QPID_LOG(debug, "ssl-cert-name = " << options->sslCertName);
        socket.setCertName(options->sslCertName);
    }
    if (options->sslIgnoreHostnameVerificationFailure) {
        socket.ignoreHostnameVerificationFailure();
    }
}

void SslTransport::connect(const std::string& host, const std::string& port)
{
    assert(!connector);
    assert(!aio);
    connector = AsynchConnector::create(
        socket,
        host, port,
        boost::bind(&SslTransport::connected, this, _1),
        boost::bind(&SslTransport::failed, this, _3));

    connector->start(poller);
}

void SslTransport::failed(const std::string& msg)
{
    QPID_LOG(debug, "Failed to connect: " << msg);
    socket.close();
    context.closed();
}

void SslTransport::connected(const Socket&)
{
    context.opened();
    aio = AsynchIO::create(socket,
                           boost::bind(&SslTransport::read, this, _1, _2),
                           boost::bind(&SslTransport::eof, this, _1),
                           boost::bind(&SslTransport::disconnected, this, _1),
                           boost::bind(&SslTransport::socketClosed, this, _1, _2),
                           0, // nobuffs
                           boost::bind(&SslTransport::write, this, _1));
    aio->createBuffers(std::numeric_limits<uint16_t>::max());//note: AMQP 1.0 _can_ handle large frame sizes
    id = boost::str(boost::format("[%1%]") % socket.getFullAddress());
    aio->start(poller);
}

void SslTransport::read(AsynchIO&, AsynchIO::BufferBase* buffer)
{
    int32_t decoded = context.getCodec().decode(buffer->bytes+buffer->dataStart, buffer->dataCount);
    if (decoded < buffer->dataCount) {
        // Adjust buffer for used bytes and then "unread them"
        buffer->dataStart += decoded;
        buffer->dataCount -= decoded;
        aio->unread(buffer);
    } else {
        // Give whole buffer back to aio subsystem
        aio->queueReadBuffer(buffer);
    }
}

void SslTransport::write(AsynchIO&)
{
    if (context.getCodec().canEncode()) {
        AsynchIO::BufferBase* buffer = aio->getQueuedBuffer();
        if (buffer) {
            size_t encoded = context.getCodec().encode(buffer->bytes, buffer->byteCount);

            buffer->dataStart = 0;
            buffer->dataCount = encoded;
            aio->queueWrite(buffer);
        }
    }

}

void SslTransport::close()
{
    QPID_LOG(debug, id << " SslTransport closing...");
    if (aio)
        aio->queueWriteClose();
}

void SslTransport::eof(AsynchIO&)
{
    close();
}

void SslTransport::disconnected(AsynchIO&)
{
    close();
    socketClosed(*aio, socket);
}

void SslTransport::socketClosed(AsynchIO&, const Socket&)
{
    if (aio)
        aio->queueForDeletion();
    context.closed();
    QPID_LOG(debug, id << " Socket closed");
}

void SslTransport::abort()
{
    if (aio) {
        // Established connection
        aio->requestCallback(boost::bind(&SslTransport::eof, this, _1));
    }
}

void SslTransport::activateOutput()
{
    if (aio) aio->notifyPendingWrite();
}

const qpid::sys::SecuritySettings* SslTransport::getSecuritySettings()
{
    securitySettings.ssf = socket.getKeyLen();
    securitySettings.authid = "dummy";//set to non-empty string to enable external authentication
    return &securitySettings;
}

}}} // namespace qpid::messaging::amqp
