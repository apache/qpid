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
#include <qpid/messaging/MapContent.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Session.h>
#include "TestOptions.h"

#include <fstream>
#include <iostream>

using namespace qpid::messaging;
using qpid::framing::Uuid;
using qpid::sys::AbsTime;
using qpid::sys::now;
using qpid::sys::TIME_INFINITE;

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
    int64_t timeout;
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
    qpid::log::Options log;

    Options(const std::string& argv0=std::string())
        : qpid::Options("Options"),
          help(false),
          url("amqp:tcp:127.0.0.1"),
          timeout(TIME_INFINITE),
          count(1),
          sendEos(0),
          durable(false),
          ttl(0),
          tx(0),
          rollbackFrequency(0),
          log(argv0)
    {
        addOptions()
            ("broker,b", qpid::optValue(url, "URL"), "url of broker to connect to")
            ("address,a", qpid::optValue(address, "ADDRESS"), "address to drain from")
            ("connection-options", qpid::optValue(connectionOptions, "OPTIONS"), "options for the connection")
            ("timeout,t", qpid::optValue(timeout, "TIMEOUT"), "exit after the specified time")
            ("count,c", qpid::optValue(count, "COUNT"), "stop after count messages have been sent, zero disables")
            ("id,i", qpid::optValue(id, "ID"), "use the supplied id instead of generating one")
            ("reply-to", qpid::optValue(replyto, "REPLY-TO"), "specify reply-to address")
            ("send-eos", qpid::optValue(sendEos, "N"), "Send N EOS messages to mark end of input")
            ("durable", qpid::optValue(durable, "true|false"), "Mark messages as durable.")
	    ("ttl", qpid::optValue(ttl, "msecs"), "Time-to-live for messages, in milliseconds")
            ("property,P", qpid::optValue(properties, "NAME=VALUE"), "specify message property")
            ("map,M", qpid::optValue(entries, "NAME=VALUE"), "specify entry for map content")
            ("correlation-id", qpid::optValue(correlationid, "ID"), "correlation-id for message")
            ("user-id", qpid::optValue(userid, "USERID"), "userid for message")
            ("content", qpid::optValue(content, "CONTENT"), "specify textual content")
            ("tx", qpid::optValue(tx, "N"), "batch size for transactions (0 implies transaction are not used)")
            ("rollback-frequency", qpid::optValue(rollbackFrequency, "N"), "rollback frequency (0 implies no transaction will be rolledback)")
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
            message.getHeaders()[name] = value;
        } else {
            message.getHeaders()[name] = Variant();
        }    
    }

    void setProperties(Message& message) const
    {
        for (string_vector::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            setProperty(message, *i);
        }
    }

    void setEntries(MapContent& content) const
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

int main(int argc, char ** argv)
{
    Options opts;
    if (opts.parse(argc, argv)) {
        try {
            Variant::Map connectionOptions;
            if (opts.connectionOptions.size()) {
                parseOptionString(opts.connectionOptions, connectionOptions);
            }
            Connection connection =  Connection::open(opts.url, connectionOptions);
            Session session = connection.newSession(opts.tx > 0);
            Sender sender = session.createSender(opts.address);
            Message msg;
            msg.setDurable(opts.durable);
            if (opts.ttl) {
                msg.setTtl(opts.ttl);
            }
            if (!opts.replyto.empty()) msg.setReplyTo(Address(opts.replyto));
            if (!opts.userid.empty()) msg.setUserId(opts.userid);
            if (!opts.correlationid.empty()) msg.setCorrelationId(opts.correlationid);
            opts.setProperties(msg);
            std::string content;
            uint sent = 0;
            uint txCount = 0;
            while (getline(std::cin, content)) {
                msg.setContent(content);
                msg.getHeaders()["sn"] = ++sent;
                sender.send(msg);
                if (opts.tx && (sent % opts.tx == 0)) {
                    if (opts.rollbackFrequency && (++txCount % opts.rollbackFrequency == 0)) {
                        session.rollback();
                    } else {
                        session.commit();
                    }
                }                
            }
            for (uint i = opts.sendEos; i > 0; --i) {
                msg.getHeaders()["sn"] = ++sent;
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
        }
    }
    return 1;
}
