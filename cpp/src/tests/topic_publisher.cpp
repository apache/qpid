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
 * style of interaction. See topic_listener.cpp for the other half, in
 * which the logic for subscribers is defined.
 * 
 * This file contains the publisher logic. The publisher will send a
 * number of messages to the exchange with the appropriate routing key
 * for the logical 'topic'. Once it has done this it will then send a
 * request that each subscriber report back with the number of message
 * it has received and the time that elapsed between receiving the
 * first one and receiving the report request. Once the expected
 * number of reports are received, it sends out a request that each
 * subscriber shutdown.
 */

#include "TestOptions.h"
#include "qpid/client/Connection.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/Session_0_10.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/sys/Monitor.h"
#include <unistd.h>
#include "qpid/sys/Time.h"
#include <cstdlib>
#include <iostream>

using namespace qpid;
using namespace qpid::client;
using namespace qpid::sys;
using namespace std;

/**
 * The publishing logic is defined in this class. It implements
 * message listener and can therfore be used to receive messages sent
 * back by the subscribers.
 */
class Publisher : public MessageListener{    
    Session_0_10& session;
    const string controlTopic;
    const bool transactional;
    Monitor monitor;
    int count;
    
    void waitForCompletion(int msgs);
    string generateData(int size);

public:
    Publisher(Session_0_10& session, const string& controlTopic, bool tx);
    virtual void received(Message& msg);
    int64_t publish(int msgs, int listeners, int size);
    void terminate();
};

/**
 * A utility class for managing the options passed in to the test
 */
struct Args : public TestOptions {
    int messages;
    int subscribers;
    int ack;
    bool transactional;
    int prefetch;
    int batches;
    int delay;
    int size;

    Args() : messages(1000), subscribers(1),
             ack(500), transactional(false), prefetch(1000),
             batches(1), delay(0), size(256)
    {
        addOptions()
            ("messages", optValue(messages, "N"), "how many messages to send")
            ("subscribers", optValue(subscribers, "N"), "how many subscribers to expect reports from")
            ("ack", optValue(ack, "MODE"), "Acknowledgement mode:0=NO_ACK, 1=AUTO_ACK, 2=LAZY_ACK")
            ("transactional", optValue(transactional), "client should use transactions")
            ("prefetch", optValue(prefetch, "N"), "prefetch count")
            ("batches", optValue(batches, "N"), "how many batches to run")
            ("delay", optValue(delay, "SECONDS"), "Causes a delay between each batch")
            ("size", optValue(size, "BYTES"), "size of the published messages");
    }
};

int main(int argc, char** argv) {
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


            //declare queue (relying on default binding):
            session.queueDeclare(arg::queue="response");

            //set up listener
            SubscriptionManager mgr(session);
            Publisher publisher(session, "topic_control", args.transactional);
            mgr.subscribe(publisher, "response");
            mgr.start();

            int batchSize(args.batches);
            int64_t max(0);
            int64_t min(0);
            int64_t sum(0);
            for(int i = 0; i < batchSize; i++){
                if(i > 0 && args.delay) sleep(args.delay);
                int64_t msecs =
                    publisher.publish(args.messages,
                                      args.subscribers,
                                      args.size) / TIME_MSEC;
                if(!max || msecs > max) max = msecs;
                if(!min || msecs < min) min = msecs;
                sum += msecs;
                cout << "Completed " << (i+1) << " of " << batchSize
                          << " in " << msecs << "ms" << endl;
            }
            publisher.terminate();
            mgr.stop();
            int64_t avg = sum / batchSize;
            if(batchSize > 1){
                cout << batchSize << " batches completed. avg=" << avg << 
                    ", max=" << max << ", min=" << min << endl;
            }
            session.close();
            connection.close();
        }
        return 0;
    }catch(exception& error) {
        cout << error.what() << endl;
    }
    return 1;
}

Publisher::Publisher(Session_0_10& _session, const string& _controlTopic, bool tx) : 
    session(_session), controlTopic(_controlTopic), transactional(tx){}

void Publisher::received(Message& ){
    //count responses and when all are received end the current batch
    Monitor::ScopedLock l(monitor);
    if(--count == 0){
        monitor.notify();
    }
}

void Publisher::waitForCompletion(int msgs){
    count = msgs;
    monitor.wait();
}

int64_t Publisher::publish(int msgs, int listeners, int size){
    Message msg(generateData(size), controlTopic);
    AbsTime start = now();
    {
        Monitor::ScopedLock l(monitor);
        for(int i = 0; i < msgs; i++){
            session.messageTransfer(arg::content=msg, arg::destination="amq.topic");
        }
        //send report request
        Message reportRequest("", controlTopic);
        reportRequest.getHeaders().setString("TYPE", "REPORT_REQUEST");
        session.messageTransfer(arg::content=reportRequest, arg::destination="amq.topic");
        if(transactional){
            session.txCommit();
        }

        waitForCompletion(listeners);
    }

    AbsTime finish = now();
    return Duration(start, finish); 
}

string Publisher::generateData(int size){
    string data;
    for(int i = 0; i < size; i++){
        data += ('A' + (i / 26));
    }
    return data;
}

void Publisher::terminate(){
    //send termination request
    Message terminationRequest("", controlTopic);
    terminationRequest.getHeaders().setString("TYPE", "TERMINATION_REQUEST");
    session.messageTransfer(arg::content=terminationRequest, arg::destination="amq.topic");
    if(transactional){
        session.txCommit();
    }
}

