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

#include "Acceptor.h"

#include "Socket.h"
#include "AsynchIO.h"
#include "Mutex.h"
#include "Thread.h"

#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>
#include <boost/assert.hpp>
#include <queue>
#include <vector>
#include <memory>

namespace qpid {
namespace sys {

class AsynchIOAcceptor : public Acceptor {
    Poller::shared_ptr poller;
    Socket listener;
    int numIOThreads;
    const uint16_t listeningPort;

  public:
    AsynchIOAcceptor(int16_t port, int backlog, int threads);
    ~AsynchIOAcceptor() {}
    void run(ConnectionInputHandlerFactory* factory);
    void shutdown();
        
    uint16_t getPort() const;
    std::string getHost() const;

  private:
    void accepted(Poller::shared_ptr, const Socket&, ConnectionInputHandlerFactory*);
};

Acceptor::shared_ptr Acceptor::create(int16_t port, int backlog, int threads)
{
    return
    	Acceptor::shared_ptr(new AsynchIOAcceptor(port, backlog, threads));
}

AsynchIOAcceptor::AsynchIOAcceptor(int16_t port, int backlog, int threads) :
    poller(new Poller),
    numIOThreads(threads),
    listeningPort(listener.listen(port, backlog))
{}

// Buffer definition
struct Buff : public AsynchIO::BufferBase {
    Buff() :
        AsynchIO::BufferBase(new char[65536], 65536)
    {}
    ~Buff()
    { delete [] bytes;}
};

class AsynchIOHandler : public qpid::sys::ConnectionOutputHandler {
    AsynchIO* aio;
    ConnectionInputHandler* inputHandler;
    std::queue<framing::AMQFrame> frameQueue;
    Mutex frameQueueLock;
    bool frameQueueClosed;
    bool initiated;
    bool readError;
    std::string identifier;

  public:
    AsynchIOHandler() :
        inputHandler(0),
        frameQueueClosed(false),
        initiated(false),
        readError(false)
    {}
	
    ~AsynchIOHandler() {
        if (inputHandler)
            inputHandler->closed();
        delete inputHandler;
    }

    void init(AsynchIO* a, ConnectionInputHandler* h) {
        aio = a;
        inputHandler = h;
        identifier = aio->getSocket().getPeerAddress();
    }

    // Output side
    void send(framing::AMQFrame&);
    void close();
    void activateOutput();

    // Input side
    void readbuff(AsynchIO& aio, AsynchIO::BufferBase* buff);
    void eof(AsynchIO& aio);
    void disconnect(AsynchIO& aio);
	
    // Notifications
    void nobuffs(AsynchIO& aio);
    void idle(AsynchIO& aio);
    void closedSocket(AsynchIO& aio, const Socket& s);
};

void AsynchIOAcceptor::accepted(Poller::shared_ptr poller, const Socket& s, ConnectionInputHandlerFactory* f) {

    AsynchIOHandler* async = new AsynchIOHandler; 
    ConnectionInputHandler* handler = f->create(async, s);
    AsynchIO* aio = new AsynchIO(s,
                                 boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::eof, async, _1),
                                 boost::bind(&AsynchIOHandler::disconnect, async, _1),
                                 boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                                 boost::bind(&AsynchIOHandler::idle, async, _1));
    async->init(aio, handler);

    // Give connection some buffers to use
    for (int i = 0; i < 4; i++) {
        aio->queueReadBuffer(new Buff);
    }
    aio->start(poller);
}


uint16_t AsynchIOAcceptor::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

std::string AsynchIOAcceptor::getHost() const {
    return listener.getSockname();
}

void AsynchIOAcceptor::run(ConnectionInputHandlerFactory* fact) {
    Dispatcher d(poller);
    AsynchAcceptor
        acceptor(listener,
                 boost::bind(&AsynchIOAcceptor::accepted, this, poller, _1, fact));
    acceptor.start(poller);
	
    std::vector<Thread> t(numIOThreads-1);

    // Run n-1 io threads
    for (int i=0; i<numIOThreads-1; ++i)
        t[i] = Thread(d);

    // Run final thread
    d.run();
	
    // Now wait for n-1 io threads to exit
    for (int i=0; i<numIOThreads-1; ++i) {
        t[i].join();
    }
}

void AsynchIOAcceptor::shutdown() {
    poller->shutdown();
}

// Output side
void AsynchIOHandler::send(framing::AMQFrame& frame) {
    // TODO: Need to find out if we are in the callback context,
    // in the callback thread if so we can go further than just queuing the frame
    // to be handled later
    {
	ScopedLock<Mutex> l(frameQueueLock);
	// Ignore anything seen after closing
	if (!frameQueueClosed)
            frameQueue.push(frame);
    }

    // Activate aio for writing here
    aio->notifyPendingWrite();
}

void AsynchIOHandler::close() {
    ScopedLock<Mutex> l(frameQueueLock);
    frameQueueClosed = true;
}

void AsynchIOHandler::activateOutput() {
    aio->notifyPendingWrite();
}

// Input side
void AsynchIOHandler::readbuff(AsynchIO& , AsynchIO::BufferBase* buff) {
    if (readError) {
        return;
    }
    framing::Buffer in(buff->bytes+buff->dataStart, buff->dataCount);
    if(initiated){
        framing::AMQFrame frame;
        try{
            while(frame.decode(in)) {
                QPID_LOG(trace, "RECV [" << identifier << "]: " << frame);
                inputHandler->received(frame);
            }
        }catch(const std::exception& e){
            QPID_LOG(error, e.what());
            readError = true;
            aio->queueWriteClose();
        }
    }else{
        framing::ProtocolInitiation protocolInit;
        if(protocolInit.decode(in)){
            QPID_LOG(debug, "INIT [" << identifier << "]");
            inputHandler->initiated(protocolInit);
            initiated = true;
        }
    }
    // TODO: unreading needs to go away, and when we can cope
    // with multiple sub-buffers in the general buffer scheme, it will
    if (in.available() != 0) {
        // Adjust buffer for used bytes and then "unread them"
        buff->dataStart += buff->dataCount-in.available();
        buff->dataCount = in.available();
        aio->unread(buff);
    } else {
        // Give whole buffer back to aio subsystem
        aio->queueReadBuffer(buff);
    }
}

void AsynchIOHandler::eof(AsynchIO&) {
    QPID_LOG(debug, "DISCONNECTED [" << identifier << "]");
    inputHandler->closed();
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
    ScopedLock<Mutex> l(frameQueueLock);
	
    if (frameQueue.empty()) {
        // At this point we know that we're write idling the connection
        // so tell the input handler to queue any available output:
        inputHandler->doOutput();
        //if still no frames, theres nothing to do:
        if (frameQueue.empty()) return;
    }
	
    do {
        // Try and get a queued buffer if not then construct new one
        AsynchIO::BufferBase* buff = aio->getQueuedBuffer();
        if (!buff)
            buff = new Buff;
        framing::Buffer out(buff->bytes, buff->byteCount);
        int buffUsed = 0;
	
        framing::AMQFrame frame = frameQueue.front();
        int frameSize = frame.size();
        int framesEncoded=0;
        while (frameSize <= int(out.available())) {
            frameQueue.pop();
	
            // Encode output frame	
            frame.encode(out);
            ++framesEncoded;
            buffUsed += frameSize;
            QPID_LOG(trace, "SENT [" << identifier << "]: " << frame);
			
            if (frameQueue.empty()) {
                //if we have run out of frames, allow upper layers to
                //generate more
                if (!frameQueueClosed) {
                    inputHandler->doOutput();
                }
                if (frameQueue.empty()) {                
                    //if there are still no frames, we have no more to
                    //do
                    break;
                }
            }
            frame = frameQueue.front();
            frameSize = frame.size();
        }
        QPID_LOG(trace, "Writing buffer: " << buffUsed << " bytes " << framesEncoded << " frames ");

        // If frame was egregiously large complain
        if (frameSize > buff->byteCount)
            throw framing::ContentTooLargeException(QPID_MSG("Could not write frame, too large for buffer."));
	
        buff->dataCount = buffUsed;
        aio->queueWrite(buff);
    } while (!frameQueue.empty());

    if (frameQueueClosed) {
        aio->queueWriteClose();
    }
}

}} // namespace qpid::sys
