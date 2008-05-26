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
#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionManager.h"

using namespace qpid;
using namespace qpid::client;
using namespace qpid::sys;
using std::string;

typedef std::vector<std::string> StringSet;

struct Args : public qpid::TestOptions {
    uint count;
    uint ack;
    string queue;

    Args() : count(0), ack(1)
    {
        addOptions()
            ("count", optValue(count, "N"), "number of messages to publish")
            ("ack-frequency", optValue(ack, "N"), "ack every N messages (0 means use no-ack mode)")
            ("queue", optValue(queue, "<exchange name>"), "queue to consume from");
    }
};

Args opts;

struct Client 
{
    Connection connection;
    Session session;

    Client() 
    {
        opts.open(connection);
        session = connection.newSession();
    }

    void consume()
    {
        
        SubscriptionManager subs(session);
        LocalQueue lq(AckPolicy(opts.ack));
        subs.setAcceptMode(opts.ack > 0 ? 0 : 1);
        subs.setFlowControl(opts.count, SubscriptionManager::UNLIMITED,
                            false);
        subs.subscribe(lq, opts.queue);
        Message msg;
        for (size_t i = 0; i < opts.count; ++i) {
            msg=lq.pop();
            std::cout << "Received: " << msg.getMessageProperties().getCorrelationId() << std::endl;
        }
        if (opts.ack != 0)
            subs.getAckPolicy().ackOutstanding(session); // Cumulative ack for final batch.
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
        client.consume();
        return 0;
    } catch(const std::exception& e) {
	std::cout << e.what() << std::endl;
    }
    return 1;
}
