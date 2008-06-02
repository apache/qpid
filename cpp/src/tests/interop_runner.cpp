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

#include "qpid/Options.h"
#include "qpid/ptr_map.h"
#include "qpid/Exception.h"
#include "qpid/client/Channel.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionOptions.h"
#include "qpid/client/Exchange.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/Queue.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Time.h"
#include <iostream>
#include <memory>
#include "BasicP2PTest.h"
#include "BasicPubSubTest.h"
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
using qpid::framing::FieldTable;
using qpid::framing::ReplyTo;
using namespace std;

class DummyRun : public TestCase
{
public:
    DummyRun() {}
    void assign(const string&, FieldTable&, ConnectionOptions&) {}
    void start() {}
    void stop() {}
    void report(qpid::client::Message&) {}
};

string parse_next_word(const string& input, const string& delims, string::size_type& position);

/**
 */
class Listener : public MessageListener, private Runnable{    
    typedef boost::ptr_map<string, TestCase> TestMap;

    Channel& channel;
    ConnectionOptions& options;
    TestMap tests;
    const string name;
    const string topic;
    TestCase* test;
    auto_ptr<Thread> runner;
    ReplyTo reportTo;
    string reportCorrelator;    

    void shutdown();
    bool invite(const string& name);
    void run();

    void sendResponse(Message& response, ReplyTo replyTo);
    void sendResponse(Message& response, Message& request);
    void sendSimpleResponse(const string& type, Message& request);
    void sendReport();
public:
    Listener(Channel& channel, ConnectionOptions& options);
    void received(Message& msg);
    void bindAndConsume();
    void registerTest(string name, TestCase* test);
};

struct TestSettings : ConnectionOptions
{
    bool help;

    TestSettings() : help(false)
    {
        addOptions()
            ("help", qpid::optValue(help), "print this usage statement");
    }
};

int main(int argc, char** argv) {
    try {
        TestSettings options;
        options.parse(argc, argv);
        if (options.help) {
            cout << options;
        } else {
            Connection connection;
            connection.open(options.host, options.port, "guest", "guest", options.virtualhost);
            
            Channel channel;
            connection.openChannel(channel);
            
            Listener listener(channel, options);
            listener.registerTest("TC1_DummyRun", new DummyRun());
            listener.registerTest("TC2_BasicP2P", new qpid::BasicP2PTest());
            listener.registerTest("TC3_BasicPubSub", new qpid::BasicPubSubTest());
            
            listener.bindAndConsume();
            
            channel.run();
            connection.close();
        }
    } catch(const exception& error) {
        cout << error.what() << endl << "Type " << argv[0] << " --help for help" << endl;
    }
}

Listener::Listener(Channel& _channel, ConnectionOptions& _options) : channel(_channel), options(_options), name(options.clientid), topic("iop.control." + name)
{}

void Listener::registerTest(string name, TestCase* test)
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
    
    string tag;
    channel.consume(control, tag, this);
}

void Listener::sendSimpleResponse(const string& type, Message& request)
{
    Message response;
    response.getHeaders().setString("CONTROL_TYPE", type);
    response.getHeaders().setString("CLIENT_NAME", name);
    response.getHeaders().setString("CLIENT_PRIVATE_CONTROL_KEY", topic);
    response.getMessageProperties().setCorrelationId(request.getMessageProperties().getCorrelationId());
    sendResponse(response, request);
}

void Listener::sendResponse(Message& response, Message& request)
{
    sendResponse(response, request.getMessageProperties().getReplyTo()); 
}

void Listener::sendResponse(Message& response, ReplyTo replyTo)
{
    string exchange = replyTo.getExchange();
    string routingKey = replyTo.getRoutingKey();
    channel.publish(response, exchange, routingKey);
}

void Listener::received(Message& message)
{
    string type(message.getHeaders().getString("CONTROL_TYPE"));

    if (type == "INVITE") {
        string name(message.getHeaders().getString("TEST_NAME"));
        if (name.empty() || invite(name)) {
            sendSimpleResponse("ENLIST", message);
        } else {
            cout << "Can't take part in '" << name << "'" << endl;
        }
    } else if (type == "ASSIGN_ROLE") {        
        test->assign(message.getHeaders().getString("ROLE"), message.getHeaders(), options);
        sendSimpleResponse("ACCEPT_ROLE", message);
    } else if (type == "START") {        
        reportTo = message.getMessageProperties().getReplyTo();
        reportCorrelator = message.getMessageProperties().getCorrelationId();
        runner = auto_ptr<Thread>(new Thread(this));
    } else if (type == "STATUS_REQUEST") {
        reportTo = message.getMessageProperties().getReplyTo();
        reportCorrelator = message.getMessageProperties().getCorrelationId();
        test->stop();
        sendReport();
    } else if (type == "TERMINATE") {
        if (test) test->stop();
        shutdown();
    } else {        
        cerr <<"ERROR!: Received unknown control message: " << type << endl;
        shutdown();
    }
}

void Listener::shutdown()
{
    channel.close();
}

bool Listener::invite(const string& name)
{
    TestMap::iterator i = tests.find(name);
    test = (i != tests.end()) ? qpid::ptr_map_ptr(i) : 0;
    return test;
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
    report.getMessageProperties().setCorrelationId(reportCorrelator);
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
