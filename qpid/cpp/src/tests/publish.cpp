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

#include <algorithm>
#include <iostream>
#include <memory>
#include <sstream>
#include <vector>

#include "TestOptions.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Message.h"
#include "qpid/client/AsyncSession.h"
#include "qpid/client/SubscriptionManager.h"

using namespace qpid;
using namespace qpid::client;
using namespace qpid::sys;
using std::string;

typedef std::vector<std::string> StringSet;

struct Args : public qpid::TestOptions {
    uint size;
    uint count;
    bool durable;
    string destination;
    string routingKey;

    Args() : size(256), count(1000), durable(true)
    {
        addOptions()
            ("size", optValue(size, "N"), "message size")
            ("count", optValue(count, "N"), "number of messages to publish")
            ("durable", optValue(durable, "yes|no"), "use durable messages")
            ("destination", optValue(destination, "<exchange name>"), "destination to publish to")
            ("routing-key", optValue(routingKey, "<key>"), "routing key to publish with");
    }
};

Args opts;

struct Client 
{
    Connection connection;
    AsyncSession session;

    Client() 
    {
        opts.open(connection);
        session = connection.newSession();
    }

    std::string id(uint i)
    {
        std::stringstream s;
        s << "msg" << i;
        return s.str();
    }

    void publish()
    {
        Message msg(string(opts.size, 'X'), opts.routingKey);
        if (opts.durable)
            msg.getDeliveryProperties().setDeliveryMode(framing::PERSISTENT);
        
        for (uint i = 0; i < opts.count; i++) {
            msg.getMessageProperties().setCorrelationId(id(i + 1));
            session.messageTransfer(arg::destination=opts.destination,
                                    arg::content=msg,
                                    arg::acceptMode=1);
        }
        session.sync();
    }

    ~Client() 
    {
        try{
            session.close();
            connection.close();
        } catch(const std::exception& e) {
            std::cout << e.what() << std::endl;
        }
    }
};

int main(int argc, char** argv)
{
    try {
        opts.parse(argc, argv);
        Client client;
        client.publish();
        return 0;
    } catch(const std::exception& e) {
	std::cout << e.what() << std::endl;
    }
    return 1;
}
