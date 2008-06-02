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

#include "qpid/Exception.h"
#include "qpid/client/Channel.h"
#include "qpid/client/Message.h"
#include "qpid/client/Connection.h"
#include "ConnectionOptions.h"
#include "qpid/client/MessageListener.h"
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

        Worker(ConnectionOptions& options, const int messages);
        virtual ~Worker(){}

        virtual void stop();
        virtual int getCount();
        virtual void init() = 0;
        virtual void start() = 0;
    };

    class Sender : public Worker
    {
        const Exchange& exchange;
        const std::string key;
    public:
        Sender(ConnectionOptions& options, 
               const Exchange& exchange, 
               const std::string& key, 
               const int messages); 
        void init();
        void start();
    };

    std::auto_ptr<Worker> worker;

public:
    virtual void assign(const std::string& role, framing::FieldTable& params, ConnectionOptions& options) = 0;
    
    virtual ~SimpleTestCaseBase() {}

    void start();
    void stop();
    void report(client::Message& report);
};

}

#endif
