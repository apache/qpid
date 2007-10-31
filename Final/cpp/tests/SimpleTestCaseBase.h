#ifndef _SimpleTestCaseBase_
#define _SimpleTestCaseBase_
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
#include "TestCase.h"
#include "TestUtils.h"

#define DEFAULT_INTEROP_MESSAGE_SIZE 256

namespace qpid {

using namespace qpid::client;

/**
 * SimpleTestCaseBase defines a base implementation of TestCase class. It provides the ability, to wrap a 'Worker' that
 * the work of running a test case is delegated too. There are two kinds of workers provided, a base worker, which is abstract
 * and may be extended to provide the tests behaviour, and a 'Sender' worker, that provides the ability to send a number
 * of messages.
 *
 * <p/>A worker encapsulates a connection, a channel, an expected number of messages to be sent or received, and a count of the
 * number actually sent or received.
 */
class SimpleTestCaseBase : public TestCase
{
protected:

    /**
     * A worker encapsulates a connection, channel, an expected number of messages to be sent or received, and a count of the
     * number actually sent or received.
     *
     * <p/>Worker is an abstract class, extend it to do something usefull on the init() and start() methods.
     *
     * <p/>A worker is created from a set of TestOptions, which captures a number of configuration parameters, such as the
     * broker to connect to.
     *
     * TODO: Extend TestOptions to capture the full set of creation properties for distributed tests.
     */
    class Worker
    {
    protected:
        /** Holds the connection for the worker to send/receive over. */
        client::Connection connection;

        /** Holds the channel for the worker to send/receive over. */
        client::Channel channel;

        /** Holds the expected number of messages for the worker to send or received. */
        const int messages;

        /** Holds a count of the number of messages actually sent or received. */
        int count;

    public:

        /**
         * Creates a worker using the specified options, for the given expected message count.
         *
         * @param options  The creation options.
         * @param messages The number of messages expected to be sent or received.
         */
        Worker(TestOptions& options, const int messages);
        virtual ~Worker(){}
        
        /** Should be called ahead of start() to configure the worker. */
        virtual void init() = 0;

        /** Starts the workers test activity. */
        virtual void start() = 0;

        /** Terminates the workers test activity. */
        virtual void stop();

        /** Gets the count of messages actually sent or received by the worker. */
        virtual int getCount();
    };

    /**
     * Sender is a worker that sends the expected number of messages to be sent, over the configured exchange, using a
     * specified routing key.
     */
    class Sender : public Worker
    {
        /** Holds the exchange to send message to. */
        const Exchange& exchange;

        /** Holds the routing key for the messages. */
        const std::string key;

        /** Holds the message size parameter for all test messages. */
        const int messageSize;

    public:

        /**
         * Creates a sender using the specified options, for the given expected message count, exchange and routing key.
         *
         * @param options  The creation options.
         * @param exchange The exchange to send the messages over.
         * @param key      The routing key for the messages.
         * @param messages The number of messages expected to be sent or received.
         */
        Sender(TestOptions& options, const Exchange& exchange, const std::string& key, const int messages); 

        /**
         * Creates a sender using the specified options, for the given expected message count, exchange and routing key.
         *
         * @param options     The creation options.
         * @param exchange    The exchange to send the messages over.
         * @param key         The routing key for the messages.
         * @param messages    The number of messages expected to be sent or received.
         * @param messageSize The size of test messages to send.
         */
        Sender(TestOptions& options, const Exchange& exchange, const std::string& key, const int messages, const int messageSize); 

        /**
         * Starts the underlying channel.
         */
        void init();

        /**
         * Sends the specified number of messages over the connection, channel and exchange using the configured routing key.
         */
        void start();
    };

    /** Holds a pointer to the encapsulated worker. */
    std::auto_ptr<Worker> worker;

public:

    virtual ~SimpleTestCaseBase() {}

    /**
     * Assigns the role to be played by this test case. The test parameters are fully specified in the
     * assignment messages filed table.
     *
     * @param role              The role to be played; sender or receiver.
     * @param assignRoleMessage The role assingment messages field table, contains the full test parameters.
     * @param options           Additional test options.
     */
    virtual void assign(const std::string& role, framing::FieldTable& params, TestOptions& options) = 0;    

    /**
     * Starts the worker.
     */
    void start();

    /**
     * Stops the worker.
     */
    void stop();

    /**
     * Reports the number of messages sent or received.
     */
    void report(client::Message& report);
};
}

#endif
