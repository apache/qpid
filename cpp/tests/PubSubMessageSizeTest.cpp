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

#include "PubSubMessageSizeTest.h"

using namespace qpid;

class PubSubMessageSizeTest::Receiver : public Worker, public MessageListener
{
    const Exchange& exchange;
    const std::string queue;
    const std::string key;
    std::string tag;
public:
    Receiver(TestOptions& options, const Exchange& _exchange, const std::string& _queue, const std::string& _key, const int _messages) 
        : Worker(options, _messages), exchange(_exchange), queue(_queue), key(_key){}

    void init()
    {
        Queue q(queue, true);
        channel.declareQueue(q);
        framing::FieldTable args;
        channel.bind(exchange, q, key, args);
        channel.consume(q, tag, this);
        channel.start();
    }

    void start(){
    }
        
    void received(Message&)
    {
        count++;
    }
};

class PubSubMessageSizeTest::MultiReceiver : public Worker, public MessageListener
{
    typedef boost::ptr_vector<Receiver> ReceiverList;
    ReceiverList receivers;

public:
    MultiReceiver(TestOptions& options, const Exchange& exchange, const std::string& key, const int _messages, int receiverCount) 
        : Worker(options, _messages) 
    {
        for (int i = 0; i != receiverCount; i++) {                
            std::string queue = (boost::format("%1%_%2%") % options.clientid % i).str();
            receivers.push_back(new Receiver(options, exchange, queue, key, _messages));
        }
    }

    void init()
    {
        for (ReceiverList::size_type i = 0; i != receivers.size(); i++) {
            receivers[i].init();
        }
    }

    void start()
    {
        for (ReceiverList::size_type i = 0; i != receivers.size(); i++) {
            receivers[i].start();
        }
    }
        
    void received(Message& msg)
    {
        for (ReceiverList::size_type i = 0; i != receivers.size(); i++) {
            receivers[i].received(msg);
        }
    }

    virtual int getCount()
    {
        count = 0;
        for (ReceiverList::size_type i = 0; i != receivers.size(); i++) {
            count += receivers[i].getCount();
        }
        return count;
    }
    virtual void stop()
    {
        for (ReceiverList::size_type i = 0; i != receivers.size(); i++) {
            receivers[i].stop();
        }
    }
};

void PubSubMessageSizeTest::assign(const std::string& role, framing::FieldTable& params, TestOptions& options)
{
    //std::cout << "void PubSubMessageSizeTest::assign(const std::string& role, framing::FieldTable& params, TestOptions& options): called";

    std::string key = params.getString("PUBSUB_KEY");
    int messages = params.getInt("PUBSUB_NUM_MESSAGES");
    int receivers = params.getInt("PUBSUB_NUM_RECEIVERS");
    int messageSize = params.getInt("messageSize");

    if (role == "SENDER") {
        worker = std::auto_ptr<Worker>(new Sender(options, Exchange::STANDARD_TOPIC_EXCHANGE, key, messages, messageSize));
    } else if(role == "RECEIVER"){
        worker = std::auto_ptr<Worker>(new MultiReceiver(options, Exchange::STANDARD_TOPIC_EXCHANGE, key, messages, receivers));
    } else {
        throw Exception("unrecognised role");
    }
    worker->init();
}

