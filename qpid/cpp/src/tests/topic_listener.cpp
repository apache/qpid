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

#include "qpid/QpidError.h"
#include "qpid/client/ClientChannel.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ClientExchange.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/ClientQueue.h"
#include "qpid/sys/Time.h"
#include <iostream>
#include <sstream>

using namespace qpid::client;
using namespace qpid::sys;
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
class Args{
    string host;
    int port;
    AckMode ackMode;
    bool transactional;
    int prefetch;
    bool trace;
    bool help;
public:
    inline Args() : host("localhost"), port(5672), ackMode(NO_ACK), transactional(false), prefetch(1000), trace(false), help(false){}
    void parse(int argc, char** argv);
    void usage();

    const string& getHost() const { return host;}
    int getPort() const { return port; }
    AckMode getAckMode(){ return ackMode; }
    bool getTransactional() const { return transactional; }
    int getPrefetch(){ return prefetch; }
    bool getTrace() const { return trace; }
    bool getHelp() const { return help; }
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
            cout << "topic_listener: Started." << endl;
            Connection connection(args.getTrace());
            connection.open(args.getHost(), args.getPort(), "guest", "guest", "/test");
            Channel channel(args.getTransactional(), args.getPrefetch());
            connection.openChannel(channel);
        
            //declare exchange, queue and bind them:
            Queue response("response");
            channel.declareQueue(response);
        
            Queue control;
            channel.declareQueue(control);
            qpid::framing::FieldTable bindArgs;
            channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, control, "topic_control", bindArgs);
            //set up listener
            Listener listener(&channel, response.getName(), args.getTransactional());
            string tag;
            channel.consume(control, tag, &listener, args.getAckMode());
            cout << "topic_listener: Consuming." << endl;
            channel.run();
            connection.close();
            cout << "topic_listener: normal exit" << endl;
            return 0;
        }catch(const std::exception& error){
            cout << "topic_listener: " << error.what() << endl;
        }
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
    string type(message.getHeaders().getString("TYPE"));

    if(type == "TERMINATION_REQUEST"){
        shutdown();
    }else if(type == "REPORT_REQUEST"){        
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
            ackMode = AckMode(atoi(argv[++i]));
        }else if("-transactional" == name){
            transactional = true;
        }else if("-prefetch" == name){
            prefetch = atoi(argv[++i]);
        }else if("-trace" == name){
            trace = true;
        }else{
            cout << "Warning: unrecognised option " << name << endl;
        }
    }
}

void Args::usage(){
    cout << "Options:" << endl;
    cout << "    -help" << endl;
    cout << "            Prints this usage message" << endl;
    cout << "    -host <host>" << endl;
    cout << "            Specifies host to connect to (default is localhost)" << endl;
    cout << "    -port <port>" << endl;
    cout << "            Specifies port to conect to (default is 5762)" << endl;
    cout << "    -ack_mode <mode>" << endl;
    cout << "            Sets the acknowledgement mode" << endl;
    cout << "            0=NO_ACK (default), 1=AUTO_ACK, 2=LAZY_ACK" << endl;
    cout << "    -transactional" << endl;
    cout << "            Indicates the client should use transactions" << endl;
    cout << "    -prefetch <count>" << endl;
    cout << "            Specifies the prefetch count (default is 1000)" << endl;
    cout << "    -trace" << endl;
    cout << "            Indicates that the frames sent and received should be logged" << endl;
}
