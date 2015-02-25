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

#include "qpid/sys/AsynchIOHandler.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/Timer.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>

namespace qpid {
namespace sys {

struct ProtocolTimeoutTask : public sys::TimerTask {
    AsynchIOHandler& handler;
    std::string id;
    Duration timeout;

    ProtocolTimeoutTask(const std::string& i, const Duration& timeout_, AsynchIOHandler& h) :
        TimerTask(timeout_, "ProtocolTimeout"),
        handler(h),
        id(i),
        timeout(timeout_)
    {}

    void fire() {
        // If this fires it means that we didn't negotiate the connection in the timeout period
        // Schedule closing the connection for the io thread
        QPID_LOG(error, "Connection " << id << " No protocol received after " << timeout
                 << ", closing");
        handler.abort();
    }
};

AsynchIOHandler::AsynchIOHandler(const std::string& id, ConnectionCodec::Factory* f, bool isClient0, bool nodict0) :
    identifier(id),
    aio(0),
    factory(f),
    codec(0),
    readError(false),
    isClient(isClient0),
    nodict(nodict0),
    headerSent(false)
{}

AsynchIOHandler::~AsynchIOHandler() {
    if (codec)
        codec->closed();
    if (timeoutTimerTask)
        timeoutTimerTask->cancel();
    delete codec;
}

namespace {
    SecuritySettings getSecuritySettings(AsynchIO* aio, bool nodict)
    {
        SecuritySettings settings = aio->getSecuritySettings();
        settings.nodict = nodict;
        return settings;
    }
}

void AsynchIOHandler::init(qpid::sys::AsynchIO* a, qpid::sys::Timer& timer, uint32_t maxTime) {
    aio = a;

    // Start timer for this connection
    timeoutTimerTask = new ProtocolTimeoutTask(identifier, maxTime*TIME_MSEC, *this);
    timer.add(timeoutTimerTask);

    // Give connection some buffers to use
    aio->createBuffers();

    if (isClient) {
        codec = factory->create(*this, identifier, getSecuritySettings(aio, nodict));
    }
}

void AsynchIOHandler::write(const framing::ProtocolInitiation& data)
{
    QPID_LOG(debug, "SENT [" << identifier << "]: INIT(" << data << ")");
    AsynchIO::BufferBase* buff = aio->getQueuedBuffer();
    assert(buff);
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.encodedSize();
    aio->queueWrite(buff);
}

void AsynchIOHandler::abort() {
    // Don't disconnect if we're already disconnecting
    if (!readError) {
        aio->requestCallback(boost::bind(&AsynchIOHandler::eof, this, _1));
    }
    aio->queueWriteClose();
}

void AsynchIOHandler::connectionEstablished() {
    if (timeoutTimerTask) {
        timeoutTimerTask->cancel();
        timeoutTimerTask = 0;
    }
}

void AsynchIOHandler::activateOutput() {
    aio->notifyPendingWrite();
}

void AsynchIOHandler::readbuff(AsynchIO& , AsynchIO::BufferBase* buff) {
    if (readError) {
        return;
    }

    size_t decoded = 0;
    if (codec) {                // Already initiated
        try {
            decoded = codec->decode(buff->bytes+buff->dataStart, buff->dataCount);
        }catch(const std::exception& e){
            QPID_LOG(error, e.what());
            readError = true;
            aio->queueWriteClose();
        }
    }else{
        framing::Buffer in(buff->bytes+buff->dataStart, buff->dataCount);
        framing::ProtocolInitiation protocolInit;
        if (protocolInit.decode(in)) {
            decoded = in.getPosition();

            QPID_LOG(debug, "RECV [" << identifier << "]: INIT(" << protocolInit << ")");
            try {
                codec = factory->create(protocolInit.getVersion(), *this, identifier, getSecuritySettings(aio, nodict));
                if (!codec) {
                    //TODO: may still want to revise this...
                    //send valid version header & close connection.
                    write(framing::ProtocolInitiation(factory->supportedVersion()));
                    readError = true;
                    aio->queueWriteClose();
                } else {
                    //read any further data that may already have been sent
                    decoded += codec->decode(buff->bytes+buff->dataStart+in.getPosition(), buff->dataCount-in.getPosition());
                }
            } catch (const std::exception& e) {
                QPID_LOG(error, e.what());
                readError = true;
                aio->queueWriteClose();
            }
        }
    }
    // TODO: unreading needs to go away, and when we can cope
    // with multiple sub-buffers in the general buffer scheme, it will
    if (decoded != size_t(buff->dataCount)) {
        // Adjust buffer for used bytes and then "unread them"
        buff->dataStart += decoded;
        buff->dataCount -= decoded;
        aio->unread(buff);
    } else {
        // Give whole buffer back to aio subsystem
        aio->queueReadBuffer(buff);
    }
}

void AsynchIOHandler::eof(AsynchIO& a) {
    disconnect(a);
    readError = true;
    aio->queueWriteClose();
}

void AsynchIOHandler::closedSocket(AsynchIO&, const Socket& s) {
    // If we closed with data still to send log a warning
    if (!aio->writeQueueEmpty()) {
        QPID_LOG(warning, "CLOSING [" << identifier << "] unsent data (probably due to client disconnect)");
    }
    delete &s;
    aio->queueForDeletion();
    delete this;
}

void AsynchIOHandler::disconnect(AsynchIO&) {
    QPID_LOG(debug, "DISCONNECTED [" << identifier << "]");
    if (codec) codec->closed();
}

// Notifications
void AsynchIOHandler::nobuffs(AsynchIO&) {
}

void AsynchIOHandler::idle(AsynchIO&){
    if (isClient && !headerSent) {
        write(framing::ProtocolInitiation(codec->getVersion()));
        headerSent = true;
        return;
    }
    if (codec == 0) return;
    if (!codec->canEncode()) {
        return;
    }
    AsynchIO::BufferBase* buff = aio->getQueuedBuffer();
    if (buff) {
        try {
            size_t encoded=codec->encode(buff->bytes, buff->byteCount);
            buff->dataCount = encoded;
            aio->queueWrite(buff);
            if (!codec->isClosed()) {
                return;
            }
        } catch (const std::exception& e) {
            QPID_LOG(error, e.what());
        }
    }
    readError = true;
    aio->queueWriteClose();
}

}} // namespace qpid::sys
