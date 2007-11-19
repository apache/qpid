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

#include "P2PMessageSizeTest.h"

using namespace qpid;
using namespace qpid::client;

/**
 * P2PMessageSizeTest::Receiver is a Worker to play the receiving role in P2P test.
 *
 * TODO: This code is identical to the receiver in BasicP2PTest so should share implementation with that.
 */
class P2PMessageSizeTest::Receiver : public Worker, public MessageListener
{
    /** Holds the name of the queue to send the test message on. */
    const std::string queue;

    /** Used for ? */
    std::string tag;

public:

    /**
     * Creates a new Worker from given the TestOptions. The namd of the queue, to consume from is also specified.
     *
     * @param options  The test options to configure the worker with.
     * @param _queue   The name of the queue to consume from on the default direct exchange.
     * @param _mesages The expected number of messages to consume. Ignored.
     */
    Receiver(TestOptions& options, const std::string& _queue, const int _messages) 
        : Worker(options, _messages), queue(_queue)
    {}

    /**
     * Binds this receivers queue to the standard exchange, and starts the dispatcher thread on its channel.
     */
    void init()
    {
        Queue q(queue, true);
        channel.declareQueue(q);
        framing::FieldTable args;
        channel.bind(Exchange::STANDARD_DIRECT_EXCHANGE, q, queue, args);
        channel.consume(q, tag, this);
        channel.start();
    }

    /**
     * Does nothing. 
     */
    void start()
    {
    }
       
    /**
     * Increments the message count, on every message received.
     *
     * @param message The test message. Ignored.
     */
    void received(Message& )
    {
        count++;
    }
};

/**
 * Assigns the role to be played by this test case. The test parameters are fully specified in the
 * assignment messages filed table. If the role is "SENDER" a Sender worker is created to delegate
 * the test functionality to, if the role is "RECEIVER" a Receiver is used.
 *
 * @param role              The role to be played; sender or receiver.
 * @param assignRoleMessage The role assingment messages field table, contains the full test parameters.
 * @param options           Additional test options.
 */
void P2PMessageSizeTest::assign(const std::string& role, framing::FieldTable& params, TestOptions& options)
{
    std::string queue = params.getString("P2P_QUEUE_AND_KEY_NAME");

    int messages = params.getInt("P2P_NUM_MESSAGES");
    int messageSize = params.getInt("messageSize");

    if (role == "SENDER") 
    {
        worker = std::auto_ptr<Worker>(new Sender(options, Exchange::STANDARD_DIRECT_EXCHANGE, queue, messages, messageSize));
    } 
    else if(role == "RECEIVER")
    {
        worker = std::auto_ptr<Worker>(new Receiver(options, queue, messages));
    }
    else 
    {
        throw Exception("unrecognised role");
    }

    worker->init();
}
