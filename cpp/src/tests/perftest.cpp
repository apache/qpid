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

#include "TestOptions.h"

#include "qpid/client/Channel.h"
#include "qpid/client/Exchange.h"
#include "qpid/client/Queue.h"
#include "qpid/client/Connection.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/Message.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"

#include <iostream>
#include <cstdlib>
#include <iomanip>
#include <time.h>
#include <unistd.h>


using namespace qpid;
using namespace qpid::client;
using namespace qpid::sys;
using namespace std;

struct Opts : public TestOptions {

    bool listen;
    bool publish;
    int count;
	bool durable;
    
    Opts() : listen(false), publish(false), count(500000) {
        addOptions() 
            ("listen", optValue(listen), "Consume messages.")
            ("publish", optValue(publish), "Produce messages.")
            ("count", optValue(count, "N"), "Messages to send/receive.")
            ("durable", optValue(durable, "N"), "Publish messages as durable.");
    }
};

Opts opts;

struct ListenThread : public Runnable { Thread thread; void run(); };
struct PublishThread : public Runnable { Thread thread; void run(); };
    
int main(int argc, char** argv) {
    try {
        opts.parse(argc, argv);
        if (!opts.listen && !opts.publish)
            opts.listen = opts.publish = true;
        ListenThread listen;
        PublishThread publish;
        if (opts.listen)
            listen.thread=Thread(listen);
        if (opts.publish)
            publish.thread=Thread(publish);
        if (opts.listen)
            listen.thread.join();
        if (opts.publish)
            publish.thread.join();
    }
    catch (const std::exception& e) {
        cout << "Unexpected exception: " << e.what() << endl;
    }
}

// ================================================================
// Publish client
//

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

ostream& operator<<(ostream& o, const struct timespec& ts) {
    o << ts.tv_sec << "." << setw(9) << setfill('0') << right << ts.tv_nsec;
    return o;
}

double toDouble(const struct timespec& ts) {
    return double(ts.tv_nsec)/1000000000 + ts.tv_sec;
}

class PublishListener : public MessageListener {

    void set_time() {
        timespec ts;
        if (::clock_gettime(CLOCK_REALTIME, &ts))
            throw Exception(QPID_MSG("clock_gettime failed: " << strError(errno)));
        startTime = ts;
    }

    void print_time() {
        timespec ts;
        if (::clock_gettime(CLOCK_REALTIME, &ts))
            throw Exception(QPID_MSG("clock_gettime failed: " << strError(errno)));
        cout << "Total Time:" << ts-startTime << endl;
        double rate = messageCount*2/toDouble(ts-startTime);
        cout << "returned Messages:" << messageCount  << endl;
        cout << "round trip Rate:" << rate  << endl;
    }

    struct timespec startTime;
    int messageCount;
    bool done;
    Monitor lock;
    
  public:
 
    PublishListener(int mcount): messageCount(mcount), done(false) {
        set_time();
    }
	
    void received(Message& msg) {
	print_time();
	QPID_LOG(info, "Publisher: received: " << msg.getData());
        Mutex::ScopedLock l(lock);
        QPID_LOG(info, "Publisher: done.");
        done = true;
        lock.notify();
    }

    void wait() {
        Mutex::ScopedLock l(lock);
        while (!done)
            lock.wait();
    }
};


void PublishThread::run() {
    Connection connection;
    Channel channel;
    Message msg;
    opts.open(connection);
    connection.openChannel(channel);
    channel.start();

    cout << "Started publisher." << endl;
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

    QPID_LOG(info, "Publisher: setup return queue: "<< queueNameC);
	
    int count = opts.count;
    PublishListener listener(count);
    channel.consume(completion, queueNameC, &listener);
    QPID_LOG(info, "Publisher setup consumer: "<< queueNameC);

    struct timespec startTime;
    if (::clock_gettime(CLOCK_REALTIME, &startTime))
        throw Exception(QPID_MSG("clock_gettime failed: " << strError(errno)));

	bool durable = opts.durable;
	if (durable)
	    msg.getDeliveryProperties().setDeliveryMode(framing::PERSISTENT);
		
    for (int i=0; i<count; i++) {
        msg.setData("Message 0123456789 ");
        channel.publish(msg, Exchange::STANDARD_TOPIC_EXCHANGE, queueName);
    }

    struct timespec endTime;
    if (::clock_gettime(CLOCK_REALTIME, &endTime))
        throw Exception(QPID_MSG("clock_gettime failed: " << strError(errno)));

    cout << "publish Time:" << endTime-startTime << endl;
    double rate = count/toDouble(endTime-startTime);
    cout << "publish Messages:" << count  << endl;
    cout << "publish Rate:" << rate  << endl;

    msg.setData(queueName);  // last message to queue.
    channel.publish(msg, Exchange::STANDARD_TOPIC_EXCHANGE, queueName);
	
    listener.wait();
 
    channel.close();
    connection.close();
}



// ================================================================
// Listen client
//

class Listener : public MessageListener{
    string queueName;
    Monitor lock;
    bool done;

  public:
    Listener (string& _queueName): queueName(_queueName), done(false) {};

    void received(Message& msg) {
        if (msg.getData() == queueName)
        {
            Mutex::ScopedLock l(lock);
            QPID_LOG(info, "Listener: done. " << queueName);
            done = true;
            lock.notify();
        }
    }
    
    void wait() {
        Mutex::ScopedLock l(lock);
        while (!done)
            lock.wait();
    }
};

void ListenThread::run() {
    Connection connection;
    Channel channel;
    Message msg;
    Message msg1;
    cout << "Started listener." << endl;;
    opts.open(connection);
    connection.openChannel(channel);
    channel.start();

    string queueControl = "control";
    Queue response(queueControl);
    channel.declareQueue(response);
    channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, response, queueControl);	
    while (!channel.get(msg, response, AUTO_ACK))	{
        QPID_LOG(info, "Listener: waiting for queue name.");
        sleep(1);
    }
    string  queueName =msg.getData();
    string  queueNameC =queueName+ "-1";

    QPID_LOG(info, "Listener: Using Queue:" << queueName);
    QPID_LOG(info, "Listener: Reply Queue:" << queueNameC);
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
    QPID_LOG(info, "Listener: consuming...");

    listener.wait();
      
    QPID_LOG(info, "Listener: send final message.");
    // complete.
    msg1.setData(queueName);
    channel.publish(msg1, Exchange::STANDARD_TOPIC_EXCHANGE, queueNameC);

    channel.close();
    connection.close();
}


