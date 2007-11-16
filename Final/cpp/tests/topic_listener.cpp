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

#include <QpidError.h>
#include <ClientChannel.h>
#include <Connection.h>
#include <ClientExchange.h>
#include <MessageListener.h>
#include <ClientQueue.h>
#include <sys/Time.h>
#include <iostream>
#include <sstream>

using namespace qpid::client;
using namespace qpid::sys;
using std::string;

/**
 * A message listener implementation in which the runtime logic is
 * defined.
 */
class Listener : public MessageListener{    
    Channel* const channel;
    const std::string responseQueue;
    const bool transactional;
    bool init;
    int count;
    Time start;
    
    void shutdown();
    void report();
public:
    Listener(Channel* channel, const std::string& reponseQueue, bool tx);
    virtual void received(Message& msg);
};

/**
 * A utility class for managing the options passed in.
 */
class Args{
    string host;
    int port;
    int ackMode;
    bool transactional;
    int prefetch;
    bool trace;
    bool help;
public:
    inline Args() : host("localhost"), port(5672), ackMode(NO_ACK), transactional(false), prefetch(1000), trace(false), help(false){}
    void parse(int argc, char** argv);
    void usage();

    inline const string& getHost() const { return host;}
    inline int getPort() const { return port; }
    inline int getAckMode(){ return ackMode; }
    inline bool getTransactional() const { return transactional; }
    inline int getPrefetch(){ return prefetch; }
    inline bool getTrace() const { return trace; }
    inline bool getHelp() const { return help; }
};

/**
 * The main routine creates a Listener instance and sets it up to
 * consume from a private queue bound to the exchange with the
 * appropriate topic name.
 */
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
        
            //declare exchange, queue and bind them:
            Queue response("response");
            channel.declareQueue(response);
        
            Queue control;
            channel.declareQueue(control);
            qpid::framing::FieldTable bindArgs;
            channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, control, "topic_control", bindArgs);
            //set up listener
            Listener listener(&channel, response.getName(), args.getTransactional());
            std::string tag;
            channel.consume(control, tag, &listener, args.getAckMode());
            channel.run();
            connection.close();
        }catch(qpid::QpidError error){
            std::cout << error.what() << std::endl;
        }
    }
}

Listener::Listener(Channel* _channel, const std::string& _responseq, bool tx) : 
    channel(_channel), responseQueue(_responseq), transactional(tx), init(false), count(0){}

void Listener::received(Message& message){
    if(!init){        
        start = now();
        count = 0;
        init = true;
    }
    std::string type(message.getHeaders().getString("TYPE"));

    if(type == "TERMINATION_REQUEST"){
        shutdown();
    }else if(type == "REPORT_REQUEST"){        
        //send a report:
        report();
        init = false;
    }else if (++count % 100 == 0){        
        std::cout <<"Received " << count << " messages." << std::endl;
    }
}

void Listener::shutdown(){
    channel->close();
}

void Listener::report(){
    Time finish = now();
    Time time = finish - start;
    std::stringstream reportstr;
    reportstr << "Received " << count << " messages in "
              << time/TIME_MSEC << " ms.";
    Message msg;
    msg.setData(reportstr.str());
    channel->publish(msg, string(), responseQueue);
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
        }else if("-ack_mode" == name){
            ackMode = atoi(argv[++i]);
        }else if("-transactional" == name){
            transactional = true;
        }else if("-prefetch" == name){
            prefetch = atoi(argv[++i]);
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
    std::cout << "    -ack_mode <mode>" << std::endl;
    std::cout << "            Sets the acknowledgement mode" << std::endl;
    std::cout << "            0=NO_ACK (default), 1=AUTO_ACK, 2=LAZY_ACK" << std::endl;
    std::cout << "    -transactional" << std::endl;
    std::cout << "            Indicates the client should use transactions" << std::endl;
    std::cout << "    -prefetch <count>" << std::endl;
    std::cout << "            Specifies the prefetch count (default is 1000)" << std::endl;
    std::cout << "    -trace" << std::endl;
    std::cout << "            Indicates that the frames sent and received should be logged" << std::endl;
}
