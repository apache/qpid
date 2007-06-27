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
#include "qpid/QpidError.h"
#include "qpid/client/ClientChannel.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ClientExchange.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/ClientQueue.h"
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
    Channel* const channel;
    const string controlTopic;
    const bool transactional;
    Monitor monitor;
    int count;
    
    void waitForCompletion(int msgs);
    string generateData(int size);

public:
    Publisher(Channel* channel, const string& controlTopic, bool tx);
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
    int ackmode;
    bool transactional;
    int prefetch;
    int batches;
    int delay;
    int size;

    Args() : messages(1000), subscribers(1),
             ackmode(NO_ACK), transactional(false), prefetch(1000),
             batches(1), delay(0), size(256)
    {
        addOptions()
            ("messages", optValue(messages, "N"), "how many messages to send")
            ("subscribers", optValue(subscribers, "N"), "how many subscribers to expect reports from")
            ("ackmode", optValue(ackmode, "MODE"), "Acknowledgement mode:0=NO_ACK, 1=AUTO_ACK, 2=LAZY_ACK")
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
            connection.open(args.host, args.port, args.username, args.password, args.virtualhost);
            Channel channel(args.transactional, args.prefetch);
            connection.openChannel(channel);

            //declare queue (relying on default binding):
            Queue response("response");
            channel.declareQueue(response);

            //set up listener
            Publisher publisher(&channel, "topic_control", args.transactional);
            string tag("mytag");
            channel.consume(response, tag, &publisher, AckMode(args.ackmode));
            channel.start();

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
            int64_t avg = sum / batchSize;
            if(batchSize > 1){
                cout << batchSize << " batches completed. avg=" << avg << 
                    ", max=" << max << ", min=" << min << endl;
            }
            channel.close();
            connection.close();
        }
        return 0;
    }catch(exception& error) {
        cout << error.what() << endl;
    }
    return 1;
}

Publisher::Publisher(Channel* _channel, const string& _controlTopic, bool tx) : 
    channel(_channel), controlTopic(_controlTopic), transactional(tx){}

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
    Message msg;
    msg.setData(generateData(size));
    AbsTime start = now();
    {
        Monitor::ScopedLock l(monitor);
        for(int i = 0; i < msgs; i++){
            channel->publish(
                msg, Exchange::STANDARD_TOPIC_EXCHANGE, controlTopic);
        }
        //send report request
        Message reportRequest;
        reportRequest.getHeaders().setString("TYPE", "REPORT_REQUEST");
        channel->publish(reportRequest, Exchange::STANDARD_TOPIC_EXCHANGE, controlTopic);
        if(transactional){
            channel->commit();
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
    Message terminationRequest;
    terminationRequest.getHeaders().setString("TYPE", "TERMINATION_REQUEST");
    channel->publish(terminationRequest, Exchange::STANDARD_TOPIC_EXCHANGE, controlTopic);
    if(transactional){
        channel->commit();
    }
}

