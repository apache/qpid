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

#include <qpid/QpidError.h>
#include <qpid/client/Channel.h>
#include <qpid/client/Connection.h>
#include <qpid/client/Message.h>
#include <qpid/client/MessageListener.h>
#include <qpid/sys/Monitor.h>
#include <qpid/framing/FieldTable.h>

using namespace qpid::client;
using namespace qpid::sys;

class SimpleListener : public virtual MessageListener{
    Monitor* monitor;

public:
    inline SimpleListener(Monitor* _monitor) : monitor(_monitor){}

    inline virtual void received(Message& /*msg*/){
	std::cout << "Received message " /**<< msg **/<< std::endl;
	monitor->notify();
    }
};

int main(int argc, char**)
{
    try{               
	Connection con(argc > 1);
	Channel channel;
	Exchange exchange("MyExchange", Exchange::TOPIC_EXCHANGE);
	Queue queue("MyQueue", true);
	
	string host("localhost");
	
	con.open(host);
	std::cout << "Opened connection." << std::endl;
	con.openChannel(&channel);
	std::cout << "Opened channel." << std::endl;	
	channel.declareExchange(exchange);
	std::cout << "Declared exchange." << std::endl;
	channel.declareQueue(queue);
	std::cout << "Declared queue." << std::endl;
	qpid::framing::FieldTable args;
	channel.bind(exchange, queue, "MyTopic", args);
	std::cout << "Bound queue to exchange." << std::endl;

	//set up a message listener
	Monitor monitor;
	SimpleListener listener(&monitor);
	string tag("MyTag");
	channel.consume(queue, tag, &listener);
	channel.start();
	std::cout << "Registered consumer." << std::endl;

	Message msg;
	string data("MyMessage");
	msg.setData(data);
	channel.publish(msg, exchange, "MyTopic");
	std::cout << "Published message." << std::endl;

	{
            Monitor::ScopedLock l(monitor);
            monitor.wait();
        }
        
	con.closeChannel(&channel);
	std::cout << "Closed channel." << std::endl;
	con.close();	
	std::cout << "Closed connection." << std::endl;
    }catch(qpid::QpidError error){
	std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
	return 1;
    }
    return 0;
}
