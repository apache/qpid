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
 * Test hack code -- will upadte into a harness, work in progress
 */

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
#include <time.h>

using namespace qpid::client;
using namespace qpid::sys;
using namespace std;


bool done = 0;

class Listener : public MessageListener{
    string queueName;
public:
    virtual void received(Message& msg);
    ~Listener() { };
    Listener (string& _queueName): queueName(_queueName){};
};


int main() {
    Connection connection;
    Channel channel;
    Message msg;
    Message msg1;
    cout << "Starting listener" << endl;
    try {
        connection.open("127.0.0.1", 5672, "guest", "guest", "/test");
        connection.openChannel(channel);
        channel.start();

  	string queueControl = "control";
        Queue response(queueControl);
        channel.declareQueue(response);
        channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, response, queueControl);
        
	
	while (!channel.get(msg, response, AUTO_ACK))	{
	   cout <<" waiting... " << endl;
	   sleep(1);
	}
	string  queueName =msg.getData();
	string  queueNameC =queueName+ "-1";

	cout << "Using Queue:" << queueName << endl;
	cout << "Reply Queue:" << queueNameC << endl;
	// create consume queue
	Queue consume(queueName);
        channel.declareQueue(consume);
        channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, consume, queueName);
	
 
  	// create completion queue
	Queue completion(queueNameC);
        channel.declareQueue(completion);
        channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, completion, queueNameC);

        Listener listener(queueName);
	channel.consume(consume, queueName, &listener);
        cout << "Consuming" << endl;


        while (!done)
            sleep(1);
      
	// complete.
	msg1.setData(queueName);
	channel.publish(msg1, Exchange::STANDARD_TOPIC_EXCHANGE, queueNameC);

 
        channel.close();
        connection.close();
        return 0;
    } catch(const std::exception& error) {
        cout << "Unexpected exception: " << error.what() << endl;
    }
    connection.close();
    return 1;
}

void Listener::received(Message& msg) {
     if (msg.getData() == queueName)
     {
       cout << "Done:" << queueName <<endl;
       done = 1;
     }
}
