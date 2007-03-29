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
#include "AMQP_HighestVersion.h"
#include "framing/AMQFrame.h"
#include "broker/Broker.h"
#include "broker/Connection.h"
#include "client/Connector.h"
#include "client/Connection.h"

#include <vector>
#include <iostream>
#include <algorithm>


namespace qpid {
namespace broker {

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

    /** A frame tagged with the sender */
    struct TaggedFrame {
        TaggedFrame(Sender e, framing::AMQFrame* f) : frame(f), sender(e) {}
        bool fromBroker() const { return sender == BROKER; }
        bool fromClient() const { return sender == CLIENT; }

        template <class MethodType>
        MethodType* asMethod() {
            return dynamic_cast<MethodType*>(frame->getBody().get());
        }
        shared_ptr<framing::AMQFrame> frame;
        Sender sender;
    };
    
    typedef std::vector<TaggedFrame> Conversation;

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
    void init() { brokerConnection.initiated(protocolInit); }
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
            Sender sender_, Conversation& conversation_,
            framing::InputHandler* ih=0
        ) : sender(sender_), conversation(conversation_), in(ih) {}

        void send(framing::AMQFrame* frame) {
            conversation.push_back(TaggedFrame(sender, frame));
            in->received(frame);
        }

        void close() {}
        
        Sender sender;
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
    std::ostream& out, const InProcessBroker::TaggedFrame& frame)
{
    return out << (frame.fromBroker()? "BROKER: ":"CLIENT: ") << frame;
}

std::ostream& operator<<(
    std::ostream& out, const InProcessBroker::Conversation& conv)
{    
    copy(conv.begin(), conv.end(),
         std::ostream_iterator<InProcessBroker::TaggedFrame>(out, "\n"));
    return out;
}

} // namespace broker


namespace client {
/** An in-process client+broker all in one. */
class InProcessBrokerClient : public client::Connection {
  public:
    broker::InProcessBroker broker;
    broker::InProcessBroker::Conversation& conversation;
    
    /** Constructor creates broker and opens client connection. */
    InProcessBrokerClient(
        u_int32_t max_frame_size=65536,
        framing::ProtocolVersion version= framing::highestProtocolVersion
    ) : client::Connection(false, max_frame_size, version),
        broker(version),
        conversation(broker.conversation)
    {
        setConnector(broker);
        open("");
    }
};


}} // namespace qpid::client


#endif // _tests_InProcessBroker_h
