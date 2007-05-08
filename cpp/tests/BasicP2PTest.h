#ifndef _BasicP2PTest_
#define _BasicP2PTest_
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

#include <memory>
#include <sstream>

#include <ClientChannel.h>
#include <ClientMessage.h>
#include <Connection.h>
#include <Exception.h>
#include <MessageListener.h>
#include "SimpleTestCaseBase.h"


namespace qpid {

using namespace qpid::client;

class BasicP2PTest : public SimpleTestCaseBase
{

    class Receiver : public Worker, public MessageListener
    {
        const std::string queue;
        std::string tag;
    public:
        Receiver(TestOptions& options, const std::string& _queue, const int _messages) : Worker(options, _messages), queue(_queue){}

        void init()
        {
            Queue q(queue, true);
            channel.declareQueue(q);
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

public:
    void assign(const std::string& role, framing::FieldTable& params, TestOptions& options)
    {
        std::string queue = params.getString("P2P_QUEUE_AND_KEY_NAME");
        int messages = params.getInt("P2P_NUM_MESSAGES");
        if (role == "SENDER") {
            worker = std::auto_ptr<Worker>(new Sender(options, Exchange::DEFAULT_EXCHANGE, queue, messages));
        } else if(role == "RECEIVER"){
            worker = std::auto_ptr<Worker>(new Receiver(options, queue, messages));
        } else {
            throw Exception("unrecognised role");
        }
        worker->init();
    }
};

}

#endif
