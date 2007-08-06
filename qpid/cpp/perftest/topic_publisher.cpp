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
#include <iomanip>
#include <time.h>

using namespace qpid::client;
using namespace qpid::sys;
using std::string;


bool done = 0;


struct timespec operator-(const struct timespec& lhs, const struct timespec& rhs) {
	timespec r;
	r.tv_nsec = lhs.tv_nsec - rhs.tv_nsec;
	r.tv_sec = lhs.tv_sec - rhs.tv_sec;
	if (r.tv_nsec < 0) {
		r.tv_nsec += 1000000000;
		r.tv_sec -= 1;
	}
	return r;
}

std::ostream& operator<<(std::ostream& o, const struct timespec& ts) {
	o << ts.tv_sec << "." << std::setw(9) << std::setfill('0') << std::right << ts.tv_nsec;
	return o;
}

double toDouble(const struct timespec& ts) {
	return double(ts.tv_nsec)/1000000000 + ts.tv_sec;
}

class Listener : public MessageListener{

void set_time() {
    timespec ts;
    if (::clock_gettime(CLOCK_REALTIME, &ts))
	std::cout << "Error" << std::endl;
    startTime = ts;
  }

void print_time() {
    timespec ts;
    if (::clock_gettime(CLOCK_REALTIME, &ts))
	std::cout << "Error" << std::endl;
    std::cout << "Total Time:" << ts-startTime << std::endl;
    double rate = messageCount*2/toDouble(ts-startTime);
    std::cout << "returned Messages:" << messageCount  << std::endl;
    std::cout << "round trip Rate:" << rate  << std::endl;
  }

  struct timespec startTime;
  int messageCount;

 public:
 

     Listener(int mcount): messageCount(mcount) {
        set_time();
     }
	
    virtual void received(Message& msg) {
	print_time();
	std::cout << "Message: " << msg.getData() << std::endl;
        done = 1;
     }
     
    ~Listener() { };
    
    
    
    
};


int main() {
    Connection connection;
    Channel channel;
    Message msg;
    try {
        connection.open("127.0.0.1", 5672, "guest", "guest", "/test");
        connection.openChannel(channel);
        channel.start();

 	string queueControl = "control";
        Queue response(queueControl);
        channel.declareQueue(response);
        channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, response, queueControl);
        
	string  queueName ="queue01";
	string  queueNameC =queueName+"-1";

	// create publish queue
	Queue publish(queueName);
        channel.declareQueue(publish);
        channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, publish, queueName);
  
  	// create completion queue
	Queue completion(queueNameC);
        channel.declareQueue(completion);
        channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, completion, queueNameC);
      
	// pass queue name
	msg.setData(queueName);
	channel.publish(msg, Exchange::STANDARD_TOPIC_EXCHANGE, queueControl);

	std::cout << "Setup return queue:"<< queueNameC << std::endl;
	
	int count = 500000;
        Listener listener(count);
        channel.consume(completion, queueNameC, &listener);
	std::cout << "Setup consumer:"<< queueNameC << std::endl;




	struct timespec startTime;
	if (::clock_gettime(CLOCK_REALTIME, &startTime))
		std::cout << "Error" << std::endl;

	for (int i=0; i<count; i++) {
		msg.setData("Message 0123456789 ");
		channel.publish(msg, Exchange::STANDARD_TOPIC_EXCHANGE, queueName);
	}

	struct timespec endTime;
	if (::clock_gettime(CLOCK_REALTIME, &endTime))
		std::cout << "Error" << std::endl;
	std::cout << "publish Time:" << endTime-startTime << std::endl;
	double rate = count/toDouble(endTime-startTime);
	std::cout << "publish Messages:" << count  << std::endl;
	std::cout << "publish Rate:" << rate  << std::endl;


	msg.setData(queueName);  // last message to queue.
	channel.publish(msg, Exchange::STANDARD_TOPIC_EXCHANGE, queueName);
	
	std::cout << "wait:"<< queueNameC << std::endl;
	
        while (!done)
            sleep(1);

 
         channel.close();
         connection.close();
        return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
}


