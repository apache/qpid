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
#include "qpid/client/Connection.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/Session_0_10.h"
#include "qpid/client/SubscriptionManager.h"
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
    Session_0_10& session;
    SubscriptionManager& mgr;
    const string responseQueue;
    const bool transactional;
    bool init;
    int count;
    AbsTime start;
    
    void shutdown();
    void report();
public:
    Listener(Session_0_10& session, SubscriptionManager& mgr, const string& reponseQueue, bool tx);
    virtual void received(Message& msg);
};

/**
 * A utility class for managing the options passed in.
 */
struct Args : public qpid::TestOptions {
    int ack;
    bool transactional;
    bool durable;
    int prefetch;

    Args() : ack(0), transactional(false), durable(false), prefetch(0) {
        addOptions()
            ("ack", optValue(ack, "MODE"), "Ack frequency in messages (defaults to half the prefetch value)")
            ("transactional", optValue(transactional), "Use transactions")
            ("durable", optValue(durable), "subscribers should use durable queues")
            ("prefetch", optValue(prefetch, "N"), "prefetch count (0 implies no flow control, and no acking)");
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
            Connection connection(args.trace);
            args.open(connection);
            Session_0_10 session = connection.newSession();
            if (args.transactional) {
                session.txSelect();
            }

            //declare exchange, queue and bind them:
            session.queueDeclare(arg::queue="response");
            std::string control = "control_" + session.getId().str();
            if (args.durable) {
                session.queueDeclare(arg::queue=control, arg::durable=true);
            } else {
                session.queueDeclare(arg::queue=control, arg::exclusive=true, arg::autoDelete=true);
            }
            session.queueBind(arg::exchange="amq.topic", arg::queue=control, arg::routingKey="topic_control");

            //set up listener
            SubscriptionManager mgr(session);
            Listener listener(session, mgr, "response", args.transactional);
            if (args.prefetch) {
                mgr.setAckPolicy(AckPolicy(args.ack ? args.ack : (args.prefetch / 2)));
                mgr.setFlowControl(args.prefetch, SubscriptionManager::UNLIMITED, true);
            } else {
                mgr.setConfirmMode(false);
                mgr.setFlowControl(SubscriptionManager::UNLIMITED, SubscriptionManager::UNLIMITED, false);
            }
            mgr.subscribe(listener, control);

            cout << "topic_listener: listening..." << endl;
            mgr.run();
            if (args.durable) {
                session.queueDelete(arg::queue=control);
            }
            session.close();
            cout << "closing connection" << endl;
            connection.close();
        }
        return 0;
    } catch (const std::exception& error) {
        cout << "topic_listener: " << error.what() << endl;
    }
    return 1;
}

Listener::Listener(Session_0_10& s, SubscriptionManager& m, const string& _responseq, bool tx) : 
    session(s), mgr(m), responseQueue(_responseq), transactional(tx), init(false), count(0){}

void Listener::received(Message& message){
    if(!init){        
        start = now();
        count = 0;
        init = true;
        cout << "Batch started." << endl;
    }
    FieldTable::ValuePtr type(message.getHeaders().get("TYPE"));

    if(!!type && StringValue("TERMINATION_REQUEST") == *type){
        shutdown();
    }else if(!!type && StringValue("REPORT_REQUEST") == *type){
        message.acknowledge();//acknowledge everything upto this point
        cout <<"Batch ended, sending report." << endl;
        //send a report:
        report();
        init = false;
    }else if (++count % 1000 == 0){        
        cout <<"Received " << count << " messages." << endl;
    }
}

void Listener::shutdown(){
    mgr.stop();
}

void Listener::report(){
    AbsTime finish = now();
    Duration time(start, finish);
    stringstream reportstr;
    reportstr << "Received " << count << " messages in "
              << time/TIME_MSEC << " ms.";
    Message msg(reportstr.str(), responseQueue);
    msg.getHeaders().setString("TYPE", "REPORT");
    session.messageTransfer(arg::content=msg);
    if(transactional){
        session.txCommit();
    }
}

