#ifndef _tests_InProcessBroker_h
#define _tests_InProcessBroker_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Connection.h"
#include "qpid/client/Connector.h"
#include "qpid/client/Connection.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/BlockingQueue.h"
#include "qpid/shared_ptr.h"

#include <vector>
#include <iostream>
#include <algorithm>


namespace qpid {


/**
 * A client::Connector that connects directly to an in-process broker.
 * Also allows you to "snoop" on frames exchanged between client & broker.
 * 
 * see FramingTest::testRequestResponseRoundtrip() for example of use.
 */
class InProcessConnector :
        public client::Connector
{
  public:
    typedef sys::Mutex Mutex;
    typedef Mutex::ScopedLock Lock;
    typedef framing::FrameHandler FrameHandler;
    typedef framing::AMQFrame AMQFrame;

    enum Sender {CLIENT,BROKER};

    /** Simulate the network thread of a peer with a queue and a thread.
     * With setInputHandler(0) drops frames simulating network packet loss.
     */
    class NetworkQueue : public sys::Runnable
    {
      public:
        NetworkQueue(const char* r) : inputHandler(0), receiver(r) {
            thread=sys::Thread(this);
        }

        ~NetworkQueue() { 
            queue.close();
            thread.join();
        }

        void push(AMQFrame& f) { queue.push(f); }

        void run() {
            try {
                while(true) {
                    AMQFrame f = queue.pop();
                    if (inputHandler) { 
                        QPID_LOG(debug, QPID_MSG(receiver << " RECV: " << f));
                        inputHandler->handle(f);
                    }
                    else 
                        QPID_LOG(debug, QPID_MSG(receiver << " DROP: " << f));
                }
            }
            catch (const sys::QueueClosed&) {
                return;
            }
        }
        
        void setInputHandler(FrameHandler* h) {
            Lock l(lock);
            inputHandler = h;
        }
        
      private:
        sys::Mutex lock;
        sys::BlockingQueue<AMQFrame> queue;
        sys::Thread thread;
        FrameHandler* inputHandler;
        const char* const receiver;
    };

    struct InProcessHandler : public sys::ConnectionOutputHandler {
        Sender from;
        NetworkQueue queue;
        const char* const sender;

        InProcessHandler(Sender s)
            : from(s),
              queue(from==CLIENT? "BROKER" : "CLIENT"),
              sender(from==BROKER? "BROKER" : "CLIENT")
        {}

        ~InProcessHandler() {  }
        
        void send(AMQFrame& f) {
            QPID_LOG(debug, QPID_MSG(sender << " SENT: " << f));
            queue.push(f);
        }
        
        void close() {
            // Do not shut down the queue here, we may be in
            // the queue's dispatch thread. 
        }
    };

    InProcessConnector(shared_ptr<broker::Broker> b,
                       framing::ProtocolVersion v=framing::ProtocolVersion()) :
        Connector(v),
        protocolInit(v),
        broker(b),
        brokerOut(BROKER),
        brokerConnection(&brokerOut, *broker),
        clientOut(CLIENT),
        isClosed(false)
    {
        clientOut.queue.setInputHandler(&brokerConnection);
    }

    ~InProcessConnector() {
        close();
        
    }

    void connect(const std::string& /*host*/, int /*port*/) {}

    void init() { brokerConnection.initiated(protocolInit); }

    void close() {
        if (!isClosed) {
            isClosed = true;
            brokerOut.close();
            clientOut.close();
            brokerConnection.closed();
        }
    }

    /** Client's input handler. */
    void setInputHandler(framing::InputHandler* handler) {
        brokerOut.queue.setInputHandler(handler);
    }

    /** Called by client to send a frame */
    void send(framing::AMQFrame& frame) {
        clientOut.handle(frame);
    }

    /** Sliently discard frames sent by either party, lost network traffic. */
    void discard() {
        brokerOut.queue.setInputHandler(0);
        clientOut.queue.setInputHandler(0);
    }

  private:
    sys::Mutex lock;
    framing::ProtocolInitiation protocolInit;
    shared_ptr<broker::Broker>  broker;
    InProcessHandler brokerOut;
    broker::Connection brokerConnection;
    InProcessHandler  clientOut;
    bool isClosed;
};

struct InProcessConnection : public client::Connection {
    InProcessConnection(shared_ptr<broker::Broker> b)
        : client::Connection(
            shared_ptr<client::Connector>(
                new InProcessConnector(b)))
    {
        open("");
    }

    ~InProcessConnection() { }
    
    /** Simulate disconnected network connection. */
    void disconnect() { impl->getConnector()->close(); }
    
    /** Sliently discard frames sent by either party, lost network traffic. */
    void discard() {
        dynamic_pointer_cast<InProcessConnector>(
            impl->getConnector())->discard();
    }
};   

/** A connector with its own broker */
struct InProcessBroker : public InProcessConnector {
    InProcessBroker() : InProcessConnector(broker::Broker::create()) {}
};

} // namespace qpid

#endif // _tests_InProcessBroker_h
