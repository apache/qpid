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

#include "qpid/client/TCPConnector.h"

#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/ConnectionSettings.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Codec.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/InitiationHandler.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/Msg.h"

#include <iostream>
#include <boost/bind.hpp>
#include <boost/format.hpp>

namespace qpid {
namespace client {

using namespace qpid::sys;
using namespace qpid::framing;
using boost::format;
using boost::str;

// Static constructor which registers connector here
namespace {
    Connector* create(Poller::shared_ptr p, framing::ProtocolVersion v, const ConnectionSettings& s, ConnectionImpl* c) {
        return new TCPConnector(p, v, s, c);
    }

    struct StaticInit {
        StaticInit() {
            Connector::registerFactory("tcp", &create);
        };
    } init;
}

TCPConnector::TCPConnector(Poller::shared_ptr p,
                     ProtocolVersion ver,
                     const ConnectionSettings& settings,
                     ConnectionImpl* cimpl)
    : maxFrameSize(settings.maxFrameSize),
      lastEof(0),
      currentSize(0),
      bounds(cimpl),
      version(ver),
      initiated(false),
      closed(true),
      shutdownHandler(0),
      input(0),
      socket(createSocket()),
      connector(0),
      aio(0),
      poller(p)
{
    QPID_LOG(debug, "TCPConnector created for " << version);
    settings.configureSocket(*socket);
}

TCPConnector::~TCPConnector() {
    close();
}

void TCPConnector::connect(const std::string& host, const std::string& port) {
    Mutex::ScopedLock l(lock);
    assert(closed);
    connector = AsynchConnector::create(
        *socket,
        host, port,
        boost::bind(&TCPConnector::connected, this, _1),
        boost::bind(&TCPConnector::connectFailed, this, _3));
    closed = false;

    connector->start(poller);
}

void TCPConnector::connected(const Socket&) {
    connector = 0;
    aio = AsynchIO::create(*socket,
                       boost::bind(&TCPConnector::readbuff, this, _1, _2),
                       boost::bind(&TCPConnector::eof, this, _1),
                       boost::bind(&TCPConnector::disconnected, this, _1),
                       boost::bind(&TCPConnector::socketClosed, this, _1, _2),
                       0, // nobuffs
                       boost::bind(&TCPConnector::writebuff, this, _1));
    start(aio);
    initAmqp();
    aio->start(poller);
}

void TCPConnector::start(sys::AsynchIO* aio_) {
    aio = aio_;

    aio->createBuffers(maxFrameSize);

    identifier = str(format("[%1%]") % socket->getFullAddress());
}

void TCPConnector::initAmqp() {
    ProtocolInitiation init(version);
    writeDataBlock(init);
}

void TCPConnector::connectFailed(const std::string& msg) {
    connector = 0;
    QPID_LOG(warning, "Connect failed: " << msg);
    socket->close();
    if (!closed)
        closed = true;
    if (shutdownHandler)
        shutdownHandler->shutdown();
}

void TCPConnector::close() {
    Mutex::ScopedLock l(lock);
    if (!closed) {
        closed = true;
        if (aio)
            aio->queueWriteClose();
    }
}

void TCPConnector::socketClosed(AsynchIO&, const Socket&) {
    if (aio)
        aio->queueForDeletion();
    if (shutdownHandler)
        shutdownHandler->shutdown();
}

void TCPConnector::connectAborted() {
    connector->stop();
    connectFailed("Connection timedout");
}

void TCPConnector::abort() {
    // Can't abort a closed connection
    if (!closed) {
        if (aio) {
            // Established connection
            aio->requestCallback(boost::bind(&TCPConnector::disconnected, this, _1));
        } else if (connector) {
            // We're still connecting
            connector->requestCallback(boost::bind(&TCPConnector::connectAborted, this));
        }
    }
}

void TCPConnector::setInputHandler(InputHandler* handler){
    input = handler;
}

void TCPConnector::setShutdownHandler(ShutdownHandler* handler){
    shutdownHandler = handler;
}

const std::string& TCPConnector::getIdentifier() const { 
    return identifier;
}

void TCPConnector::handle(AMQFrame& frame) {
    bool notifyWrite = false;
    {
    Mutex::ScopedLock l(lock);
    frames.push_back(frame);
    //only ask to write if this is the end of a frameset or if we
    //already have a buffers worth of data
    currentSize += frame.encodedSize();
    if (frame.getEof()) {
        lastEof = frames.size();
        notifyWrite = true;
    } else {
        notifyWrite = (currentSize >= maxFrameSize);
    }
    /*
      NOTE: Moving the following line into this mutex block
            is a workaround for BZ 570168, in which the test
            testConcurrentSenders causes a hang about 1.5%
            of the time.  ( To see the hang much more frequently
            leave this line out of the mutex block, and put a 
            small usleep just before it.)

            TODO mgoulish - fix the underlying cause and then
                            move this call back outside the mutex.
    */
    if (notifyWrite && !closed) aio->notifyPendingWrite();
    }
}

void TCPConnector::writebuff(AsynchIO& /*aio*/) 
{
    // It's possible to be disconnected and be writable
    if (closed)
        return;

    Codec* codec = securityLayer.get() ? (Codec*) securityLayer.get() : (Codec*) this;

    if (!codec->canEncode()) {
        return;
    }

    AsynchIO::BufferBase* buffer = aio->getQueuedBuffer();
    if (buffer) {

        size_t encoded = codec->encode(buffer->bytes, buffer->byteCount);

        buffer->dataStart = 0;
        buffer->dataCount = encoded;
        aio->queueWrite(buffer);
    }
}

// Called in IO thread.
bool TCPConnector::canEncode()
{
    Mutex::ScopedLock l(lock);
    //have at least one full frameset or a whole buffers worth of data
    return lastEof || currentSize >= maxFrameSize;
}

// Called in IO thread.
size_t TCPConnector::encode(char* buffer, size_t size)
{
    framing::Buffer out(buffer, size);
    size_t bytesWritten(0);
    {
        Mutex::ScopedLock l(lock);
        while (!frames.empty() && out.available() >= frames.front().encodedSize() ) {
            frames.front().encode(out);
            QPID_LOG(trace, "SENT [" << identifier << "]: " << frames.front());
            frames.pop_front();
            if (lastEof) --lastEof;
        }
        bytesWritten = size - out.available();
        currentSize -= bytesWritten;
    }
    if (bounds) bounds->reduce(bytesWritten);
    return bytesWritten;
}

void TCPConnector::readbuff(AsynchIO& aio, AsynchIO::BufferBase* buff)
{
    Codec* codec = securityLayer.get() ? (Codec*) securityLayer.get() : (Codec*) this;
    int32_t decoded = codec->decode(buff->bytes+buff->dataStart, buff->dataCount);
    // TODO: unreading needs to go away, and when we can cope
    // with multiple sub-buffers in the general buffer scheme, it will
    if (decoded < buff->dataCount) {
        // Adjust buffer for used bytes and then "unread them"
        buff->dataStart += decoded;
        buff->dataCount -= decoded;
        aio.unread(buff);
    } else {
        // Give whole buffer back to aio subsystem
        aio.queueReadBuffer(buff);
    }
}

size_t TCPConnector::decode(const char* buffer, size_t size)
{
    framing::Buffer in(const_cast<char*>(buffer), size);
    try {
        if (checkProtocolHeader(in, version)) {
            AMQFrame frame;
            while(frame.decode(in)){
                QPID_LOG(trace, "RECV [" << identifier << "]: " << frame);
                input->received(frame);
            }
        }
    } catch (const ProtocolVersionError& e) {
        QPID_LOG(info, "Closing connection due to " << e.what());
        close();
    }
    return size - in.available();
}

void TCPConnector::writeDataBlock(const AMQDataBlock& data) {
    AsynchIO::BufferBase* buff = aio->getQueuedBuffer();
    assert(buff);
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.encodedSize();
    aio->queueWrite(buff);
}

void TCPConnector::eof(AsynchIO&) {
    close();
}

void TCPConnector::disconnected(AsynchIO&) {
    close();
    socketClosed(*aio, *socket);
}

void TCPConnector::activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer> sl)
{
    securityLayer = sl;
    securityLayer->init(this);
}

}} // namespace qpid::client
