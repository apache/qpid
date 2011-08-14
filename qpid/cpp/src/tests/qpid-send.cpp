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
#include <qpid/messaging/Message.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Session.h>
#include <qpid/messaging/FailoverUpdates.h>
#include <qpid/sys/Time.h>
#include <qpid/sys/Monitor.h>
#include "TestOptions.h"
#include "Statistics.h"

#include <fstream>
#include <iostream>
#include <memory>

using namespace std;
using namespace qpid::messaging;
using namespace qpid::types;

typedef std::vector<std::string> string_vector;

namespace qpid {
namespace tests {

struct Options : public qpid::Options
{
    bool help;
    std::string url;
    std::string connectionOptions;
    std::string address;
    uint messages;
    std::string id;
    std::string replyto;
    uint sendEos;
    bool durable;
    uint ttl;
    uint priority;
    std::string userid;
    std::string correlationid;
    string_vector properties;
    string_vector entries;
    std::string contentString;
    uint contentSize;
    bool contentStdin;
    uint tx;
    uint rollbackFrequency;
    uint capacity;
    bool failoverUpdates;
    qpid::log::Options log;
    bool reportTotal;
    uint reportEvery;
    bool reportHeader;
    uint sendRate;
    uint flowControl;
    bool sequence;
    bool timestamp;

    Options(const std::string& argv0=std::string())
        : qpid::Options("Options"),
          help(false),
          url("amqp:tcp:127.0.0.1"),
          messages(1),
          sendEos(0),
          durable(false),
          ttl(0),
          priority(0),
          contentString(),
          contentSize(0),
          contentStdin(false),
          tx(0),
          rollbackFrequency(0),
          capacity(1000),
          failoverUpdates(false),
          log(argv0),
          reportTotal(false),
          reportEvery(0),
          reportHeader(true),
          sendRate(0),
          flowControl(0),
          sequence(true),
          timestamp(true)
    {
        addOptions()
            ("broker,b", qpid::optValue(url, "URL"), "url of broker to connect to")
            ("address,a", qpid::optValue(address, "ADDRESS"), "address to send to")
            ("connection-options", qpid::optValue(connectionOptions, "OPTIONS"), "options for the connection")
            ("messages,m", qpid::optValue(messages, "N"), "stop after N messages have been sent, 0 means no limit")
            ("id,i", qpid::optValue(id, "ID"), "use the supplied id instead of generating one")
            ("reply-to", qpid::optValue(replyto, "REPLY-TO"), "specify reply-to address")
            ("send-eos", qpid::optValue(sendEos, "N"), "Send N EOS messages to mark end of input")
            ("durable", qpid::optValue(durable, "yes|no"), "Mark messages as durable.")
	    ("ttl", qpid::optValue(ttl, "msecs"), "Time-to-live for messages, in milliseconds")
	    ("priority", qpid::optValue(priority, "PRIORITY"), "Priority for messages (higher value implies higher priority)")
            ("property,P", qpid::optValue(properties, "NAME=VALUE"), "specify message property")
            ("correlation-id", qpid::optValue(correlationid, "ID"), "correlation-id for message")
            ("user-id", qpid::optValue(userid, "USERID"), "userid for message")
            ("content-string", qpid::optValue(contentString, "CONTENT"), "use CONTENT as message content")
            ("content-size", qpid::optValue(contentSize, "N"), "create an N-byte message content")
            ("content-map,M", qpid::optValue(entries, "NAME=VALUE"), "specify entry for map content")
            ("content-stdin", qpid::optValue(contentStdin), "read message content from stdin, one line per message")
            ("capacity", qpid::optValue(capacity, "N"), "size of the senders outgoing message queue")
            ("tx", qpid::optValue(tx, "N"), "batch size for transactions (0 implies transaction are not used)")
            ("rollback-frequency", qpid::optValue(rollbackFrequency, "N"), "rollback frequency (0 implies no transaction will be rolledback)")
            ("failover-updates", qpid::optValue(failoverUpdates), "Listen for membership updates distributed via amq.failover")
            ("report-total", qpid::optValue(reportTotal), "Report total throughput statistics")
            ("report-every", qpid::optValue(reportEvery,"N"), "Report throughput statistics every N messages")
            ("report-header", qpid::optValue(reportHeader, "yes|no"), "Headers on report.")
            ("send-rate", qpid::optValue(sendRate,"N"), "Send at rate of N messages/second. 0 means send as fast as possible.")
            ("flow-control", qpid::optValue(flowControl,"N"), "Do end to end flow control to limit queue depth to 2*N. 0 means no flow control.")
            ("sequence", qpid::optValue(sequence, "yes|no"), "Add a sequence number messages property (required for duplicate/lost message detection)")
            ("timestamp", qpid::optValue(timestamp, "yes|no"), "Add a time stamp messages property (required for latency measurement)")
            ("help", qpid::optValue(help), "print this usage statement");
        add(log);
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

    static bool nameval(const std::string& in, std::string& name, std::string& value)
    {
        std::string::size_type i = in.find("=");
        if (i == std::string::npos) {
            name = in;
            return false;
        } else {
            name = in.substr(0, i);
            if (i+1 < in.size()) {
                value = in.substr(i+1);
                return true;
            } else {
                return false;
            }
        }
    }

    static void setProperty(Message& message, const std::string& property)
    {
        std::string name;
        std::string value;
        if (nameval(property, name, value)) {
            message.getProperties()[name] = value;
        } else {
            message.getProperties()[name] = Variant();
        }
    }

    void setProperties(Message& message) const
    {
        for (string_vector::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            setProperty(message, *i);
        }
    }

    void setEntries(Variant::Map& content) const
    {
        for (string_vector::const_iterator i = entries.begin(); i != entries.end(); ++i) {
            std::string name;
            std::string value;
            if (nameval(*i, name, value)) {
                content[name] = value;
            } else {
                content[name] = Variant();
            }
        }
    }
};

const string EOS("eos");
const string SN("sn");
const string TS("ts");

}} // namespace qpid::tests

using namespace qpid::tests;

class ContentGenerator {
  public:
    virtual ~ContentGenerator() {}
    virtual bool setContent(Message& msg) = 0;
};

class GetlineContentGenerator : public ContentGenerator {
  public:
    virtual bool setContent(Message& msg) {
        string content;
        bool got = getline(std::cin, content);
        if (got) msg.setContent(content);
        return got;
    }
};

class FixedContentGenerator   : public ContentGenerator {
  public:
    FixedContentGenerator(const string& s) : content(s) {}
    virtual bool setContent(Message& msg) {
        msg.setContent(content);
        return true;
    }
  private:
    std::string content;
};

class MapContentGenerator   : public ContentGenerator {
  public:
    MapContentGenerator(const Options& opt) : opts(opt) {}
    virtual bool setContent(Message& msg) {
        Variant::Map map;
        opts.setEntries(map);
        encode(map, msg);
        return true;
    }
  private:
    const Options& opts;
};

int main(int argc, char ** argv)
{
    Connection connection;
    Options opts;
    try {
        if (opts.parse(argc, argv)) {
             connection = Connection(opts.url, opts.connectionOptions);
            connection.open();
            std::auto_ptr<FailoverUpdates> updates(opts.failoverUpdates ? new FailoverUpdates(connection) : 0);
            Session session = opts.tx ? connection.createTransactionalSession() : connection.createSession();
            Sender sender = session.createSender(opts.address);
            if (opts.capacity) sender.setCapacity(opts.capacity);
            Message msg;
            msg.setDurable(opts.durable);
            if (opts.ttl) {
                msg.setTtl(Duration(opts.ttl));
            }
            if (opts.priority) {
                msg.setPriority(opts.priority);
            }
            if (!opts.replyto.empty()) {
                if (opts.flowControl)
                    throw Exception("Can't use reply-to and flow-control together");
                msg.setReplyTo(Address(opts.replyto));
            }
            if (!opts.userid.empty()) msg.setUserId(opts.userid);
            if (!opts.correlationid.empty()) msg.setCorrelationId(opts.correlationid);
            opts.setProperties(msg);
            uint sent = 0;
            uint txCount = 0;
            Reporter<Throughput> reporter(std::cout, opts.reportEvery, opts.reportHeader);

            std::auto_ptr<ContentGenerator> contentGen;
            if (opts.contentStdin) {
                opts.messages = 0; // Don't limit # messages sent.
                contentGen.reset(new GetlineContentGenerator);
            }
            else if (opts.entries.size() > 0)
                contentGen.reset(new MapContentGenerator(opts));
            else if (opts.contentSize > 0)
                contentGen.reset(new FixedContentGenerator(string(opts.contentSize, 'X')));
            else
                contentGen.reset(new FixedContentGenerator(opts.contentString));

            qpid::sys::AbsTime start = qpid::sys::now();
            int64_t interval = 0;
            if (opts.sendRate) interval = qpid::sys::TIME_SEC/opts.sendRate;

            Receiver flowControlReceiver;
            Address flowControlAddress("flow-"+Uuid(true).str()+";{create:always,delete:always}");
            uint flowSent = 0;
            if (opts.flowControl) {
                flowControlReceiver = session.createReceiver(flowControlAddress);
                flowControlReceiver.setCapacity(2);
            }

            while (contentGen->setContent(msg)) {
                ++sent;
                if (opts.sequence)
                    msg.getProperties()[SN] = sent;
                if (opts.timestamp)
                    msg.getProperties()[TS] = int64_t(
                        qpid::sys::Duration(qpid::sys::EPOCH, qpid::sys::now()));
                if (opts.flowControl) {
                    if ((sent % opts.flowControl) == 0) {
                        msg.setReplyTo(flowControlAddress);
                        ++flowSent;
                    }
                    else
                        msg.setReplyTo(Address()); // Clear the reply address.
                }
                sender.send(msg);
                reporter.message(msg);

                if (opts.tx && (sent % opts.tx == 0)) {
                    if (opts.rollbackFrequency &&
                        (++txCount % opts.rollbackFrequency == 0))
                        session.rollback();
                    else
                        session.commit();
                }
                if (opts.messages && sent >= opts.messages) break;

                if (opts.flowControl && flowSent == 2) {
                    flowControlReceiver.get(Duration::SECOND);
                    --flowSent;
                }

                if (opts.sendRate) {
                    qpid::sys::AbsTime waitTill(start, sent*interval);
                    int64_t delay = qpid::sys::Duration(qpid::sys::now(), waitTill);
                    if (delay > 0) qpid::sys::usleep(delay/qpid::sys::TIME_USEC);
                }
            }
            for ( ; flowSent>0; --flowSent)
                flowControlReceiver.get(Duration::SECOND);
            if (opts.reportTotal) reporter.report();
            for (uint i = opts.sendEos; i > 0; --i) {
                if (opts.sequence)
                    msg.getProperties()[SN] = ++sent;
                msg.setContent(EOS); //TODO: add in ability to send digest or similar
                sender.send(msg);
            }
            if (opts.tx) {
                if (opts.rollbackFrequency && (++txCount % opts.rollbackFrequency == 0)) {
                    session.rollback();
                } else {
                    session.commit();
                }
            }
            session.sync();
            session.close();
            connection.close();
            return 0;
        }
    } catch(const std::exception& error) {
        std::cout << "Failed: " << error.what() << std::endl;
        connection.close();
        return 1;
    }
}
