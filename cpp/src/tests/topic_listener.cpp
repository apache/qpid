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

/**
 * This file provides one half of a test and example of a pub-sub
 * style of interaction. See topic_publisher.cpp for the other half,
 * in which the logic for publishing is defined.
 * 
 * This file contains the listener logic. A listener will subscribe to
 * a logical 'topic'. It will count the number of messages it receives
 * and the time elapsed between the first one and the last one. It
 * recognises two types of 'special' message that tell it to (a) send
 * a report containing this information, (b) shutdown (i.e. stop
 * listening).
 */

#include "TestOptions.h"
#include "qpid/client/Channel.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Exchange.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/Queue.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/FieldValue.h"
#include <iostream>
#include <sstream>

using namespace qpid;
using namespace qpid::client;
using namespace qpid::sys;
using namespace qpid::framing;
using namespace std;

/**
 * A message listener implementation in which the runtime logic is
 * defined.
 */
class Listener : public MessageListener{    
    Channel* const channel;
    const string responseQueue;
    const bool transactional;
    bool init;
    int count;
    AbsTime start;
    
    void shutdown();
    void report();
public:
    Listener(Channel* channel, const string& reponseQueue, bool tx);
    virtual void received(Message& msg);
};

/**
 * A utility class for managing the options passed in.
 */
struct Args : public qpid::TestOptions {
    int ackmode;
    bool transactional;
    int prefetch;
    Args() : ackmode(NO_ACK), transactional(false), prefetch(1000) {
        addOptions()
            ("ack", optValue(ackmode, "MODE"), "Ack mode: 0=NO_ACK, 1=AUTO_ACK, 2=LAZY_ACK")
            ("transactional", optValue(transactional), "Use transactions")
            ("prefetch", optValue(prefetch, "N"), "prefetch count");
    }
};


/**
 * The main routine creates a Listener instance and sets it up to
 * consume from a private queue bound to the exchange with the
 * appropriate topic name.
 */
int main(int argc, char** argv){
    try{
        Args args;
        args.parse(argc, argv);
        if(args.help)
            cout << args << endl;
        else {
            cout << "topic_listener: Started." << endl;
            Connection connection(args.trace);
            connection.open(args.host, args.port, args.username, args.password, args.virtualhost);
            Channel channel(args.transactional, args.prefetch);
            connection.openChannel(channel);
        
            //declare exchange, queue and bind them:
            Queue response("response");
            channel.declareQueue(response);
        
            Queue control;
            channel.declareQueue(control);
            qpid::framing::FieldTable bindArgs;
            channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, control, "topic_control", bindArgs);
            //set up listener
            Listener listener(&channel, response.getName(), args.transactional);
            channel.consume(control, "c1", &listener, AckMode(args.ackmode));
            cout << "topic_listener: Consuming." << endl;
            channel.run();
            cout << "topic_listener: run returned, closing connection" << endl;
            connection.close();
            cout << "topic_listener: normal exit" << endl;
        }
        return 0;
    } catch (const std::exception& error) {
        cout << "topic_listener: " << error.what() << endl;
    }
    return 1;
}

Listener::Listener(Channel* _channel, const string& _responseq, bool tx) : 
    channel(_channel), responseQueue(_responseq), transactional(tx), init(false), count(0){}

void Listener::received(Message& message){
    if(!init){        
        start = now();
        count = 0;
        init = true;
    }
    FieldTable::ValuePtr type(message.getHeaders().get("TYPE"));

    if(!!type && StringValue("TERMINATION_REQUEST") == *type){
        shutdown();
    }else if(!!type && StringValue("REPORT_REQUEST") == *type){
        //send a report:
        report();
        init = false;
    }else if (++count % 100 == 0){        
        cout <<"Received " << count << " messages." << endl;
    }
}

void Listener::shutdown(){
    channel->close();
}

void Listener::report(){
    AbsTime finish = now();
    Duration time(start, finish);
    stringstream reportstr;
    reportstr << "Received " << count << " messages in "
              << time/TIME_MSEC << " ms.";
    Message msg(reportstr.str());
    msg.getHeaders().setString("TYPE", "REPORT");
    channel->publish(msg, string(), responseQueue);
    if(transactional){
        channel->commit();
    }
}

