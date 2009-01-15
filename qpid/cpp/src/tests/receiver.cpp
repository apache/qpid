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

#include <qpid/client/FailoverManager.h>
#include <qpid/client/Session.h>
#include <qpid/client/Message.h>
#include <qpid/client/SubscriptionManager.h>
#include <qpid/client/SubscriptionManager.h>
#include "TestOptions.h"

#include <iostream>
#include <fstream>


using namespace qpid;
using namespace qpid::client;
using namespace qpid::framing;

using namespace std;

struct Args : public qpid::TestOptions 
{
    string queue;
    uint messages;
    bool ignoreDuplicates;
    uint creditWindow;
    uint ackFrequency;

    Args() : queue("test-queue"), messages(0), ignoreDuplicates(false), creditWindow(0), ackFrequency(1)
    {
        addOptions()            
            ("queue", qpid::optValue(queue, "QUEUE NAME"), "Queue from which to request messages")
            ("messages", qpid::optValue(messages, "N"), "Number of messages to receive; 0 means receive indefinitely")
            ("ignore-duplicates", qpid::optValue(ignoreDuplicates), "Detect and ignore duplicates (by checking 'sn' header)")
            ("credit-window", qpid::optValue(creditWindow, "N"), "Credit window (0 implies infinite window)")
            ("ack-frequency", qpid::optValue(ackFrequency, "N"), "Ack frequency (0 implies none of the messages will get accepted)");
    }
};

const string EOS("eos");

class Receiver : public MessageListener, public FailoverManager::Command
{
  public:
    Receiver(const string& queue, uint messages, bool ignoreDuplicates, uint creditWindow, uint ackFrequency);
    void received(Message& message);
    void execute(AsyncSession& session, bool isRetry);
  private:
    const string queue;
    const uint count;
    const bool skipDups;
    SubscriptionSettings settings;
    Subscription subscription;
    uint processed;
    uint lastSn;

    bool isDuplicate(Message& message);
};

Receiver::Receiver(const string& q, uint messages, bool ignoreDuplicates, uint creditWindow, uint ackFrequency) : 
    queue(q), count(messages), skipDups(ignoreDuplicates), processed(0), lastSn(0) 
{
    if (creditWindow) settings.flowControl = FlowControl::messageWindow(creditWindow);
    settings.autoAck = ackFrequency;
}

void Receiver::received(Message& message) 
{
    if (!(skipDups && isDuplicate(message))) {
        bool eos = message.getData() == EOS;
        if (!eos) std::cout << message.getData() << std::endl;
        if (eos || ++processed == count) subscription.cancel();
    }
}

bool Receiver::isDuplicate(Message& message) 
{
    uint sn = message.getHeaders().getAsInt("sn");
    if (lastSn < sn) {
        lastSn = sn;
        return false;
    } else {
        return true;
    }
}

void Receiver::execute(AsyncSession& session, bool /*isRetry*/)
{
    SubscriptionManager subs(session);
    subscription = subs.subscribe(*this, queue, settings);
    subs.run();
}

int main(int argc, char ** argv)
{
    Args opts;
    try {
        opts.parse(argc, argv);
        FailoverManager connection(opts.con);
        Receiver receiver(opts.queue, opts.messages, opts.ignoreDuplicates, opts.creditWindow, opts.ackFrequency);
        connection.execute(receiver);
        connection.close();
        return 0;
    } catch(const std::exception& error) {
        std::cerr << "Failure: " << error.what() << std::endl;
    }
    return 1;
}



