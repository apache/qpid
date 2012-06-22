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
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Session.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/FailoverUpdates.h>
#include <qpid/Options.h>
#include <qpid/log/Logger.h>
#include <qpid/log/Options.h>
#include "qpid/sys/Time.h"
#include "TestOptions.h"
#include "Statistics.h"

#include <iostream>
#include <memory>

using namespace qpid::messaging;
using namespace qpid::types;
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
    bool verifySequence;
    bool checkRedelivered;
    uint capacity;
    uint ackFrequency;
    uint tx;
    uint rollbackFrequency;
    bool printContent;
    bool printHeaders;
    bool failoverUpdates;
    qpid::log::Options log;
    bool reportTotal;
    uint reportEvery;
    bool reportHeader;
    string readyAddress;
    uint receiveRate;
    std::string replyto;

    Options(const std::string& argv0=std::string())
        : qpid::Options("Options"),
          help(false),
          url("amqp:tcp:127.0.0.1"),
          timeout(0),
          forever(false),
          messages(0),
          ignoreDuplicates(false),
          verifySequence(false),
          checkRedelivered(false),
          capacity(1000),
          ackFrequency(100),
          tx(0),
          rollbackFrequency(0),
          printContent(true),
          printHeaders(false),
          failoverUpdates(false),
          log(argv0),
          reportTotal(false),
          reportEvery(0),
          reportHeader(true),
          receiveRate(0)
    {
        addOptions()
            ("broker,b", qpid::optValue(url, "URL"), "url of broker to connect to")
            ("address,a", qpid::optValue(address, "ADDRESS"), "address to receive from")
            ("connection-options", qpid::optValue(connectionOptions, "OPTIONS"), "options for the connection")
            ("timeout", qpid::optValue(timeout, "TIMEOUT"), "timeout in seconds to wait before exiting")
            ("forever,f", qpid::optValue(forever), "ignore timeout and wait forever")
            ("messages,m", qpid::optValue(messages, "N"), "Number of messages to receive; 0 means receive indefinitely")
            ("ignore-duplicates", qpid::optValue(ignoreDuplicates), "Detect and ignore duplicates (by checking 'sn' header)")
            ("verify-sequence", qpid::optValue(verifySequence), "Verify there are no gaps in the message sequence (by checking 'sn' header)")
            ("check-redelivered", qpid::optValue(checkRedelivered), "Fails with exception if a duplicate is not marked as redelivered (only relevant when ignore-duplicates is selected)")
            ("capacity", qpid::optValue(capacity, "N"), "Pre-fetch window (0 implies no pre-fetch)")
            ("ack-frequency", qpid::optValue(ackFrequency, "N"), "Ack frequency (0 implies none of the messages will get accepted)")
            ("tx", qpid::optValue(tx, "N"), "batch size for transactions (0 implies transaction are not used)")
            ("rollback-frequency", qpid::optValue(rollbackFrequency, "N"), "rollback frequency (0 implies no transaction will be rolledback)")
            ("print-content", qpid::optValue(printContent, "yes|no"), "print out message content")
            ("print-headers", qpid::optValue(printHeaders, "yes|no"), "print out message headers")
            ("failover-updates", qpid::optValue(failoverUpdates), "Listen for membership updates distributed via amq.failover")
            ("report-total", qpid::optValue(reportTotal), "Report total throughput and latency statistics")
            ("report-every", qpid::optValue(reportEvery,"N"), "Report throughput and latency statistics every N messages.")
            ("report-header", qpid::optValue(reportHeader, "yes|no"), "Headers on report.")
            ("ready-address", qpid::optValue(readyAddress, "ADDRESS"), "send a message to this address when ready to receive")
            ("receive-rate", qpid::optValue(receiveRate,"N"), "Receive at rate of N messages/second. 0 means receive as fast as possible.")
            ("reply-to", qpid::optValue(replyto, "REPLY-TO"), "specify reply-to address on response messages")
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
const string SN("sn");

/** Check for duplicate or dropped messages by sequence number */
class SequenceTracker
{
  public:
    SequenceTracker(const Options& o) : opts(o), lastSn(0) {}

    /** Return true if the message should be procesed, false if it should be ignored. */
    bool track(Message& message) {
        if (!(opts.verifySequence || opts.ignoreDuplicates))
            return true;        // Not checking sequence numbers.
        uint sn = message.getProperties()[SN];
        bool duplicate = (sn <= lastSn);
        bool dropped = (sn > lastSn+1);
        if (opts.verifySequence && dropped)
            throw Exception(QPID_MSG("Gap in sequence numbers " << lastSn << "-" << sn));
        bool ignore = duplicate && opts.ignoreDuplicates;
        if (ignore && opts.checkRedelivered && !message.getRedelivered())
            throw qpid::Exception("duplicate sequence number received, message not marked as redelivered!");
        if (!duplicate) lastSn = sn;
        return !ignore;
    }

  private:
    const Options& opts;
    uint lastSn;
};

}} // namespace qpid::tests

using namespace qpid::tests;

int main(int argc, char ** argv)
{
    Connection connection;
    try {
        Options opts;
        if (opts.parse(argc, argv)) {
            connection = Connection(opts.url, opts.connectionOptions);
            connection.open();
            std::auto_ptr<FailoverUpdates> updates(opts.failoverUpdates ? new FailoverUpdates(connection) : 0);
            Session session = opts.tx ? connection.createTransactionalSession() : connection.createSession();
            Receiver receiver = session.createReceiver(opts.address);
            receiver.setCapacity(opts.capacity);
            Message msg;
            uint count = 0;
            uint txCount = 0;
            SequenceTracker sequenceTracker(opts);
            Duration timeout = opts.getTimeout();
            bool done = false;
            Reporter<ThroughputAndLatency> reporter(std::cout, opts.reportEvery, opts.reportHeader);
            if (!opts.readyAddress.empty())
                session.createSender(opts.readyAddress).send(msg);
            // For receive rate calculation
            qpid::sys::AbsTime start = qpid::sys::now();
            int64_t interval = 0;
            if (opts.receiveRate) interval = qpid::sys::TIME_SEC/opts.receiveRate;

            std::map<std::string,Sender> replyTo;

            while (!done && receiver.fetch(msg, timeout)) {
                reporter.message(msg);
                if (sequenceTracker.track(msg)) {
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
                            if (msg.getPriority()) std::cout << "Priority: " << msg.getPriority() << std::endl;
                            if (msg.getDurable()) std::cout << "Durable: true" << std::endl;
                            if (msg.getRedelivered()) std::cout << "Redelivered: true" << std::endl;
                            std::cout << "Properties: " << msg.getProperties() << std::endl;
                            std::cout << std::endl;
                        }
                        if (opts.printContent)
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
                if (msg.getReplyTo()) { // Echo message back to reply-to address.
                    Sender& s = replyTo[msg.getReplyTo().str()];
                    if (s.isNull()) {
                        s = session.createSender(msg.getReplyTo());
                        s.setCapacity(opts.capacity);
                    }
                    if (!opts.replyto.empty()) {
                        msg.setReplyTo(Address(opts.replyto));
                    }
                    s.send(msg);
                }
                if (opts.receiveRate) {
                    qpid::sys::AbsTime waitTill(start, count*interval);
                    int64_t delay = qpid::sys::Duration(qpid::sys::now(), waitTill);
                    if (delay > 0) qpid::sys::usleep(delay/qpid::sys::TIME_USEC);
                }
                // Clear out message properties & content for next iteration.
                msg = Message(); // TODO aconway 2010-12-01: should be done by fetch
            }
            if (opts.reportTotal) reporter.report();
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
        }
    } catch(const std::exception& error) {
        std::cerr << "qpid-receive: " << error.what() << std::endl;
        connection.close();
        return 1;
    }
}
