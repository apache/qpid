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
#include "TcpTransport.h"
#include "ConnectionContext.h"
#include "qpid/messaging/ConnectionOptions.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/Poller.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>
#include <boost/format.hpp>

using namespace qpid::sys;

namespace qpid {
namespace messaging {
namespace amqp {
// Static constructor which registers connector here
namespace {
Transport* create(TransportContext& c, Poller::shared_ptr p)
{
    return new TcpTransport(c, p);
}

struct StaticInit
{
    StaticInit()
    {
        Transport::add("tcp", &create);
    };
} init;
}

TcpTransport::TcpTransport(TransportContext& c, boost::shared_ptr<Poller> p) : socket(createSocket()), context(c), connector(0), aio(0), poller(p), closed(false) {}

void TcpTransport::connect(const std::string& host, const std::string& port)
{
    assert(!connector);
    assert(!aio);
    context.getOptions()->configureSocket(*socket);
    connector = AsynchConnector::create(
        *socket,
        host, port,
        boost::bind(&TcpTransport::connected, this, _1),
        boost::bind(&TcpTransport::failed, this, _3));

    connector->start(poller);
}

void TcpTransport::failed(const std::string& msg)
{
    QPID_LOG(debug, "Failed to connect: " << msg);
    closed = true;
    connector = 0;
    socket->close();
    context.closed();
}

void TcpTransport::connected(const Socket&)
{
    context.opened();
    connector = 0;
    aio = AsynchIO::create(*socket,
                           boost::bind(&TcpTransport::read, this, _1, _2),
                           boost::bind(&TcpTransport::eof, this, _1),
                           boost::bind(&TcpTransport::disconnected, this, _1),
                           boost::bind(&TcpTransport::socketClosed, this, _1, _2),
                           0, // nobuffs
                           boost::bind(&TcpTransport::write, this, _1));
    aio->createBuffers(std::numeric_limits<uint16_t>::max());//note: AMQP 1.0 _can_ handle large frame sizes
    id = boost::str(boost::format("[%1%]") % socket->getFullAddress());
    aio->start(poller);
}

void TcpTransport::read(AsynchIO&, AsynchIO::BufferBase* buffer)
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

void TcpTransport::write(AsynchIO&)
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

void TcpTransport::close()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    if (!closed) {
        QPID_LOG(debug, id << " TcpTransport closing...");
        if (aio)
            aio->queueWriteClose();
    }
}

void TcpTransport::eof(AsynchIO&)
{
    close();
}

void TcpTransport::disconnected(AsynchIO&)
{
    close();
    socketClosed(*aio, *socket);
}

void TcpTransport::socketClosed(AsynchIO&, const Socket&)
{
    bool notify(false);
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        if (!closed) {
            closed = true;
            if (aio)
                aio->queueForDeletion();
            QPID_LOG(debug, id << " Socket closed");
            notify = true;
        } //else has already been closed
    }
    if (notify) context.closed();
}

void TcpTransport::abort()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    if (!closed) {
        if (aio) {
            // Established connection
            aio->requestCallback(boost::bind(&TcpTransport::eof, this, _1));
        } else if (connector) {
            // We're still connecting
            connector->stop();
            failed("Connection timedout");
        }
    }
}

void TcpTransport::activateOutput()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    if (!closed && aio) aio->notifyPendingWrite();
}

const qpid::sys::SecuritySettings* TcpTransport::getSecuritySettings()
{
    return 0;
}

}}} // namespace qpid::messaging::amqp
