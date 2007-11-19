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

#include <QpidError.h>
#include <ClientChannel.h>
#include <Connection.h>
#include <ClientExchange.h>
#include <MessageListener.h>
#include <ClientQueue.h>
#include <sys/Monitor.h>
#include "unistd.h"
#include <sys/Time.h>
#include <cstdlib>
#include <iostream>

using namespace qpid::client;
using namespace qpid::sys;
using std::string;

/**
 * The publishing logic is defined in this class. It implements
 * message listener and can therfore be used to receive messages sent
 * back by the subscribers.
 */
class Publisher : public MessageListener{    
    Channel* const channel;
    const std::string controlTopic;
    const bool transactional;
    Monitor monitor;
    int count;
    
    void waitForCompletion(int msgs);
    string generateData(int size);

public:
    Publisher(Channel* channel, const std::string& controlTopic, bool tx);
    virtual void received(Message& msg);
    int64_t publish(int msgs, int listeners, int size);
    void terminate();
};

/**
 * A utility class for managing the options passed in to the test
 */
class Args{
    string host;
    int port;
    int messages;
    int subscribers;
    int ackMode;
    bool transactional;
    int prefetch;
    int batches;
    int delay;
    int size;
    bool trace;
    bool help;
public:
    inline Args() : host("localhost"), port(5672), messages(1000), subscribers(1), 
                    ackMode(NO_ACK), transactional(false), prefetch(1000), batches(1), 
                    delay(0), size(256), trace(false), help(false){}

    void parse(int argc, char** argv);
    void usage();

    inline const string& getHost() const { return host;}
    inline int getPort() const { return port; }
    inline int getMessages() const { return messages; }
    inline int getSubscribers() const { return subscribers; }
    inline int getAckMode(){ return ackMode; }
    inline bool getTransactional() const { return transactional; }
    inline int getPrefetch(){ return prefetch; }
    inline int getBatches(){ return batches; }
    inline int getDelay(){ return delay; }
    inline int getSize(){ return size; }
    inline bool getTrace() const { return trace; }
    inline bool getHelp() const { return help; }
};

int main(int argc, char** argv){
    Args args;
    args.parse(argc, argv);
    if(args.getHelp()){
        args.usage();
    }else{
        try{
            Connection connection(args.getTrace());
            connection.open(args.getHost(), args.getPort(), "guest", "guest", "/test");
            Channel channel(args.getTransactional(), args.getPrefetch());
            connection.openChannel(&channel);

            //declare queue (relying on default binding):
            Queue response("response");
            channel.declareQueue(response);

            //set up listener
            Publisher publisher(&channel, "topic_control", args.getTransactional());
            std::string tag("mytag");
            channel.consume(response, tag, &publisher, args.getAckMode());
            channel.start();

            int batchSize(args.getBatches());
            int64_t max(0);
            int64_t min(0);
            int64_t sum(0);
            for(int i = 0; i < batchSize; i++){
                if(i > 0 && args.getDelay()) sleep(args.getDelay());
                Time time = publisher.publish(
                    args.getMessages(), args.getSubscribers(), args.getSize());
                if(!max || time > max) max = time;
                if(!min || time < min) min = time;
                sum += time;
                std::cout << "Completed " << (i+1) << " of " << batchSize
                          << " in " << time/TIME_MSEC << "ms" << std::endl;
            }
            publisher.terminate();
            int64_t avg = sum / batchSize;
            if(batchSize > 1){
                std::cout << batchSize << " batches completed. avg=" << avg << 
                    ", max=" << max << ", min=" << min << std::endl;
            }
            channel.close();
            connection.close();
        }catch(qpid::QpidError error){
            std::cout << error.what() << std::endl;
        }
    }
}

Publisher::Publisher(Channel* _channel, const std::string& _controlTopic, bool tx) : 
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
    Time start = now();
    {
        Monitor::ScopedLock l(monitor);
        for(int i = 0; i < msgs; i++){
            channel->publish(msg, Exchange::STANDARD_TOPIC_EXCHANGE, controlTopic);
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

    Time finish = now();
    return finish - start; 
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

void Args::parse(int argc, char** argv){
    for(int i = 1; i < argc; i++){
        string name(argv[i]);
        if("-help" == name){
            help = true;
            break;
        }else if("-host" == name){
            host = argv[++i];
        }else if("-port" == name){
            port = atoi(argv[++i]);
        }else if("-messages" == name){
            messages = atoi(argv[++i]);
        }else if("-subscribers" == name){
            subscribers = atoi(argv[++i]);
        }else if("-ack_mode" == name){
            ackMode = atoi(argv[++i]);
        }else if("-transactional" == name){
            transactional = true;
        }else if("-prefetch" == name){
            prefetch = atoi(argv[++i]);
        }else if("-batches" == name){
            batches = atoi(argv[++i]);
        }else if("-delay" == name){
            delay = atoi(argv[++i]);
        }else if("-size" == name){
            size = atoi(argv[++i]);
        }else if("-trace" == name){
            trace = true;
        }else{
            std::cout << "Warning: unrecognised option " << name << std::endl;
        }
    }
}

void Args::usage(){
    std::cout << "Options:" << std::endl;
    std::cout << "    -help" << std::endl;
    std::cout << "            Prints this usage message" << std::endl;
    std::cout << "    -host <host>" << std::endl;
    std::cout << "            Specifies host to connect to (default is localhost)" << std::endl;
    std::cout << "    -port <port>" << std::endl;
    std::cout << "            Specifies port to conect to (default is 5762)" << std::endl;
    std::cout << "    -messages <count>" << std::endl;
    std::cout << "            Specifies how many messages to send" << std::endl;
    std::cout << "    -subscribers <count>" << std::endl;
    std::cout << "            Specifies how many subscribers to expect reports from" << std::endl;
    std::cout << "    -ack_mode <mode>" << std::endl;
    std::cout << "            Sets the acknowledgement mode" << std::endl;
    std::cout << "            0=NO_ACK (default), 1=AUTO_ACK, 2=LAZY_ACK" << std::endl;
    std::cout << "    -transactional" << std::endl;
    std::cout << "            Indicates the client should use transactions" << std::endl;
    std::cout << "    -prefetch <count>" << std::endl;
    std::cout << "            Specifies the prefetch count (default is 1000)" << std::endl;
    std::cout << "    -batches <count>" << std::endl;
    std::cout << "            Specifies how many batches to run" << std::endl;
    std::cout << "    -delay <seconds>" << std::endl;
    std::cout << "            Causes a delay between each batch" << std::endl;
    std::cout << "    -size <bytes>" << std::endl;
    std::cout << "            Sets the size of the published messages (default is 256 bytes)" << std::endl;
    std::cout << "    -trace" << std::endl;
    std::cout << "            Indicates that the frames sent and received should be logged" << std::endl;
}
