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
#include "qpid/framing/ConnectionRedirectBody.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/amqp_framing.h"
#include <iostream>
#include "qpid_test_plugin.h"
#include <sstream>
#include <typeinfo>
#include "qpid/QpidError.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "InProcessBroker.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Connector.h"
#include "qpid/client/ClientExchange.h"
#include "qpid/client/ClientQueue.h"
#include "qpid/framing/BasicGetOkBody.h"
#include <memory>
#include <boost/lexical_cast.hpp>
#include <boost/bind.hpp>

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
        ConnectionRedirectBody in(version, a, b);
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
                    new ConnectionRedirectBody(version, a, b));
        in.encode(buffer);
        buffer.flip(); 
        AMQFrame out;
        out.decode(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testBasicConsumeOkBodyFrame()
    {
        std::string s = "hostA";
        AMQFrame in(version, 999, new BasicConsumeOkBody(version, s));
        in.encode(buffer);
        buffer.flip(); 
        AMQFrame out;
        for(int i = 0; i < 5; i++){
            out.decode(buffer);
            CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
        }
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

    // expect may contain null chars so use string(ptr,size) constructor
    // Use sizeof(expect)-1 to strip the trailing null.
#define ASSERT_FRAME(expect, frame) \
    CPPUNIT_ASSERT_EQUAL(string(expect, sizeof(expect)-1), boost::lexical_cast<string>(frame))

    void testRequestResponseRoundtrip() {
        boost::shared_ptr<broker::InProcessBroker> ibroker(new broker::InProcessBroker(version));
        client::Connection clientConnection(boost::static_pointer_cast<client::Connector>(ibroker));
        clientConnection.open("");
        client::Channel c;
        clientConnection.openChannel(c);

        client::Exchange exchange(
            "MyExchange", client::Exchange::TOPIC_EXCHANGE);
        client::Queue queue("MyQueue", true);
        c.declareExchange(exchange);
        c.declareQueue(queue);
        c.bind(exchange, queue, "MyTopic", framing::FieldTable());
        c.close();
        clientConnection.close();
        broker::InProcessBroker::Conversation::const_iterator i = ibroker->conversation.begin();
        ASSERT_FRAME("BROKER: Frame[channel=0; ConnectionStart: versionMajor=0; versionMinor=10; serverProperties={}; mechanisms=PLAIN; locales=en_US]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=0; ConnectionStartOk: clientProperties={}; mechanism=PLAIN; response=\000guest\000guest; locale=en_US]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=0; ConnectionTune: channelMax=32767; frameMax=65536; heartbeat=0]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=0; ConnectionTuneOk: channelMax=32767; frameMax=65536; heartbeat=0]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=0; ConnectionOpen: virtualHost=/; capabilities=; insist=1]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=0; ConnectionOpenOk: knownHosts=]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; ChannelOpen: outOfBand=]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; ChannelOpenOk: ]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; ExchangeDeclare: ticket=0; exchange=MyExchange; type=topic; alternateExchange=; passive=0; durable=0; autoDelete=0; arguments={}]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; ExecutionFlush: ]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; ExecutionComplete: cumulativeExecutionMark=1; rangedExecutionSet={}]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; QueueDeclare: ticket=0; queue=MyQueue; alternateExchange=; passive=0; durable=0; exclusive=1; autoDelete=1; nowait=0; arguments={}]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; QueueDeclareOk: queue=MyQueue; messageCount=0; consumerCount=0]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; ExecutionFlush: ]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; ExecutionComplete: cumulativeExecutionMark=2; rangedExecutionSet={}]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; QueueBind: ticket=0; queue=MyQueue; exchange=MyExchange; routingKey=MyTopic; arguments={}]", *i++);
        ASSERT_FRAME("CLIENT: Frame[channel=1; ExecutionFlush: ]", *i++);
        ASSERT_FRAME("BROKER: Frame[channel=1; ExecutionComplete: cumulativeExecutionMark=3; rangedExecutionSet={}]", *i++);
    }
 };


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(FramingTest);



