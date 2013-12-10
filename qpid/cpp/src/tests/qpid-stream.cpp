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

#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Session.h>
#include <qpid/sys/Runnable.h>
#include <qpid/sys/Thread.h>
#include <qpid/sys/Time.h>
#include <qpid/Options.h>
#include <iostream>
#include <string>

using namespace qpid::messaging;
using namespace qpid::types;

namespace qpid {
namespace tests {

struct Args : public qpid::Options
{
    std::string url;
    std::string address;
    uint size;
    uint rate;
    bool durable;
    uint receiverCapacity;
    uint senderCapacity;
    uint ackFrequency;

    Args() :
        url("amqp:tcp:127.0.0.1:5672"),
        address("test-queue"),
        size(512),
        rate(1000),
        durable(false),
        receiverCapacity(0),
        senderCapacity(0),
        ackFrequency(1)
    {
        addOptions()
            ("url", qpid::optValue(url, "URL"), "Url to connect to.")
            ("address", qpid::optValue(address, "ADDRESS"), "Address to stream messages through.")
            ("size", qpid::optValue(size, "bytes"), "Message size in bytes (content only, not headers).")
            ("rate", qpid::optValue(rate, "msgs/sec"), "Rate at which to stream messages.")
            ("durable", qpid::optValue(durable, "true|false"), "Mark messages as durable.")
            ("sender-capacity", qpid::optValue(senderCapacity, "N"), "Credit window (0 implies infinite window)")
            ("receiver-capacity", qpid::optValue(receiverCapacity, "N"), "Credit window (0 implies infinite window)")
            ("ack-frequency", qpid::optValue(ackFrequency, "N"),
             "Ack frequency (0 implies none of the messages will get accepted)");
    }
};

Args opts;

const std::string TS = "ts";

uint64_t timestamp(const qpid::sys::AbsTime& time)
{
    qpid::sys::Duration t(qpid::sys::EPOCH, time);
    return t;
}

struct Client : qpid::sys::Runnable
{
    virtual ~Client() {}
    virtual void doWork(Session&) = 0;

    void run()
    {
        Connection connection(opts.url);
        try {
            connection.open();
            Session session = connection.createSession();
            doWork(session);
            session.close();
            connection.close();
        } catch(const std::exception& error) {
            std::cout << error.what() << std::endl;
            connection.close();
        }
    }

    qpid::sys::Thread thread;

    void start() { thread = qpid::sys::Thread(this); }
    void join() { thread.join(); }
};

struct Publish : Client
{
    void doWork(Session& session)
    {
        Sender sender = session.createSender(opts.address);
        if (opts.senderCapacity) sender.setCapacity(opts.senderCapacity);
        Message msg(std::string(opts.size, 'X'));
        uint64_t interval = qpid::sys::TIME_SEC / opts.rate;
        uint64_t sent = 0, missedRate = 0;
        qpid::sys::AbsTime start = qpid::sys::now();
        while (true) {
            qpid::sys::AbsTime sentAt = qpid::sys::now();
            msg.getProperties()[TS] = timestamp(sentAt);
            sender.send(msg);
            ++sent;
            qpid::sys::AbsTime waitTill(start, sent*interval);
            qpid::sys::Duration delay(sentAt, waitTill);
            if (delay < 0) {
                ++missedRate;
            } else {
                qpid::sys::usleep(delay / qpid::sys::TIME_USEC);
            }
        }
    }
};

struct Consume : Client
{
    void doWork(Session& session)
    {
        Message msg;
        uint64_t received = 0;
        double minLatency = std::numeric_limits<double>::max();
        double maxLatency = 0;
        double totalLatency = 0;
        Receiver receiver = session.createReceiver(opts.address);
        if (opts.receiverCapacity) receiver.setCapacity(opts.receiverCapacity);
        while (receiver.fetch(msg)) {
            ++received;
            if (opts.ackFrequency && (received % opts.ackFrequency == 0)) {
                session.acknowledge();
            }
            //calculate latency
            uint64_t receivedAt = timestamp(qpid::sys::now());
            uint64_t sentAt = msg.getProperties()[TS].asUint64();
            double latency = ((double) (receivedAt - sentAt)) / qpid::sys::TIME_MSEC;

            //update avg, min & max
            minLatency = std::min(minLatency, latency);
            maxLatency = std::max(maxLatency, latency);
            totalLatency += latency;

            if (received % opts.rate == 0) {
                std::cout << "count=" << received
                          << ", avg=" << (totalLatency/received)
                          << ", min=" << minLatency
                          << ", max=" << maxLatency << std::endl;
            }
        }
    }
};

}} // namespace qpid::tests

using namespace qpid::tests;

int main(int argc, char** argv)
{
    try {
        opts.parse(argc, argv);
        Publish publish;
        Consume consume;
        publish.start();
        consume.start();
        consume.join();
        publish.join();
        return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
}


