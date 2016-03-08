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
#include <qpid/sys/SystemInfo.h>
#include "TestOptions.h"
#include "Statistics.h"

#include <fstream>
#include <iostream>
#include <memory>

using std::string;
using std::ios_base;

using qpid::messaging::Address;
using qpid::messaging::Connection;
using qpid::messaging::Duration;
using qpid::messaging::FailoverUpdates;
using qpid::messaging::Message;
using qpid::messaging::Receiver;
using qpid::messaging::Session;
using qpid::messaging::Sender;
using qpid::types::Exception;
using qpid::types::Uuid;
using qpid::types::Variant;

namespace qpid {
namespace tests {

typedef std::vector<std::string> string_vector;

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
    bool autouserid;
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
    bool sequence;
    bool timestamp;
    std::string groupKey;
    std::string groupPrefix;
    uint groupSize;
    bool groupRandSize;
    uint groupInterleave;

    Options(const std::string& argv0=std::string())
        : qpid::Options("Options"),
          help(false),
          url("127.0.0.1"),
          messages(1),
          sendEos(0),
          durable(false),
          ttl(0),
          priority(0),
          autouserid(false),
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
        sequence(true),
        timestamp(true),
        groupPrefix("GROUP-"),
        groupSize(10),
        groupRandSize(false),
        groupInterleave(1)
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
            ("auto-user-id", qpid::optValue(autouserid, "yes| no"), "set userid for message based on authenticated identity")
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
            ("sequence", qpid::optValue(sequence, "yes|no"), "Add a sequence number messages property (required for duplicate/lost message detection)")
            ("timestamp", qpid::optValue(timestamp, "yes|no"), "Add a time stamp messages property (required for latency measurement)")
            ("group-key", qpid::optValue(groupKey, "KEY"), "Generate groups of messages using message header 'KEY' to hold the group identifier")
            ("group-prefix", qpid::optValue(groupPrefix, "STRING"), "Generate group identifers with 'STRING' prefix (if group-key specified)")
            ("group-size", qpid::optValue(groupSize, "N"), "Number of messages per a group (if group-key specified)")
            ("group-randomize-size", qpid::optValue(groupRandSize), "Randomize the number of messages per group to [1...group-size] (if group-key specified)")
            ("group-interleave", qpid::optValue(groupInterleave, "N"), "Simultaineously interleave messages from N different groups (if group-key specified)")
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
                std::cout << *this << std::endl << std::endl
                          << "Sends messages to the specified address" << std::endl;
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
            message.getProperties()[name].parse(value);
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

class ContentGenerator {
  public:
    virtual ~ContentGenerator() {}
    virtual bool setContent(Message& msg) = 0;
    void setContentObject(Message& msg, const std::string& content, const std::string& encoding=std::string("utf8"))
    {
        Variant& obj = msg.getContentObject();
        obj = content;
        obj.setEncoding(encoding);
    }
};


class GetlineContentGenerator : public ContentGenerator {
  public:
    virtual bool setContent(Message& msg) {
        string content;
        bool got = !!getline(std::cin, content);
        if (got) {
            setContentObject(msg, content);
        }
        return got;
    }
};

class FixedContentGenerator   : public ContentGenerator {
  public:
    FixedContentGenerator(const string& s) : content(s) {}
    virtual bool setContent(Message& msg) {
        setContentObject(msg, content);
        return true;
    }
  private:
    std::string content;
};

class MapContentGenerator   : public ContentGenerator {
  public:
    MapContentGenerator(const Options& opt) : opts(opt) {}
    virtual bool setContent(Message& msg) {
        msg.getContentObject() = qpid::types::Variant::Map();
        opts.setEntries(msg.getContentObject().asMap());
        return true;
    }
  private:
    const Options& opts;
};

// tag each generated message with a group identifer
//
class GroupGenerator {
  public:
    GroupGenerator(const std::string& key,
                   const std::string& prefix,
                   const uint size,
                   const bool randomize,
                   const uint interleave)
        : groupKey(key), groupPrefix(prefix), groupSize(size),
          randomizeSize(randomize), groupSuffix(0)
    {
        if (randomize) srand((unsigned int)qpid::sys::SystemInfo::getProcessId());

        for (uint i = 0; i < 1 || i < interleave; ++i) {
            newGroup();
        }
        current = groups.begin();
    }

    void setGroupInfo(Message &msg)
    {
        if (current == groups.end())
            current = groups.begin();
        msg.getProperties()[groupKey] = current->id;
        // std::cout << "SENDING GROUPID=[" << current->id << "]" << std::endl;
        if (++(current->count) == current->size) {
            newGroup();
            groups.erase(current++);
        } else
            ++current;
    }

  private:
    const std::string& groupKey;
    const std::string& groupPrefix;
    const uint groupSize;
    const bool randomizeSize;

    uint groupSuffix;

    struct GroupState {
        std::string id;
        const uint size;
        uint count;
        GroupState( const std::string& i, const uint s )
            : id(i), size(s), count(0) {}
    };
    typedef std::list<GroupState> GroupList;
    GroupList groups;
    GroupList::iterator current;

    void newGroup() {
        std::ostringstream groupId(groupPrefix, ios_base::out|ios_base::ate);
        groupId << groupSuffix++;
        uint size = (randomizeSize) ? (rand() % groupSize) + 1 : groupSize;
        // std::cout << "New group: GROUPID=[" << groupId.str() << "] size=" << size << std::endl;
        GroupState group( groupId.str(), size );
        groups.push_back( group );
    }
};

}} // namespace qpid::tests

using qpid::tests::Options;
using qpid::tests::Reporter;
using qpid::tests::Throughput;
using qpid::tests::ContentGenerator;
using qpid::tests::GroupGenerator;
using qpid::tests::GetlineContentGenerator;
using qpid::tests::MapContentGenerator;
using qpid::tests::FixedContentGenerator;
using qpid::tests::SN;
using qpid::tests::TS;
using qpid::tests::EOS;

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
                msg.setReplyTo(Address(opts.replyto));
            }
            if (!opts.userid.empty()) msg.setUserId(opts.userid);
            else if (opts.autouserid) msg.setUserId(connection.getAuthenticatedUsername());
            if (!opts.id.empty()) msg.setMessageId(opts.id);
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

            std::auto_ptr<GroupGenerator> groupGen;
            if (!opts.groupKey.empty())
                groupGen.reset(new GroupGenerator(opts.groupKey,
                                                  opts.groupPrefix,
                                                  opts.groupSize,
                                                  opts.groupRandSize,
                                                  opts.groupInterleave));

            qpid::sys::AbsTime start = qpid::sys::now();
            int64_t interval = 0;
            if (opts.sendRate) interval = qpid::sys::TIME_SEC/opts.sendRate;

            while (contentGen->setContent(msg)) {
                ++sent;
                if (opts.sequence)
                    msg.getProperties()[SN] = sent;
                if (groupGen.get())
                    groupGen->setGroupInfo(msg);

                if (opts.timestamp)
                    msg.getProperties()[TS] = int64_t(
                        qpid::sys::Duration::FromEpoch());
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

                if (opts.sendRate) {
                    qpid::sys::AbsTime waitTill(start, sent*interval);
                    int64_t delay = qpid::sys::Duration(qpid::sys::now(), waitTill);
                    if (delay > 0) qpid::sys::usleep(delay/qpid::sys::TIME_USEC);
                }
            }
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
        return 1;
    } catch(const std::exception& error) {
        std::cerr << "qpid-send: " << error.what() << std::endl;
        connection.close();
        return 1;
    }
}
