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

using qpid::sys::ConnectionInputHandler;

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

    struct Task {
        AMQFrame frame;
        bool doOutput;

        Task() : doOutput(true) {}
        Task(AMQFrame& f) : frame(f), doOutput(false) {}
    };

    /** Simulate the network thread of a peer with a queue and a thread.
     * With setInputHandler(0) drops frames simulating network packet loss.
     */
    class NetworkQueue : public sys::Runnable
    {
      public:
        NetworkQueue(const char* r) : inputHandler(0), connectionHandler(0), receiver(r) {
            thread=sys::Thread(this);
        }

        ~NetworkQueue() { 
            queue.close();
            thread.join();
        }

        void push(AMQFrame& f) { queue.push(f); }
        void activateOutput() { queue.push(Task()); }

        void run() {
            try {
                while(true) {
                    Task t = queue.pop();
                    if (t.doOutput) {
                        if (connectionHandler) {
                            while (connectionHandler->doOutput());
                        }
                    } else {
                        if (inputHandler) { 
                            QPID_LOG(debug, QPID_MSG(receiver << " RECV: " << t.frame));
                            inputHandler->handle(t.frame);
                        }
                        else 
                            QPID_LOG(debug, QPID_MSG(receiver << " DROP: " << t.frame));
                    }
                }
            }
            catch (const std::exception& e) {
                QPID_LOG(debug, QPID_MSG(receiver << " Terminated: " << e.what()));
                return;
            }
        }
        
        void setConnectionInputHandler(ConnectionInputHandler* h) {
            Lock l(lock);
            inputHandler = h;
            connectionHandler = h;
        }

        void setInputHandler(FrameHandler* h) {
            Lock l(lock);
            inputHandler = h;
            connectionHandler = 0;
        }
        
      private:
        sys::Mutex lock;
        sys::BlockingQueue<Task> queue;
        sys::Thread thread;
        FrameHandler* inputHandler;
        ConnectionInputHandler* connectionHandler;
        const char* const receiver;
    };

    struct InProcessHandler : public sys::ConnectionOutputHandler {
        Sender from;
        NetworkQueue queue;
        const char* const sender;
        NetworkQueue* reverseQueue;

        InProcessHandler(Sender s)
            : from(s),
              queue(from==CLIENT? "BROKER" : "CLIENT"),
              sender(from==BROKER? "BROKER" : "CLIENT"),
              reverseQueue(0)
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

        void activateOutput() { 
            if (reverseQueue) reverseQueue->activateOutput(); 
        }
    };


    InProcessConnector(shared_ptr<broker::Broker> b=broker::Broker::create(),
                       framing::ProtocolVersion v=framing::ProtocolVersion()) :
        Connector(v),
        protocolInit(v),
        broker(b),
        brokerOut(BROKER),
        brokerConnection(&brokerOut, *broker),
        clientOut(CLIENT),
        isClosed(false)
    {
        clientOut.queue.setConnectionInputHandler(&brokerConnection);
        brokerOut.reverseQueue = &clientOut.queue;
        clientOut.reverseQueue = &brokerOut.queue;
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
        clientOut.queue.setConnectionInputHandler(0);
    }

    shared_ptr<broker::Broker> getBroker() { return broker; }
    
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
    /** Connect to an existing broker */
    InProcessConnection(shared_ptr<broker::Broker> b=broker::Broker::create())
        : client::Connection(
            shared_ptr<client::Connector>(new InProcessConnector(b)))
    { open(""); }

    InProcessConnector& getConnector() {
        return static_cast<InProcessConnector&>(*impl->getConnector());
    }
    
    /** Simulate disconnected network connection. */
    void disconnect() { getConnector().close(); }
    
    /** Discard frames, simulates lost network traffic. */
    void discard() { getConnector().discard(); }

    shared_ptr<broker::Broker> getBroker() {
        return getConnector().getBroker();
    }
};

} // namespace qpid

#endif // _tests_InProcessBroker_h
