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
#include "qpid/client/Connector.h"

#include "config.h"
#include "qpid/client/Bounds.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/ConnectionSettings.h"
#include "qpid/Options.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/InitiationHandler.h"
#include "qpid/sys/ssl/util.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/ssl/SslSocket.h"
#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/Msg.h"
#include "qpid/types/Exception.h"

#include <iostream>
#include <boost/bind.hpp>
#include <boost/format.hpp>

namespace qpid {
namespace client {

using namespace qpid::sys;
using namespace qpid::sys::ssl;
using namespace qpid::framing;
using boost::format;
using boost::str;


class SslConnector : public Connector
{
    typedef std::deque<framing::AMQFrame> Frames;

    const uint16_t maxFrameSize;

    sys::Mutex lock;
    Frames frames;
    size_t lastEof; // Position after last EOF in frames
    uint64_t currentSize;
    Bounds* bounds;

    framing::ProtocolVersion version;
    bool initiated;
    bool closed;

    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;

    sys::ssl::SslSocket socket;

    sys::AsynchConnector* connector;
    sys::AsynchIO* aio;
    std::string identifier;
    Poller::shared_ptr poller;
    SecuritySettings securitySettings;

    ~SslConnector();

    void readbuff(AsynchIO&, AsynchIOBufferBase*);
    void writebuff(AsynchIO&);
    void writeDataBlock(const framing::AMQDataBlock& data);
    void eof(AsynchIO&);
    void disconnected(AsynchIO&);

    void connect(const std::string& host, const std::string& port);
    void connected(const sys::Socket&);
    void connectFailed(const std::string& msg);

    void close();
    void handle(framing::AMQFrame& frame);
    void abort();
    void connectAborted();

    void setInputHandler(framing::InputHandler* handler);
    void setShutdownHandler(sys::ShutdownHandler* handler);
    const std::string& getIdentifier() const;
    const SecuritySettings* getSecuritySettings();
    void socketClosed(AsynchIO&, const Socket&);

    size_t decode(const char* buffer, size_t size);
    size_t encode(char* buffer, size_t size);
    bool canEncode();

public:
    SslConnector(Poller::shared_ptr p, framing::ProtocolVersion pVersion,
              const ConnectionSettings&,
              ConnectionImpl*);
};

// Static constructor which registers connector here
namespace {
    Connector* create(Poller::shared_ptr p, framing::ProtocolVersion v, const ConnectionSettings& s, ConnectionImpl* c);

    struct StaticInit {
        static bool initialised;

        StaticInit() {
            Connector::registerFactory("ssl", &create);
        };
        ~StaticInit() {
            if (initialised) shutdownNSS();
        }

        void checkInitialised() {
            static qpid::sys::Mutex lock;
            qpid::sys::Mutex::ScopedLock l(lock);
            if (!initialised) {
                CommonOptions common("", "", QPIDC_CONF_FILE);
                SslOptions options;
                try {
                    common.parse(0, 0, common.clientConfig, true);
                    options.parse (0, 0, common.clientConfig, true);
                } catch (const std::exception& e) {
                    throw qpid::types::Exception(QPID_MSG("Failed to parse options while initialising SSL connector: " << e.what()));
                }
                if (options.certDbPath.empty()) {
                    throw qpid::types::Exception(QPID_MSG("SSL connector not enabled, you must set QPID_SSL_CERT_DB to enable it."));
                } else {
                    try {
                        initNSS(options);
                        initialised = true;
                    } catch (const std::exception& e) {
                        throw qpid::types::Exception(QPID_MSG("Failed to initialise SSL: " << e.what()));
                    }
                }
            }
        }

    } init;
    bool StaticInit::initialised = false;

    Connector* create(Poller::shared_ptr p, framing::ProtocolVersion v, const ConnectionSettings& s, ConnectionImpl* c) {
        init.checkInitialised();
        return new SslConnector(p, v, s, c);
    }
}

void initialiseSSL()
{
    init.checkInitialised();
}

void shutdownSSL()
{
    if (StaticInit::initialised) shutdownNSS();
}

SslConnector::SslConnector(Poller::shared_ptr p,
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
      aio(0),
      poller(p)
{
    QPID_LOG(debug, "SslConnector created for " << version.toString());

    if (settings.sslCertName != "") {
        QPID_LOG(debug, "ssl-cert-name = " << settings.sslCertName);
        socket.setCertName(settings.sslCertName);
    }
    if (settings.sslIgnoreHostnameVerificationFailure) {
        socket.ignoreHostnameVerificationFailure();
    }
}

SslConnector::~SslConnector() {
    close();
}

void SslConnector::connect(const std::string& host, const std::string& port) {
    Mutex::ScopedLock l(lock);
    assert(closed);
    connector = AsynchConnector::create(
        socket,
        host, port,
        boost::bind(&SslConnector::connected, this, _1),
        boost::bind(&SslConnector::connectFailed, this, _3));
    closed = false;

    connector->start(poller);
}

void SslConnector::connected(const Socket&) {
    connector = 0;
    aio = AsynchIO::create(socket,
                           boost::bind(&SslConnector::readbuff, this, _1, _2),
                           boost::bind(&SslConnector::eof, this, _1),
                           boost::bind(&SslConnector::disconnected, this, _1),
                           boost::bind(&SslConnector::socketClosed, this, _1, _2),
                           0, // nobuffs
                           boost::bind(&SslConnector::writebuff, this, _1));

    aio->createBuffers(maxFrameSize);
    identifier = str(format("[%1%]") % socket.getFullAddress());
    ProtocolInitiation init(version);
    writeDataBlock(init);
    aio->start(poller);
}

void SslConnector::connectFailed(const std::string& msg) {
    connector = 0;
    QPID_LOG(warning, "Connect failed: " << msg);
    socket.close();
    if (!closed)
        closed = true;
    if (shutdownHandler)
        shutdownHandler->shutdown();
}

void SslConnector::close() {
    Mutex::ScopedLock l(lock);
    if (!closed) {
        closed = true;
        if (aio)
            aio->queueWriteClose();
    }
}

void SslConnector::socketClosed(AsynchIO&, const Socket&) {
    if (aio)
        aio->queueForDeletion();
    if (shutdownHandler)
        shutdownHandler->shutdown();
}

void SslConnector::connectAborted() {
    connector->stop();
    connectFailed("Connection timedout");
}

void SslConnector::abort() {
    // Can't abort a closed connection
    if (!closed) {
        if (aio) {
            // Established connection
            aio->requestCallback(boost::bind(&SslConnector::eof, this, _1));
        } else if (connector) {
            // We're still connecting
            connector->requestCallback(boost::bind(&SslConnector::connectAborted, this));
        }
    }
}

void SslConnector::setInputHandler(InputHandler* handler){
    input = handler;
}

void SslConnector::setShutdownHandler(ShutdownHandler* handler){
    shutdownHandler = handler;
}

const std::string& SslConnector::getIdentifier() const {
    return identifier;
}

void SslConnector::handle(AMQFrame& frame) {
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

void SslConnector::writebuff(AsynchIO& /*aio*/)
{
    // It's possible to be disconnected and be writable
    if (closed)
        return;

    if (!canEncode()) {
        return;
    }

    AsynchIOBufferBase* buffer = aio->getQueuedBuffer();
    if (buffer) {

        size_t encoded = encode(buffer->bytes, buffer->byteCount);

        buffer->dataStart = 0;
        buffer->dataCount = encoded;
        aio->queueWrite(buffer);
    }
}

// Called in IO thread.
bool SslConnector::canEncode()
{
    Mutex::ScopedLock l(lock);
    //have at least one full frameset or a whole buffers worth of data
    return lastEof || currentSize >= maxFrameSize;
}

// Called in IO thread.
size_t SslConnector::encode(char* buffer, size_t size)
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

void SslConnector::readbuff(AsynchIO& aio, AsynchIOBufferBase* buff)
{
    int32_t decoded = decode(buff->bytes+buff->dataStart, buff->dataCount);
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

size_t SslConnector::decode(const char* buffer, size_t size)
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

void SslConnector::writeDataBlock(const AMQDataBlock& data) {
    AsynchIOBufferBase* buff = aio->getQueuedBuffer();
    assert(buff);
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.encodedSize();
    aio->queueWrite(buff);
}

void SslConnector::eof(AsynchIO&) {
    close();
}

void SslConnector::disconnected(AsynchIO&) {
    close();
    socketClosed(*aio, socket);
}

const SecuritySettings* SslConnector::getSecuritySettings()
{
    securitySettings.ssf = socket.getKeyLen();
    securitySettings.authid = socket.getLocalAuthId();
    return &securitySettings;
}

}} // namespace qpid::client
