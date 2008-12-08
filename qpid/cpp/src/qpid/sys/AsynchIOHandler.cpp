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

#include "AsynchIOHandler.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Socket.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace sys {

// Buffer definition
struct Buff : public AsynchIO::BufferBase {
    Buff() :
        AsynchIO::BufferBase(new char[65536], 65536)
    {}
    ~Buff()
    { delete [] bytes;}
};

AsynchIOHandler::AsynchIOHandler(std::string id, ConnectionCodec::Factory* f) :
    identifier(id),
    aio(0),
    factory(f),
    codec(0),
    readError(false),
    isClient(false),
    readCredit(InfiniteCredit)
{}

AsynchIOHandler::~AsynchIOHandler() {
    if (codec)
        codec->closed();
    delete codec;
}

void AsynchIOHandler::init(AsynchIO* a, int numBuffs) {
    aio = a;

    // Give connection some buffers to use
    for (int i = 0; i < numBuffs; i++) {
        aio->queueReadBuffer(new Buff);
    }
}

void AsynchIOHandler::write(const framing::ProtocolInitiation& data)
{
    QPID_LOG(debug, "SENT [" << identifier << "] INIT(" << data << ")");
    AsynchIO::BufferBase* buff = aio->getQueuedBuffer();
    if (!buff)
        buff = new Buff;
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.encodedSize();
    aio->queueWrite(buff);
}

void AsynchIOHandler::activateOutput() {
    aio->notifyPendingWrite();
}

// Input side
void AsynchIOHandler::giveReadCredit(int32_t credit) {
    // Check whether we started in the don't about credit state
    if (readCredit.boolCompareAndSwap(InfiniteCredit, credit))
        return;
    else if (readCredit.fetchAndAdd(credit) != 0)
        return;
    // Lock and retest credit to make sure we don't race with decreasing credit
    ScopedLock<Mutex> l(creditLock);
    assert(readCredit.get() >= 0);
    if (readCredit.get() != 0)
        aio->startReading();
}

bool AsynchIOHandler::readbuff(AsynchIO& , AsynchIO::BufferBase* buff) {
    if (readError) {
        return false;
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
            QPID_LOG(debug, "RECV [" << identifier << "] INIT(" << protocolInit << ")");
            try {
                codec = factory->create(protocolInit.getVersion(), *this, identifier);
                if (!codec) {
                    //TODO: may still want to revise this...
                    //send valid version header & close connection.
                    write(framing::ProtocolInitiation(framing::highestProtocolVersion));
                    readError = true;
                    aio->queueWriteClose();                
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
    // Check here for read credit
    if (readCredit.get() != InfiniteCredit) {
        if (--readCredit == 0) {
            // Lock and retest credit to make sure we don't race with increasing credit
            ScopedLock<Mutex> l(creditLock);
            assert(readCredit.get() >= 0);
            if (readCredit.get() == 0)
                return false;
        }
    }
    return true;
}

void AsynchIOHandler::eof(AsynchIO&) {
    QPID_LOG(debug, "DISCONNECTED [" << identifier << "]");
    if (codec) codec->closed();
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

void AsynchIOHandler::disconnect(AsynchIO& a) {
    // treat the same as eof
    eof(a);
}

// Notifications
void AsynchIOHandler::nobuffs(AsynchIO&) {
}

void AsynchIOHandler::idle(AsynchIO&){
    if (isClient && codec == 0) {
        codec = factory->create(*this, identifier);
        write(framing::ProtocolInitiation(codec->getVersion()));
        return;
    }
    if (codec == 0) return;
    if (codec->canEncode()) {
        // Try and get a queued buffer if not then construct new one
        AsynchIO::BufferBase* buff = aio->getQueuedBuffer();
        if (!buff) buff = new Buff;
        size_t encoded=codec->encode(buff->bytes, buff->byteCount);
        buff->dataCount = encoded;
        aio->queueWrite(buff);
    }
    if (codec->isClosed())
        aio->queueWriteClose();
}

}} // namespace qpid::sys
