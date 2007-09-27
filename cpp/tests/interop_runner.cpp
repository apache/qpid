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

#include <CommonOptions.h>
#include <Connection.h>
#include <ClientChannel.h>
#include <ClientExchange.h>
#include <ClientQueue.h>
#include <Exception.h>
#include <MessageListener.h>
#include <QpidError.h>
#include <sys/Thread.h>
#include <sys/Time.h>
#include <iostream>
#include <memory>
#include "BasicP2PTest.h"
#include "BasicPubSubTest.h"
#include "P2PMessageSizeTest.h"
#include "PubSubMessageSizeTest.h"
#include "TestCase.h"
#include <boost/ptr_container/ptr_map.hpp>

/**
 * Framework for interop tests.
 * 
 * [see http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification for details].
 */

using namespace qpid::client;
using namespace qpid::sys;
using qpid::TestCase;
using qpid::TestOptions;
using qpid::framing::FieldTable;
using std::string;

class DummyRun : public TestCase
{
public:
    DummyRun() {}
    void assign(const std::string&, FieldTable&, TestOptions&) {}
    void start() {}
    void stop() {}
    void report(qpid::client::Message&) {}
};

string parse_next_word(const string& input, const string& delims, string::size_type& position);

/**
 */
class Listener : public MessageListener, private Runnable{    
    typedef boost::ptr_map<std::string, TestCase> TestMap;

    Channel& channel;
    TestOptions& options;
    TestMap tests;
    const string name;
    const string topic;
    TestMap::iterator test;
    std::auto_ptr<Thread> runner;
    string reportTo;
    string reportCorrelator;    

    void shutdown();
    bool invite(const string& name);
    void run();

    void sendResponse(Message& response, string replyTo);
    void sendResponse(Message& response, Message& request);
    void sendSimpleResponse(const string& type, Message& request);
    void sendReport();
public:
    Listener(Channel& channel, TestOptions& options);
    void received(Message& msg);
    void bindAndConsume();
    void registerTest(std::string name, TestCase* test);
};

/**
 * TODO: Add clock synching. CLOCK_SYNCH command is currently ignored.
 */
int main(int argc, char** argv){
    TestOptions options;
    options.parse(argc, argv);

    if (options.help) {
        options.usage();
    } else {
        try{
            Connection connection(options.trace);
            connection.open(options.broker, options.port, "guest", "guest", options.virtualhost);
            
            Channel channel;
            connection.openChannel(&channel);
            
            Listener listener(channel, options);
            listener.registerTest("TC1_DummyRun", new DummyRun());
            listener.registerTest("TC2_BasicP2P", new qpid::BasicP2PTest());
            listener.registerTest("TC3_BasicPubSub", new qpid::BasicPubSubTest());
            listener.registerTest("TC4_P2PMessageSize", new qpid::P2PMessageSizeTest());
            listener.registerTest("TC5_PubSubMessageSize", new qpid::PubSubMessageSizeTest());

            listener.bindAndConsume();
            
            channel.run();
            connection.close();
        } catch(qpid::Exception error) {
            std::cout << error.what() << std::endl;
        }
    }
}

Listener::Listener(Channel& _channel, TestOptions& _options) : channel(_channel), options(_options), name(options.clientid), topic("iop.control." + name)
{}

void Listener::registerTest(std::string name, TestCase* test)
{
    tests.insert(name, test);
}

void Listener::bindAndConsume()
{
    Queue control(name, true);
    channel.declareQueue(control);
    qpid::framing::FieldTable bindArgs;
    //replace these separate binds with a wildcard once that is supported on java broker
    channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, control, "iop.control", bindArgs);
    channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, control, topic, bindArgs);
    
    std::string tag;
    channel.consume(control, tag, this);
}

void Listener::sendSimpleResponse(const string& type, Message& request)
{
    Message response;
    response.getHeaders().setString("CONTROL_TYPE", type);
    response.getHeaders().setString("CLIENT_NAME", name);
    response.getHeaders().setString("CLIENT_PRIVATE_CONTROL_KEY", topic);
    response.setCorrelationId(request.getCorrelationId());
    sendResponse(response, request);
}

void Listener::sendResponse(Message& response, Message& request)
{
    sendResponse(response, request.getReplyTo()); 
}

void Listener::sendResponse(Message& response, string replyTo)
{
    //Exchange and routing key need to be extracted from the reply-to
    //field. Format is assumed to be:
    //
    //    <exchange type>://<exchange name>/<routing key>?<options>
    //
    //and all we need is the exchange name and routing key
    // 
    if (replyTo.empty()) throw qpid::Exception("Reply address not set!"); 
    const string delims(":/?=");

    string::size_type start = replyTo.find(':');//skip exchange type
    string exchange = parse_next_word(replyTo, delims, start);
    string routingKey = parse_next_word(replyTo, delims, start);
    channel.publish(response, exchange, routingKey);
}

void Listener::received(Message& message)
{
    std::string type(message.getHeaders().getString("CONTROL_TYPE"));

    if (type == "INVITE") {
        std::string name(message.getHeaders().getString("TEST_NAME"));
        if (name.empty() || invite(name)) {
            sendSimpleResponse("ENLIST", message);
  	    //std::cout << "Enlisting in test '" << name << "'" << std::endl;
        } else {
            std::cout << "Can't take part in '" << name << "'" << std::endl;
        }
    } else if (type == "ASSIGN_ROLE") {        
        //std::cout << "Got role assignment request for '" << name << "'" << std::endl;
        test->assign(message.getHeaders().getString("ROLE"), message.getHeaders(), options);
        sendSimpleResponse("ACCEPT_ROLE", message);
    } else if (type == "START") {        
        reportTo = message.getReplyTo();
        reportCorrelator = message.getCorrelationId();
        runner = std::auto_ptr<Thread>(new Thread(this));
    } else if (type == "STATUS_REQUEST") {
        reportTo = message.getReplyTo();
        reportCorrelator = message.getCorrelationId();
        test->stop();
        sendReport();
    } else if (type == "TERMINATE") {
        if (test != tests.end()) test->stop();
        shutdown();
    } else if (type == "CLOCK_SYNCH") {
        // Just ignore for now.
    } else {        
        std::cerr <<"ERROR!: Received unknown control message: " << type << std::endl;
        shutdown();
    }
}

void Listener::shutdown()
{
    channel.close();
}

bool Listener::invite(const string& name)
{
    test = tests.find(name);
    return test != tests.end();
}

void Listener::run()
{
    //NB: this method will be called in its own thread 
    //start test and when start returns...
    test->start();
    sendReport();
}

void Listener::sendReport()
{
    Message report;
    report.getHeaders().setString("CONTROL_TYPE", "REPORT");
    test->report(report);
    report.setCorrelationId(reportCorrelator);
    sendResponse(report, reportTo);
}

string parse_next_word(const string& input, const string& delims, string::size_type& position)
{
    string::size_type start = input.find_first_not_of(delims, position);
    if (start == string::npos) {
        return "";
    } else {
        string::size_type end = input.find_first_of(delims, start);
        if (end == string::npos) {
            end = input.length();
        }
        position = end;
        return input.substr(start, end - start);
    }
}
