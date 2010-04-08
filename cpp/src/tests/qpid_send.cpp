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
#include <qpid/messaging/Session.h>
#include <qpid/client/amqp0_10/FailoverUpdates.h>
#include <qpid/sys/Time.h>
#include <qpid/sys/Monitor.h>
#include "TestOptions.h"
#include "Statistics.h"

#include <fstream>
#include <iostream>
#include <memory>

using namespace qpid::messaging;
using namespace qpid::types;
using qpid::client::amqp0_10::FailoverUpdates;
typedef std::vector<std::string> string_vector;

using namespace std;

namespace qpid {
namespace tests {

struct Options : public qpid::Options
{
    bool help;
    std::string url;
    std::string connectionOptions;
    std::string address;
    uint count;
    std::string id;
    std::string replyto;
    uint sendEos;
    bool durable;
    uint ttl;
    std::string userid;
    std::string correlationid;
    string_vector properties;
    string_vector entries;
    std::string content;
    uint tx;
    uint rollbackFrequency;
    uint capacity;
    bool failoverUpdates;
    qpid::log::Options log;
    bool reportTotal;
    uint reportEvery;
    uint rate;

    Options(const std::string& argv0=std::string())
        : qpid::Options("Options"),
          help(false),
          url("amqp:tcp:127.0.0.1"),
          count(0),
          sendEos(0),
          durable(false),
          ttl(0),
          tx(0),
          rollbackFrequency(0),
          capacity(1000),
          failoverUpdates(false),
          log(argv0),
          reportTotal(false),
          reportEvery(0),
          rate(0)
    {
        addOptions()
            ("broker,b", qpid::optValue(url, "URL"), "url of broker to connect to")
            ("address,a", qpid::optValue(address, "ADDRESS"), "address to drain from")
            ("connection-options", qpid::optValue(connectionOptions, "OPTIONS"), "options for the connection")
            ("count,c", qpid::optValue(count, "COUNT"), "stop after count messages have been sent, zero disables")
            ("id,i", qpid::optValue(id, "ID"), "use the supplied id instead of generating one")
            ("reply-to", qpid::optValue(replyto, "REPLY-TO"), "specify reply-to address")
            ("send-eos", qpid::optValue(sendEos, "N"), "Send N EOS messages to mark end of input")
            ("durable", qpid::optValue(durable, "yes|no"), "Mark messages as durable.")
	    ("ttl", qpid::optValue(ttl, "msecs"), "Time-to-live for messages, in milliseconds")
            ("property,P", qpid::optValue(properties, "NAME=VALUE"), "specify message property")
            ("map,M", qpid::optValue(entries, "NAME=VALUE"), "specify entry for map content")
            ("correlation-id", qpid::optValue(correlationid, "ID"), "correlation-id for message")
            ("user-id", qpid::optValue(userid, "USERID"), "userid for message")
            ("content", qpid::optValue(content, "CONTENT"), "use CONTENT as message content instead of reading from stdin")
            ("capacity", qpid::optValue(capacity, "N"), "size of the senders outgoing message queue")
            ("tx", qpid::optValue(tx, "N"), "batch size for transactions (0 implies transaction are not used)")
            ("rollback-frequency", qpid::optValue(rollbackFrequency, "N"), "rollback frequency (0 implies no transaction will be rolledback)")
            ("failover-updates", qpid::optValue(failoverUpdates), "Listen for membership updates distributed via amq.failover")
            ("report-total", qpid::optValue(reportTotal), "Report total throughput statistics")
            ("report-every", qpid::optValue(reportEvery,"N"), "Report throughput statistics every N messages")
            ("rate", qpid::optValue(rate,"N"), "Send at rate of N messages/second. 0 means send as fast as possible.")
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

}} // namespace qpid::tests

using namespace qpid::tests;

class ContentGenerator {
  public:
    virtual ~ContentGenerator() {}
    virtual bool getContent(std::string& content) = 0;
};

class GetlineContentGenerator : public ContentGenerator {
  public:
    virtual bool getContent(std::string& content) { return getline(std::cin, content); }
};

class FixedContentGenerator   : public ContentGenerator {
  public:
    FixedContentGenerator(std::string s) : content(s) {}
    virtual bool getContent(std::string& contentOut) {
        contentOut = content;
        return true;
    }
  private:
    std::string content;
};



int main(int argc, char ** argv)
{
    Options opts;
    if (opts.parse(argc, argv)) {
        Connection connection(opts.connectionOptions);
        try {
            connection.open(opts.url);
            std::auto_ptr<FailoverUpdates> updates(opts.failoverUpdates ? new FailoverUpdates(connection) : 0);
            Session session = opts.tx ? connection.createTransactionalSession() : connection.createSession();
            Sender sender = session.createSender(opts.address);
            if (opts.capacity) sender.setCapacity(opts.capacity);
            Message msg;
            msg.setDurable(opts.durable);
            if (opts.ttl) {
                msg.setTtl(Duration(opts.ttl));
            }
            if (!opts.replyto.empty()) msg.setReplyTo(Address(opts.replyto));
            if (!opts.userid.empty()) msg.setUserId(opts.userid);
            if (!opts.correlationid.empty()) msg.setCorrelationId(opts.correlationid);
            opts.setProperties(msg);
            std::string content;
            uint sent = 0;
            uint txCount = 0;
            Reporter<Throughput> reporter(std::cout, opts.reportEvery);

            std::auto_ptr<ContentGenerator> contentGen;
            if (!opts.content.empty())
                contentGen.reset(new FixedContentGenerator(opts.content));
            else
                contentGen.reset(new GetlineContentGenerator);

            qpid::sys::AbsTime start = qpid::sys::now();
            int64_t interval = 0;
            if (opts.rate) interval = qpid::sys::TIME_SEC/opts.rate;

            while (contentGen->getContent(content)) {
                msg.setContent(content);
                msg.getProperties()["sn"] = ++sent;
                msg.getProperties()["ts"] = int64_t(
                    qpid::sys::Duration(qpid::sys::EPOCH, qpid::sys::now()));
                sender.send(msg);
                reporter.message(msg);
                if (opts.tx && (sent % opts.tx == 0)) {
                    if (opts.rollbackFrequency &&
                        (++txCount % opts.rollbackFrequency == 0))
                        session.rollback();
                    else
                        session.commit();
                }
                if (opts.count && sent >= opts.count) break;
                if (opts.rate) {
                    qpid::sys::AbsTime waitTill(start, sent*interval);
                    int64_t delay = qpid::sys::Duration(qpid::sys::now(), waitTill);
                    if (delay > 0)
                        qpid::sys::usleep(delay/qpid::sys::TIME_USEC);
                }
            }
            if (opts.reportTotal) reporter.report();
            for (uint i = opts.sendEos; i > 0; --i) {
                msg.getProperties()["sn"] = ++sent;
                msg.setContent(EOS);//TODO: add in ability to send digest or similar
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
        } catch(const std::exception& error) {
            std::cout << "Failed: " << error.what() << std::endl;
            connection.close();
        }
    }
    return 1;
}
