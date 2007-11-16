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
 * This file provides a simple test (and example) of basic
 * functionality including declaring an exchange and a queue, binding
 * these together, publishing a message and receiving that message
 * asynchronously.
 */

#include <iostream>

#include <QpidError.h>
#include <ClientChannel.h>
#include <Connection.h>
#include <ClientMessage.h>
#include <MessageListener.h>
#include <sys/Monitor.h>
#include <FieldTable.h>

using namespace qpid::client;
using namespace qpid::sys;
using std::string;

/**
 * A simple message listener implementation that prints out the
 * message content then notifies a montitor allowing the test to
 * complete.
 */
class SimpleListener : public virtual MessageListener{
    Monitor* monitor;

public:
    inline SimpleListener(Monitor* _monitor) : monitor(_monitor){}

    inline virtual void received(Message& msg){
	std::cout << "Received message " << msg.getData()  << std::endl;
	monitor->notify();
    }
};

int main(int argc, char**)
{
    try{               
        //Use a custom exchange
	Exchange exchange("MyExchange", Exchange::TOPIC_EXCHANGE);
        //Use a named, temporary queue
	Queue queue("MyQueue", true);

 	
	Connection con(argc > 1);
        con.setTcpNoDelay(true);
	string host("localhost");	
	con.open(host, 5672, "guest", "guest", "/test");
	std::cout << "Opened connection." << std::endl;

        //Create and open a channel on the connection through which
        //most functionality is exposed
	Channel channel;      
	con.openChannel(&channel);
	std::cout << "Opened channel." << std::endl;	

        //'declare' the exchange and the queue, which will create them
        //as they don't exist
	channel.declareExchange(exchange);
	std::cout << "Declared exchange." << std::endl;
	channel.declareQueue(queue);
	std::cout << "Declared queue." << std::endl;

        //now bind the queue to the exchange
	qpid::framing::FieldTable args;
	channel.bind(exchange, queue, "MyTopic", args);
	std::cout << "Bound queue to exchange." << std::endl;

	//Set up a message listener to receive any messages that
	//arrive in our queue on the broker. We only expect one, and
	//as it will be received on another thread, we create a
	//montior to use to notify the main thread when that message
	//is received.
	Monitor monitor;
	SimpleListener listener(&monitor);
	string tag("MyTag");
	channel.consume(queue, tag, &listener);
	std::cout << "Registered consumer." << std::endl;

        //we need to enable the message dispatching for this channel
        //and we want that to occur on another thread so we call
        //start().
	channel.start();

        //Now we create and publish a message to our exchange with a
        //routing key that will cause it to be routed to our queue
	Message msg;
	string data("MyMessage");
	msg.setData(data);
	channel.publish(msg, exchange, "MyTopic");
	std::cout << "Published message: " << data << std::endl;

	{
            Monitor::ScopedLock l(monitor);
            //now we wait until we receive notification that the
            //message was received
            monitor.wait();
        }
        
        //close the channel & connection
	con.closeChannel(&channel);
	std::cout << "Closed channel." << std::endl;
	con.close();	
	std::cout << "Closed connection." << std::endl;
    }catch(qpid::QpidError error){
	std::cout << "Error [" << error.code << "] " << error.msg << " ("
                  << error.location.file << ":" << error.location.line
                  << ")" << std::endl;
	return 1;
    }
    return 0;
}
