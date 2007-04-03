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
#include <memory>
#include <boost/lexical_cast.hpp>

#include "ConnectionRedirectBody.h"
#include "../framing/ProtocolVersion.h"
#include "../framing/amqp_framing.h"
#include <iostream>
#include "qpid_test_plugin.h"
#include <sstream>
#include <typeinfo>
#include "../QpidError.h"
#include "AMQP_HighestVersion.h"
#include "../framing/AMQRequestBody.h"
#include "../framing/AMQResponseBody.h"
#include "../framing/Requester.h"
#include "../framing/Responder.h"
#include "InProcessBroker.h"
#include "../client/Connection.h"
#include "../client/ClientExchange.h"
#include "../client/ClientQueue.h"

using namespace qpid;
using namespace qpid::framing;
using namespace std;

template <class T>
std::string tostring(const T& x) 
{
    std::ostringstream out;
    out << x;
    return out.str();
}

class FramingTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(FramingTest);
    CPPUNIT_TEST(testBasicQosBody); 
    CPPUNIT_TEST(testConnectionSecureBody); 
    CPPUNIT_TEST(testConnectionRedirectBody);
    CPPUNIT_TEST(testAccessRequestBody);
    CPPUNIT_TEST(testBasicConsumeBody);
    CPPUNIT_TEST(testConnectionRedirectBodyFrame);
    CPPUNIT_TEST(testBasicConsumeOkBodyFrame);
    CPPUNIT_TEST(testRequestBodyFrame);
    CPPUNIT_TEST(testResponseBodyFrame);
    CPPUNIT_TEST(testRequester);
    CPPUNIT_TEST(testResponder);
    CPPUNIT_TEST(testInlineContent);
    CPPUNIT_TEST(testContentReference);
    CPPUNIT_TEST(testContentValidation);
    CPPUNIT_TEST(testRequestResponseRoundtrip);
    CPPUNIT_TEST_SUITE_END();

  private:
    Buffer buffer;
    ProtocolVersion version;
    AMQP_MethodVersionMap versionMap;
    
  public:

    FramingTest() : buffer(1024), version(highestProtocolVersion) {}

    void testBasicQosBody() 
    {
        BasicQosBody in(version, 0xCAFEBABE, 0xABBA, true);
        in.encodeContent(buffer);
        buffer.flip(); 
        BasicQosBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }
    
    void testConnectionSecureBody() 
    {
        std::string s = "security credential";
        ConnectionSecureBody in(version, s);
        in.encodeContent(buffer);
        buffer.flip(); 
        ConnectionSecureBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testConnectionRedirectBody()
    {
        std::string a = "hostA";
        std::string b = "hostB";
        ConnectionRedirectBody in(version, 0, a, b);
        in.encodeContent(buffer);
        buffer.flip(); 
        ConnectionRedirectBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testAccessRequestBody()
    {
        std::string s = "text";
        AccessRequestBody in(version, s, true, false, true, false, true);
        in.encodeContent(buffer);
        buffer.flip(); 
        AccessRequestBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testBasicConsumeBody()
    {
        std::string q = "queue";
        std::string t = "tag";
        BasicConsumeBody in(version, 0, q, t, false, true, false, false,
                            FieldTable());
        in.encodeContent(buffer);
        buffer.flip(); 
        BasicConsumeBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }
    

    void testConnectionRedirectBodyFrame()
    {
        std::string a = "hostA";
        std::string b = "hostB";
        AMQFrame in(version, 999,
                    new ConnectionRedirectBody(version, 0, a, b));
        in.encode(buffer);
        buffer.flip(); 
        AMQFrame out;
        out.decode(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testBasicConsumeOkBodyFrame()
    {
        std::string s = "hostA";
        AMQFrame in(version, 999, new BasicConsumeOkBody(version, 0, s));
        in.encode(buffer);
        buffer.flip(); 
        AMQFrame out;
        for(int i = 0; i < 5; i++){
            out.decode(buffer);
            CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
        }
    }

    void testRequestBodyFrame() {
        std::string testing("testing");
        AMQBody::shared_ptr request(new ChannelOpenBody(version, testing));
        AMQFrame in(version, 999, request);
        in.encode(buffer);
        buffer.flip();
        AMQFrame out;
        out.decode(buffer);
        ChannelOpenBody* decoded =
            dynamic_cast<ChannelOpenBody*>(out.getBody().get());
        CPPUNIT_ASSERT(decoded);
        CPPUNIT_ASSERT_EQUAL(testing, decoded->getOutOfBand());
    }
    
    void testResponseBodyFrame() {
        AMQBody::shared_ptr response(new ChannelOkBody(version));
        AMQFrame in(version, 999, response);
        in.encode(buffer);
        buffer.flip();
        AMQFrame out;
        out.decode(buffer);
        ChannelOkBody* decoded =
            dynamic_cast<ChannelOkBody*>(out.getBody().get());
        CPPUNIT_ASSERT(decoded);
    }

    void testInlineContent() {        
        Content content(INLINE, "MyData");
        CPPUNIT_ASSERT(content.isInline());
        content.encode(buffer);
        buffer.flip();
        Content recovered;
        recovered.decode(buffer);
        CPPUNIT_ASSERT(recovered.isInline());
        CPPUNIT_ASSERT_EQUAL(content.getValue(), recovered.getValue());
    }

    void testContentReference() {        
        Content content(REFERENCE, "MyRef");
        CPPUNIT_ASSERT(content.isReference());
        content.encode(buffer);
        buffer.flip();
        Content recovered;
        recovered.decode(buffer);
        CPPUNIT_ASSERT(recovered.isReference());
        CPPUNIT_ASSERT_EQUAL(content.getValue(), recovered.getValue());
    }

    void testContentValidation() {
        try {
            Content content(REFERENCE, "");
            CPPUNIT_ASSERT(false);//fail, expected exception
        } catch (QpidError& e) {
            CPPUNIT_ASSERT_EQUAL(FRAMING_ERROR, e.code);
            CPPUNIT_ASSERT_EQUAL(string("Reference cannot be empty"), e.msg);
        }
        
        try {
            Content content(2, "Blah");
            CPPUNIT_ASSERT(false);//fail, expected exception
        } catch (QpidError& e) {
            CPPUNIT_ASSERT_EQUAL(FRAMING_ERROR, e.code);
            CPPUNIT_ASSERT_EQUAL(string("Invalid discriminator: 2"), e.msg);
        }
        
        try {
            buffer.putOctet(2);
            buffer.putLongString("blah, blah");
            buffer.flip();
            Content content;
            content.decode(buffer);
            CPPUNIT_ASSERT(false);//fail, expected exception
        } catch (QpidError& e) {
            CPPUNIT_ASSERT_EQUAL(FRAMING_ERROR, e.code);
            CPPUNIT_ASSERT_EQUAL(string("Invalid discriminator: 2"), e.msg);
        }
        
    }

    void testRequester() {
        Requester r;
        AMQRequestBody::Data q;
        AMQResponseBody::Data p;

        r.sending(q);
        CPPUNIT_ASSERT_EQUAL(RequestId(1), q.requestId);
        CPPUNIT_ASSERT_EQUAL(ResponseId(0), q.responseMark);

        r.sending(q);
        CPPUNIT_ASSERT_EQUAL(RequestId(2), q.requestId);
        CPPUNIT_ASSERT_EQUAL(ResponseId(0), q.responseMark);

        // Now process a response
        p.responseId = 1;
        p.requestId = 2;
        r.processed(AMQResponseBody::Data(1, 2));

        r.sending(q);
        CPPUNIT_ASSERT_EQUAL(RequestId(3), q.requestId);
        CPPUNIT_ASSERT_EQUAL(ResponseId(1), q.responseMark);
        
        try {
            r.processed(p);     // Already processed this response.
            CPPUNIT_FAIL("Expected exception");
        } catch (...) {}

        try {
            p.requestId = 50;
            r.processed(p);     // No such request
            CPPUNIT_FAIL("Expected exception");
        } catch (...) {}

        r.sending(q);           // reqId=4
        r.sending(q);           // reqId=5
        r.sending(q);           // reqId=6
        p.responseId++;
        p.requestId = 4;
        p.batchOffset = 2;
        r.processed(p);
        r.sending(q);
        CPPUNIT_ASSERT_EQUAL(RequestId(7), q.requestId);
        CPPUNIT_ASSERT_EQUAL(ResponseId(2), q.responseMark);

        p.responseId++;
        p.requestId = 1;        // Out of order
        p.batchOffset = 0;
        r.processed(p);
        r.sending(q);
        CPPUNIT_ASSERT_EQUAL(RequestId(8), q.requestId);
        CPPUNIT_ASSERT_EQUAL(ResponseId(3), q.responseMark);
    }

    void testResponder() {
        Responder r;
        AMQRequestBody::Data q;
        AMQResponseBody::Data p;

        q.requestId = 1;
        q.responseMark = 0;
        r.received(q);
        p.requestId = q.requestId;
        r.sending(p);
        CPPUNIT_ASSERT_EQUAL(ResponseId(1), p.responseId);
        CPPUNIT_ASSERT_EQUAL(RequestId(1), p.requestId);
        CPPUNIT_ASSERT_EQUAL(0U,   p.batchOffset);
        CPPUNIT_ASSERT_EQUAL(ResponseId(0), r.getResponseMark());

        q.requestId++;
        q.responseMark = 1;
        r.received(q);
        r.sending(p);
        CPPUNIT_ASSERT_EQUAL(ResponseId(2), p.responseId);
        CPPUNIT_ASSERT_EQUAL(0U,   p.batchOffset);
        CPPUNIT_ASSERT_EQUAL(ResponseId(1), r.getResponseMark());

        try {
            // Response mark higher any request ID sent.
            q.responseMark = 3;
            r.received(q);
        } catch(...) {}

        try {
            // Response mark lower than previous response mark.
            q.responseMark = 0;
            r.received(q);
        } catch(...) {}

        // TODO aconway 2007-01-14: Test for batching when supported.
        
    }

    // expect may contain null chars so use string(ptr,size) constructor
    // Use sizeof(expect)-1 to strip the trailing null.
#define ASSERT_FRAME(expect, frame) \
    CPPUNIT_ASSERT_EQUAL(string(expect, sizeof(expect)-1), boost::lexical_cast<string>(frame))

    void testRequestResponseRoundtrip() {
        broker::InProcessBroker ibroker(version);
        client::Connection clientConnection;
        clientConnection.setConnector(ibroker);
        clientConnection.open("");
        client::Channel c;
        clientConnection.openChannel(c);

        client::Exchange exchange(
            "MyExchange", client::Exchange::TOPIC_EXCHANGE);
        client::Queue queue("MyQueue", true);
        c.declareExchange(exchange);
        c.declareQueue(queue);
        c.bind(exchange, queue, "MyTopic", framing::FieldTable());
        broker::InProcessBroker::Conversation::const_iterator i = ibroker.conversation.begin();
        ASSERT_FRAME("BROKER: Frame[channel=0; request(id=1,mark=0): ConnectionStart: versionMajor=0; versionMinor=9; serverProperties={}; mechanisms=PLAIN; locales=en_US]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=0; response(id=1,request=1,batch=0): ConnectionStartOk: clientProperties={}; mechanism=PLAIN; response=\000guest\000guest; locale=en_US]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=0; request(id=2,mark=1): ConnectionTune: channelMax=100; frameMax=65536; heartbeat=0]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=0; response(id=2,request=2,batch=0): ConnectionTuneOk: channelMax=100; frameMax=65536; heartbeat=0]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=0; request(id=1,mark=0): ConnectionOpen: virtualHost=/; capabilities=; insist=1]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=0; response(id=1,request=1,batch=0): ConnectionOpenOk: knownHosts=]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; request(id=1,mark=0): ChannelOpen: outOfBand=]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; response(id=1,request=1,batch=0): ChannelOpenOk: channelId=]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; request(id=2,mark=1): ExchangeDeclare: ticket=0; exchange=MyExchange; type=topic; passive=0; durable=0; autoDelete=0; internal=0; nowait=0; arguments={}]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; response(id=2,request=2,batch=0): ExchangeDeclareOk: ]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; request(id=3,mark=2): QueueDeclare: ticket=0; queue=MyQueue; passive=0; durable=0; exclusive=1; autoDelete=1; nowait=0; arguments={}]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; response(id=3,request=3,batch=0): QueueDeclareOk: queue=MyQueue; messageCount=0; consumerCount=0]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; request(id=4,mark=3): QueueBind: ticket=0; queue=MyQueue; exchange=MyExchange; routingKey=MyTopic; nowait=0; arguments={}]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; response(id=4,request=4,batch=0): QueueBindOk: ]", *i++);
    }
 };


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(FramingTest);



