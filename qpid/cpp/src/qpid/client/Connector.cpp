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
#include <iostream>
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/AMQFrame.h"
#include "Connector.h"

#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Poller.h"

#include <boost/bind.hpp>

namespace qpid {
namespace client {

using namespace qpid::sys;
using namespace qpid::framing;

Connector::Connector(
    ProtocolVersion ver, bool _debug, uint32_t buffer_size
) : debug(_debug),
    receive_buffer_size(buffer_size),
    send_buffer_size(buffer_size),
    version(ver), 
    closed(true),
    timeout(0),
    idleIn(0), idleOut(0), 
    timeoutHandler(0),
    shutdownHandler(0),
    aio(0)
{}

Connector::~Connector() {
    close();
    if (receiver.id() && receiver.id() != Thread::current().id())
        receiver.join();
}

void Connector::connect(const std::string& host, int port){
    socket.connect(host, port);
    closed = false;
    poller = Poller::shared_ptr(new Poller);
    aio = new AsynchIO(socket,
                       boost::bind(&Connector::readbuff, this, _1, _2),
                       boost::bind(&Connector::eof, this, _1),
                       boost::bind(&Connector::eof, this, _1),
                       0, // closed
                       0, // nobuffs
                       boost::bind(&Connector::writebuff, this, _1));
    writer.setAio(aio);
}

void Connector::init(){
    ProtocolInitiation init(version);

    writeDataBlock(init);
    receiver = Thread(this);
}

bool Connector::closeInternal() {
    Mutex::ScopedLock l(closedLock);
    if (!closed) {
        poller->shutdown();
        closed = true;
        return true;
    }
    return false;
}
        
void Connector::close() {
    closeInternal();
}

void Connector::setInputHandler(InputHandler* handler){
    input = handler;
}

void Connector::setShutdownHandler(ShutdownHandler* handler){
    shutdownHandler = handler;
}

OutputHandler* Connector::getOutputHandler(){ 
    return this; 
}

void Connector::send(AMQFrame& frame) {
    writer.handle(frame);
}

void Connector::handleClosed() {
    if (closeInternal() && shutdownHandler)
        shutdownHandler->shutdown();
}

// TODO: astitcher 20070908: This version of the code can never time out, so the idle processing
// can never be called. The timeut processing needs to be added into the underlying Dispatcher code
//
// TODO: astitcher 20070908: EOF is dealt with separately now via a callback to eof
void Connector::checkIdle(ssize_t status){
    if(timeoutHandler){
        AbsTime t = now();
        if(status == Socket::SOCKET_TIMEOUT) {
            if(idleIn && (Duration(lastIn, t) > idleIn)){
                timeoutHandler->idleIn();
            }
        }
        else if(status == 0 || status == Socket::SOCKET_EOF) {
            handleClosed();
        }
        else {
            lastIn = t;
        }
        if(idleOut && (Duration(lastOut, t) > idleOut)){
            timeoutHandler->idleOut();
        }
    }
}

void Connector::setReadTimeout(uint16_t t){
    idleIn = t * TIME_SEC;//t is in secs
    if(idleIn && (!timeout || idleIn < timeout)){
        timeout = idleIn;
        setSocketTimeout();
    }

}

void Connector::setWriteTimeout(uint16_t t){
    idleOut = t * TIME_SEC;//t is in secs
    if(idleOut && (!timeout || idleOut < timeout)){
        timeout = idleOut;
        setSocketTimeout();
    }
}

void Connector::setSocketTimeout(){
    socket.setTimeout(timeout);
}

void Connector::setTimeoutHandler(TimeoutHandler* handler){
    timeoutHandler = handler;
}

struct Connector::Buff : public AsynchIO::BufferBase {
    Buff() : AsynchIO::BufferBase(new char[65536], 65536) {}    
    ~Buff() { delete [] bytes;}
};

Connector::Writer::Writer() : aio(0), buffer(0), lastEof(0) {}

Connector::Writer::~Writer() { delete buffer; }

void Connector::Writer::setAio(sys::AsynchIO* a) {
    Mutex::ScopedLock l(lock);
    aio = a;
    newBuffer(l);
}

void Connector::Writer::handle(framing::AMQFrame& frame) { 
    Mutex::ScopedLock l(lock);
    frames.push_back(frame);
    if (frame.getEof()) {
        lastEof = frames.size();
        aio->notifyPendingWrite();
    }
    QPID_LOG(trace, "SENT [" << this << "]: " << frame);
}

void Connector::Writer::writeOne(const Mutex::ScopedLock& l) {
    assert(buffer);
    QPID_LOG(trace, "Write buffer " << encode.getPosition()
             << " bytes " << framesEncoded << " frames ");    
    framesEncoded = 0;

    buffer->dataStart = 0;
    buffer->dataCount = encode.getPosition();
    aio->queueWrite(buffer);
    newBuffer(l);
}

void Connector::Writer::newBuffer(const Mutex::ScopedLock&) {
    buffer = aio->getQueuedBuffer();
    if (!buffer) buffer = new Buff();
    encode = framing::Buffer(buffer->bytes, buffer->byteCount);
    framesEncoded = 0;
}

// Called in IO thread.
void Connector::Writer::write(sys::AsynchIO&) {
    Mutex::ScopedLock l(lock);
    assert(buffer);
    for (size_t i = 0; i < lastEof; ++i) {
        AMQFrame& frame = frames[i];
        if (frame.size() > encode.available()) writeOne(l);
        assert(frame.size() <= encode.available());
        frame.encode(encode);
        ++framesEncoded;
    }
    frames.erase(frames.begin(), frames.begin()+lastEof);
    lastEof = 0;
    if (encode.getPosition() > 0) writeOne(l);
}

void Connector::readbuff(AsynchIO& aio, AsynchIO::BufferBase* buff) {
    framing::Buffer in(buff->bytes+buff->dataStart, buff->dataCount);

    AMQFrame frame;
    while(frame.decode(in)){
        QPID_LOG(trace, "RECV [" << this << "]: " << frame);
        input->received(frame);
    }
    // TODO: unreading needs to go away, and when we can cope
    // with multiple sub-buffers in the general buffer scheme, it will
    if (in.available() != 0) {
        // Adjust buffer for used bytes and then "unread them"
        buff->dataStart += buff->dataCount-in.available();
        buff->dataCount = in.available();
        aio.unread(buff);
    } else {
        // Give whole buffer back to aio subsystem
        aio.queueReadBuffer(buff);
    }
}

void Connector::writebuff(AsynchIO& aio_) {
    writer.write(aio_);
}

void Connector::writeDataBlock(const AMQDataBlock& data) {
    AsynchIO::BufferBase* buff = new Buff;
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.size();
    aio->queueWrite(buff);
}

void Connector::eof(AsynchIO&) {
    handleClosed();
}

// TODO: astitcher 20070908 This version of the code can never time out, so the idle processing
// will never be called
void Connector::run(){
    try {
        Dispatcher d(poller);
	
        for (int i = 0; i < 32; i++) {
            aio->queueReadBuffer(new Buff);
        }
	
        aio->start(poller);
        d.run();
        aio->queueForDeletion();
        socket.close();
    } catch (const std::exception& e) {
        QPID_LOG(error, e.what());
        handleClosed();
    }
}


}} // namespace qpid::client
