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
#include <vector>
#include <iostream>
#include <algorithm>

#include "AMQP_HighestVersion.h"
#include "framing/AMQFrame.h"
#include "broker/Broker.h"
#include "broker/Connection.h"
#include "client/Connector.h"
#include "client/Connection.h"

namespace qpid {
namespace broker {

/** Make a copy of a frame body. Inefficient, only intended for tests. */
// TODO aconway 2007-01-29: from should be const, need to fix
// AMQPFrame::encode as const.
framing::AMQFrame copy(framing::AMQFrame& from) {
    framing::Buffer buffer(from.size());
    from.encode(buffer);
    buffer.flip();
    framing::AMQFrame result;
    result.decode(buffer);
    return result;
}

/**
 * A broker that implements client::Connector allowing direct
 * in-process connection of client to broker. Used to write round-trip
 * tests without requiring an external broker process.
 *
 * Also allows you to "snoop" on frames exchanged between client & broker.
 * 
 * see FramingTest::testRequestResponseRoundtrip() for example of use.
 */
class InProcessBroker : public client::Connector {
  public:
    enum Sender {CLIENT,BROKER};
    
    struct Frame : public framing::AMQFrame {
        Frame(Sender e, const AMQFrame& f) : AMQFrame(f), from(e) {}
        bool fromBroker() const { return from == BROKER; }
        bool fromClient() const { return from == CLIENT; }

        template <class MethodType>
        MethodType* asMethod() {
            return dynamic_cast<MethodType*>(getBody().get());
        }

        Sender from;
    };
    typedef std::vector<Frame> Conversation;

    InProcessBroker(framing::ProtocolVersion ver=
                    framing::highestProtocolVersion
    ) :
        Connector(ver),
        protocolInit(ver),
        broker(broker::Broker::create()),
        brokerOut(BROKER, conversation),
        brokerConnection(&brokerOut, *broker),
        clientOut(CLIENT, conversation, &brokerConnection)
    {}

    ~InProcessBroker() { broker->shutdown(); }

    void connect(const std::string& /*host*/, int /*port*/) {}
    void init() { brokerConnection.initiated(&protocolInit); }
    void close() {}

    /** Client's input handler. */
    void setInputHandler(framing::InputHandler* handler) {
        brokerOut.in = handler;
    }

    /** Called by client to send a frame */
    void send(framing::AMQFrame* frame) {
        clientOut.send(frame);
    }

    /** Entire client-broker conversation is recorded here */
    Conversation conversation;

  private:
    /** OutputHandler that forwards data to an InputHandler */
    struct OutputToInputHandler : public sys::ConnectionOutputHandler {
        OutputToInputHandler(
            Sender from_, Conversation& conversation_,
            framing::InputHandler* ih=0
        ) : from(from_), conversation(conversation_), in(ih) {}

        void send(framing::AMQFrame* frame) {
            conversation.push_back(Frame(from, copy(*frame)));
            in->received(frame);
        }

        void close() {}
        
        Sender from;
        Conversation& conversation;
        framing::InputHandler* in;
    };

    framing::ProtocolInitiation protocolInit;
    Broker::shared_ptr  broker;
    OutputToInputHandler brokerOut;
    broker::Connection brokerConnection;
    OutputToInputHandler clientOut;
};

std::ostream& operator<<(
    std::ostream& out, const InProcessBroker::Frame& frame)
{
    return out << (frame.fromBroker()? "BROKER: ":"CLIENT: ") <<
        static_cast<const framing::AMQFrame&>(frame);
}
std::ostream& operator<<(
    std::ostream& out, const InProcessBroker::Conversation& conv)
{
    for (InProcessBroker::Conversation::const_iterator i = conv.begin();
         i != conv.end(); ++i)
    {
        out << *i << std::endl;
    }
    return out;
}


}} // namespace qpid::broker

/** An in-process client+broker all in one. */
class InProcessBrokerClient : public qpid::client::Connection {
  public:
    qpid::broker::InProcessBroker broker;
    qpid::broker::InProcessBroker::Conversation& conversation;
    
    /** Constructor creates broker and opens client connection. */
    InProcessBrokerClient(qpid::framing::ProtocolVersion version=
                          qpid::framing::highestProtocolVersion
    ) : broker(version), conversation(broker.conversation)
    {
        setConnector(broker);
        open("");
    }

    ~InProcessBrokerClient() {}
};

#endif // _tests_InProcessBroker_h
