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
#include "SimpleTestCaseBase.h"

using namespace qpid;
using namespace qpid::client;

/**
 * Starts the test cases worker.
 */
void SimpleTestCaseBase::start()
{
    if (worker.get()) 
    {
        worker->start();
    }
}

/**
 * Stops the test cases worker.
 */
void SimpleTestCaseBase::stop()
{
    if (worker.get()) 
    {
        worker->stop();
    }
}

/**
 * Adds the test report to the specified message. This consists of writing the count of messages received into
 * a header on the message.
 *
 * @param report The report message to add the test report to.
 */
void SimpleTestCaseBase::report(client::Message& report)
{
    if (worker.get()) 
    {
        report.getHeaders().setInt("MESSAGE_COUNT", worker->getCount());

        // Add number of messages sent or received in the message body.
        /*
        std::stringstream reportstr;
        reportstr << worker->getCount();
        report.setData(reportstr.str());
        */
    }
}

/**
 * Creates a sender using the specified options, for the given expected message count, exchange and routing key.
 * This sets up the sender with the default message size for interop tests.
 *
 * @param options  The creation options.
 * @param exchange The exchange to send the messages over.
 * @param key      The routing key for the messages.
 * @param messages The number of messages expected to be sent or received.
 */
SimpleTestCaseBase::Sender::Sender(TestOptions& options, 
                                   const Exchange& _exchange, 
                                   const std::string& _key, 
                                   const int _messages) 
  : Worker(options, _messages), exchange(_exchange), key(_key), messageSize(DEFAULT_INTEROP_MESSAGE_SIZE) {}

/**
 * Creates a sender using the specified options, for the given expected message count, exchange and routing key.
 *
 * @param options     The creation options.
 * @param exchange    The exchange to send the messages over.
 * @param key         The routing key for the messages.
 * @param messages    The number of messages expected to be sent or received.
 * @param messageSize The size of test messages to send.
 */
SimpleTestCaseBase::Sender::Sender(TestOptions& options, 
                                   const Exchange& _exchange, 
                                   const std::string& _key, 
                                   const int _messages,
                                   const int _messageSize) 
  : Worker(options, _messages), exchange(_exchange), key(_key), messageSize(_messageSize) {}

void SimpleTestCaseBase::Sender::init()
{
    channel.start();
}

void SimpleTestCaseBase::Sender::start()
{
    Message message;
    qpid::createTestMessageOfSize(message, messageSize);

    while (count < messages) 
    {
        channel.publish(message, exchange, key);
        count++;
    }

    stop();
}

SimpleTestCaseBase::Worker::Worker(TestOptions& options, const int _messages) : 
    connection(options.trace), messages(_messages), count(0)
{
    connection.open(options.broker, options.port);
    connection.openChannel(&channel);
}
            
void SimpleTestCaseBase::Worker::stop()
{
    channel.close();
    connection.close();
}

int SimpleTestCaseBase::Worker::getCount()
{
    return count;
}
