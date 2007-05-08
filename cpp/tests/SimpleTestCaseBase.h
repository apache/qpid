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


namespace qpid {

using namespace qpid::client;

class SimpleTestCaseBase : public TestCase
{
protected:
    class Worker
    {
    protected:
        client::Connection connection;
        client::Channel channel;
        const int messages;
        int count;

    public:

        Worker(TestOptions& options, const int _messages) : 
            connection(options.trace), messages(_messages), count(0)
        {
            connection.open(options.broker, options.port);
            connection.openChannel(&channel);
        }
            
        virtual ~Worker(){}

        virtual void stop()
        {
            channel.close();
            connection.close();
        }

        virtual int getCount()
        {
            return count;
        }

        virtual void init() = 0;
        virtual void start() = 0;
    };

    class Sender : public Worker
    {
        const Exchange& exchange;
        const std::string key;
    public:
        Sender(TestOptions& options, 
               const Exchange& _exchange, 
               const std::string& _key, 
               const int _messages) 
            : Worker(options, _messages), exchange(_exchange), key(_key) {}

        void init()
        {
            channel.start();
        }

        void start(){
            Message msg;
            while (count < messages) {
                channel.publish(msg, exchange, key);
                count++;
            }
            stop();
        }
    };

    std::auto_ptr<Worker> worker;

public:
    virtual void assign(const std::string& role, framing::FieldTable& params, TestOptions& options) = 0;
    
    virtual ~SimpleTestCaseBase() {}

    void start()
    {
        if (worker.get()) {
            worker->start();
        }
    }

    void stop()
    {
        if (worker.get()) {
            worker->stop();
        }
    }

    void report(client::Message& report)
    {
        if (worker.get()) {
            report.getHeaders().setInt("MESSAGE_COUNT", worker->getCount());
            //add number of messages sent or received
            std::stringstream reportstr;
            reportstr << worker->getCount();
            report.setData(reportstr.str());
        }
    }
};

}

#endif
