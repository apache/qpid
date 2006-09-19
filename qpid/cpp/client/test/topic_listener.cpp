/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <iostream>
#include <sstream>
#include "apr_time.h"
#include "QpidError.h"
#include "Channel.h"
#include "Connection.h"
#include "Exchange.h"
#include "MessageListener.h"
#include "Queue.h"

using namespace qpid::client;

class Listener : public MessageListener{    
    Channel* const channel;
    const std::string responseQueue;
    const bool transactional;
    bool init;
    int count;
    apr_time_t start;
    
    void shutdown();
    void report();
public:
    Listener(Channel* channel, const std::string& reponseQueue, bool tx);
    virtual void received(Message& msg);
};

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

int main(int argc, char** argv){
    Args args;
    args.parse(argc, argv);
    if(args.getHelp()){
        args.usage();
    }else{
        try{
            Connection connection(args.getTrace());
            connection.open(args.getHost(), args.getPort());
            Channel channel(args.getTransactional(), args.getPrefetch());
            connection.openChannel(&channel);
        
            //declare exchange, queue and bind them:
            Queue response("response");
            channel.declareQueue(response);
        
            Queue control;
            channel.declareQueue(control);
            qpid::framing::FieldTable bindArgs;
            channel.bind(Exchange::DEFAULT_TOPIC_EXCHANGE, control, "topic_control", bindArgs);
            //set up listener
            Listener listener(&channel, response.getName(), args.getTransactional());
            std::string tag;
            channel.consume(control, tag, &listener, args.getAckMode());
            channel.run();
            connection.close();
        }catch(qpid::QpidError error){
            std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
        }
    }
}

Listener::Listener(Channel* _channel, const std::string& _responseq, bool tx) : 
    channel(_channel), responseQueue(_responseq), transactional(tx), init(false), count(0){}

void Listener::received(Message& message){
    if(!init){        
        start = apr_time_as_msec(apr_time_now());
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
    apr_time_t finish = apr_time_as_msec(apr_time_now());
    apr_time_t time = finish - start;
    std::stringstream report;
    report << "Received " << count << " messages in " << time << " ms.";
    Message msg;
    msg.setData(report.str());
    channel->publish(msg, Exchange::DEFAULT_DIRECT_EXCHANGE, responseQueue);
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
