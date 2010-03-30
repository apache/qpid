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

#include <qpid/messaging/Address.h>
#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Session.h>
#include <qpid/messaging/Message.h>
#include <qpid/Options.h>
#include <qpid/log/Logger.h>
#include <qpid/log/Options.h>
#include <qpid/client/amqp0_10/FailoverUpdates.h>
#include "TestOptions.h"

#include <iostream>
#include <memory>

using namespace qpid::messaging;
using namespace qpid::types;
using qpid::client::amqp0_10::FailoverUpdates;

using namespace std;

namespace qpid {
namespace tests {

struct Options : public qpid::Options
{
    bool help;
    std::string url;
    std::string address;
    std::string connectionOptions;
    int64_t timeout;
    bool forever;
    uint messages;
    bool ignoreDuplicates;
    uint capacity;
    uint ackFrequency;
    uint tx;
    uint rollbackFrequency;
    bool printHeaders;
    bool failoverUpdates;
    qpid::log::Options log;

    Options(const std::string& argv0=std::string())
        : qpid::Options("Options"),
          help(false),
          url("amqp:tcp:127.0.0.1"),
          timeout(0),
          forever(false),
          messages(0),
          ignoreDuplicates(false),
          capacity(0),
          ackFrequency(1),
          tx(0),
          rollbackFrequency(0),
          printHeaders(false),
          failoverUpdates(false),
          log(argv0)
    {
        addOptions()
            ("broker,b", qpid::optValue(url, "URL"), "url of broker to connect to")
            ("address,a", qpid::optValue(address, "ADDRESS"), "address to receive from")
            ("connection-options", qpid::optValue(connectionOptions, "OPTIONS"), "options for the connection")
            ("timeout,t", qpid::optValue(timeout, "TIMEOUT"), "timeout in seconds to wait before exiting")
            ("forever,f", qpid::optValue(forever), "ignore timeout and wait forever")
            ("messages", qpid::optValue(messages, "N"), "Number of messages to receive; 0 means receive indefinitely")
            ("ignore-duplicates", qpid::optValue(ignoreDuplicates), "Detect and ignore duplicates (by checking 'sn' header)")
            ("capacity", qpid::optValue(capacity, "N"), "Credit window (0 implies infinite window)")
            ("ack-frequency", qpid::optValue(ackFrequency, "N"), "Ack frequency (0 implies none of the messages will get accepted)")
            ("tx", qpid::optValue(tx, "N"), "batch size for transactions (0 implies transaction are not used)")
            ("rollback-frequency", qpid::optValue(rollbackFrequency, "N"), "rollback frequency (0 implies no transaction will be rolledback)")
            ("print-headers", qpid::optValue(printHeaders), "If specified print out all message headers as well as content")
            ("failover-updates", qpid::optValue(failoverUpdates), "Listen for membership updates distributed via amq.failover")
            ("help", qpid::optValue(help), "print this usage statement");
        add(log);
    }

    Duration getTimeout()
    {
        if (forever) return Duration::FOREVER;
        else return Duration::SECOND*timeout;

    }
    bool parse(int argc, char** argv)
    {
        try {
            qpid::Options::parse(argc, argv);
            if (address.empty()) throw qpid::Exception("Address must be specified!");
            qpid::log::Logger::instance().configure(log);
            if (help) {
                std::ostringstream msg;
                std::cout << msg << *this << std::endl << std::endl 
                          << "Drains messages from the specified address" << std::endl;
                return false;
            } else {
                return true;
            }
        } catch (const std::exception& e) {
            std::cerr << *this << std::endl << std::endl << e.what() << std::endl;
            return false;
        }
    }
};

const string EOS("eos");

class SequenceTracker
{
    uint lastSn;
  public:
    SequenceTracker() : lastSn(0) {}

    bool isDuplicate(Message& message)
    {
        uint sn = message.getProperties()["sn"];
        if (lastSn < sn) {
            lastSn = sn;
            return false;
        } else {
            return true;
        }
    }
};

}} // namespace qpid::tests

using namespace qpid::tests;

int main(int argc, char ** argv)
{
    Options opts;
    if (opts.parse(argc, argv)) {
        Connection connection(opts.connectionOptions);
        try {
            connection.open(opts.url);
            std::auto_ptr<FailoverUpdates> updates(opts.failoverUpdates ? new FailoverUpdates(connection) : 0);
            Session session = connection.newSession(opts.tx > 0);
            Receiver receiver = session.createReceiver(opts.address);
            receiver.setCapacity(opts.capacity);
            Message msg;
            uint count = 0;
            uint txCount = 0;
            SequenceTracker sequenceTracker;
            Duration timeout = opts.getTimeout();
            bool done = false;
            while (!done && receiver.fetch(msg, timeout)) {
                if (!opts.ignoreDuplicates || !sequenceTracker.isDuplicate(msg)) {
                    if (msg.getContent() == EOS) {
                        done = true;
                    } else {
                        ++count;
                        if (opts.printHeaders) {
                            if (msg.getSubject().size()) std::cout << "Subject: " << msg.getSubject() << std::endl;
                            if (msg.getReplyTo()) std::cout << "ReplyTo: " << msg.getReplyTo() << std::endl;
                            if (msg.getCorrelationId().size()) std::cout << "CorrelationId: " << msg.getCorrelationId() << std::endl;
                            if (msg.getUserId().size()) std::cout << "UserId: " << msg.getUserId() << std::endl;
                            if (msg.getTtl().getMilliseconds()) std::cout << "TTL: " << msg.getTtl().getMilliseconds() << std::endl;
                            if (msg.getDurable()) std::cout << "Durable: true" << std::endl;
                            if (msg.getRedelivered()) std::cout << "Redelivered: true" << std::endl;
                            std::cout << "Properties: " << msg.getProperties() << std::endl;
                            std::cout << std::endl;
                        }
                        std::cout << msg.getContent() << std::endl;//TODO: handle map or list messages
                        if (opts.messages && count >= opts.messages) done = true;
                    }
                }
                if (opts.tx && (count % opts.tx == 0)) {
                    if (opts.rollbackFrequency && (++txCount % opts.rollbackFrequency == 0)) {
                        session.rollback();
                    } else {
                        session.commit();
                    }
                } else if (opts.ackFrequency && (count % opts.ackFrequency == 0)) {
                    session.acknowledge();
                }
                //opts.rejectFrequency??
            }
            if (opts.tx) {
                if (opts.rollbackFrequency && (++txCount % opts.rollbackFrequency == 0)) {
                    session.rollback();
                } else {
                    session.commit();
                }
            } else {
                session.acknowledge();
            }
            session.close();
            connection.close();
            return 0;
        } catch(const std::exception& error) {
            std::cerr << "Failure: " << error.what() << std::endl;
            connection.close();
        }
    }
    return 1;
}
