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

void SimpleTestCaseBase::start()
{
    if (worker.get()) {
        worker->start();
    }
}

void SimpleTestCaseBase::stop()
{
    if (worker.get()) {
        worker->stop();
    }
}

void SimpleTestCaseBase::report(client::Message& report)
{
    if (worker.get()) {
        report.getHeaders().setInt("MESSAGE_COUNT", worker->getCount());
        //add number of messages sent or received
        std::stringstream reportstr;
        reportstr << worker->getCount();
        report.setData(reportstr.str());
    }
}

SimpleTestCaseBase::Sender::Sender(ConnectionOptions& options, 
                                   const Exchange& _exchange, 
                                   const std::string& _key, 
                                   const int _messages) 
    : Worker(options, _messages), exchange(_exchange), key(_key) {}

void SimpleTestCaseBase::Sender::init()
{
    channel.start();
}

void SimpleTestCaseBase::Sender::start(){
    Message msg;
    while (count < messages) {
        channel.publish(msg, exchange, key);
        count++;
    }
    stop();
}

SimpleTestCaseBase::Worker::Worker(ConnectionOptions& options, const int _messages) : 
    messages(_messages), count(0)
{
    connection.open(options.host, options.port);
    connection.openChannel(channel);
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

