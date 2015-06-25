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
#include <iomanip>
#include <iostream>
#include <memory>
#include <sstream>
#include <vector>

#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include <qpid/Options.h>
#include <qpid/log/Logger.h>
#include <qpid/log/Options.h>
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"

using namespace qpid::messaging;
using namespace qpid::sys;

namespace qpid {
namespace tests {

typedef std::vector<std::string> StringSet;

struct Options : public qpid::Options {
    bool help;
    bool init, transfer, check;//actions
    uint size;
    bool durable;
    uint queues;
    std::string base;
    uint msgsPerTx;
    uint txCount;
    uint totalMsgCount;
    bool dtx;
    uint capacity;
    std::string url;
    std::string connectionOptions;
    qpid::log::Options log;
    uint port;
    bool quiet;
    double fetchTimeout;

    Options() : help(false), init(true), transfer(true), check(true),
                size(256), durable(true), queues(2),
                base("tx"), msgsPerTx(1), txCount(5), totalMsgCount(10),
                capacity(1000), url("localhost"), port(0), quiet(false), fetchTimeout(5)
    {
        addOptions()
            ("init", qpid::optValue(init, "yes|no"), "Declare queues and populate one with the initial set of messages.")
            ("transfer", qpid::optValue(transfer, "yes|no"), "'Move' messages from one queue to another using transactions to ensure no message loss.")
            ("check", qpid::optValue(check, "yes|no"), "Check that the initial messages are all still available.")
            ("size", qpid::optValue(size, "N"), "message size")
            ("durable", qpid::optValue(durable, "yes|no"), "use durable messages")
            ("queues", qpid::optValue(queues, "N"), "number of queues")
            ("queue-base-name", qpid::optValue(base, "<name>"), "base name for queues")
            ("messages-per-tx", qpid::optValue(msgsPerTx, "N"), "number of messages transferred per transaction")
            ("tx-count", qpid::optValue(txCount, "N"), "number of transactions per 'agent'")
            ("total-messages", qpid::optValue(totalMsgCount, "N"), "total number of messages in 'circulation'")
            ("capacity", qpid::optValue(capacity, "N"), "Pre-fetch window (0 implies no pre-fetch)")
            ("broker,b", qpid::optValue(url, "URL"), "url of broker to connect to")
            ("connection-options", qpid::optValue(connectionOptions, "OPTIONS"), "options for the connection")
            ("port,p", qpid::optValue(port, "PORT"), "(for test compatibility only, use broker option instead)")
            ("quiet", qpid::optValue(quiet), "reduce output from test")
            ("fetch-timeout", qpid::optValue(fetchTimeout, "SECONDS"), "Timeout for transactional fetch")
            ("help", qpid::optValue(help), "print this usage statement");
        add(log);
    }

    bool parse(int argc, char** argv)
    {
        try {
            qpid::Options::parse(argc, argv);
            if (port) {
                if (url == "localhost") {
                    std::stringstream u;
                    u << url << ":" << port;
                    url = u.str();
                } else {
                    std::cerr << *this << std::endl << std::endl
                              << "--port and --broker should not be specified together; specify full url in --broker option" << std::endl;
                    return false;
                }

            }
            qpid::log::Logger::instance().configure(log);
            if (help) {
                std::cout << *this << std::endl << std::endl
                          << "Transactionally moves messages between queues" << std::endl;
                return false;
            }
            if (totalMsgCount < msgsPerTx) {
                totalMsgCount = msgsPerTx; // Must have at least msgsPerTx total messages.
            }
            return true;
        } catch (const std::exception& e) {
            std::cerr << *this << std::endl << std::endl << e.what() << std::endl;
            return false;
        }
    }
};

const std::string chars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");

std::string generateData(uint size)
{
    if (size < chars.length()) {
        return chars.substr(0, size);
    }
    std::string data;
    for (uint i = 0; i < (size / chars.length()); i++) {
        data += chars;
    }
    data += chars.substr(0, size % chars.length());
    return data;
}

void generateSet(const std::string& base, uint count, StringSet& collection)
{
    for (uint i = 0; i < count; i++) {
        std::ostringstream digits;
        digits << count;
        std::ostringstream out;
        out << base << "-" << std::setw(digits.str().size()) << std::setfill('0') << (i+1);
        collection.push_back(out.str());
    }
}

struct Client
{
    const Options& opts;
    Connection connection;
    Session session;

    Client(const Options& o, bool transactional=false) : opts(o), connection(opts.url, opts.connectionOptions)
    {
        connection.open();
        session = transactional ? connection.createTransactionalSession() : connection.createSession();
    }

    virtual ~Client()
    {
        try {
            session.sync();
            session.close();
            connection.close();
        } catch(const std::exception& e) {
            std::cout << "Client shutdown: " << e.what() << std::endl;
        }
    }
};

struct TransactionalClient : Client
{
    TransactionalClient(const Options& o) : Client(o, true) {}
    virtual ~TransactionalClient() {}
};

struct Transfer : public TransactionalClient, public Runnable
{
    const std::string target;
    const std::string source;
    Thread thread;
    bool failed;

    Transfer(const std::string& to, const std::string& from, const Options& opts) : TransactionalClient(opts), target(to), source(from), failed(false) {}

    void run()
    {
        try {

            Sender sender(session.createSender(target));
            Receiver receiver(session.createReceiver(source));
            receiver.setCapacity(opts.capacity);
            for (uint t = 0; t < opts.txCount;) {
                std::ostringstream id;
                id << source << ">" << target << ":" << t+1;
                try {
                    for (uint m = 0; m < opts.msgsPerTx; m++) {
                        Message msg = receiver.fetch(Duration::SECOND*uint64_t(opts.fetchTimeout));
                        if (msg.getContentSize() != opts.size) {
                            std::ostringstream oss;
                            oss << "Message size incorrect: size=" << msg.getContentSize() << "; expected " << opts.size;
                            throw std::runtime_error(oss.str());
                        }
                        sender.send(msg);
                    }
                    session.commit();
                    t++;
                    if (!opts.quiet) std::cout << "Transaction " << id.str() << " of " << opts.txCount << " committed successfully" << std::endl;
                } catch (const TransactionAborted&) {
                    std::cout << "Transaction " << id.str() << " of " << opts.txCount << " was aborted and will be retried" << std::endl;
                    session = connection.createTransactionalSession();
                    sender = session.createSender(target);
                    receiver = session.createReceiver(source);
                    receiver.setCapacity(opts.capacity);
                }
            }
            sender.close();
            receiver.close();
        } catch(const std::exception& e) {
            failed = true;
            QPID_LOG(error,  "Transfer " << source << " to " << target << " interrupted: " << e.what());
        }
    }
};

namespace {
const std::string CREATE_DURABLE("; {create:always, node:{durable:True}}");
const std::string CREATE_NON_DURABLE("; {create:always}");
}

struct Controller : public Client
{
    StringSet ids;
    StringSet queues;

    Controller(const Options& opts) : Client(opts)
    {
        generateSet(opts.base, opts.queues, queues);
        generateSet("msg", opts.totalMsgCount, ids);
    }

    void init()
    {
        Message msg(generateData(opts.size));
        msg.setDurable(opts.durable);

        for (StringSet::iterator i = queues.begin(); i != queues.end(); i++) {
            std::string address = *i + (opts.durable ? CREATE_DURABLE : CREATE_NON_DURABLE);

            // Clear out any garbage on queues.
            Receiver receiver = session.createReceiver(address);
            Message rmsg;
            uint count(0);
            while (receiver.fetch(rmsg, Duration::IMMEDIATE)) ++count;
            session.acknowledge();
            receiver.close();
            if (!opts.quiet) std::cout << "Cleaned up " << count << " messages from " << *i << std::endl;

            Sender sender = session.createSender(address);
            if (i == queues.begin()) {
                for (StringSet::iterator i = ids.begin(); i != ids.end(); i++) {
                    msg.setCorrelationId(*i);
                    sender.send(msg);
                }
            }
            sender.close();
        }
    }

    void transfer()
    {
        boost::ptr_vector<Transfer> agents(opts.queues);
        //launch transfer agents
        for (StringSet::iterator i = queues.begin(); i != queues.end(); i++) {
            StringSet::iterator next = i + 1;
            if (next == queues.end()) next = queues.begin();

            if (!opts.quiet) std::cout << "Transfering from " << *i << " to " << *next << std::endl;
            agents.push_back(new Transfer(*i, *next, opts));
            agents.back().thread = Thread(agents.back());
        }

        for (boost::ptr_vector<Transfer>::iterator i = agents.begin(); i != agents.end(); i++)
            i->thread.join();
        for (boost::ptr_vector<Transfer>::iterator i = agents.begin(); i != agents.end(); i++)
            if (i->failed)
                throw std::runtime_error("Transfer agents failed");
    }

    int check()
    {
        StringSet drained;
        //drain each queue and verify the correct set of messages are available
        for (StringSet::iterator i = queues.begin(); i != queues.end(); i++) {
            Receiver receiver = session.createReceiver(*i);
            uint count(0);
            Message msg;
            while (receiver.fetch(msg, Duration::IMMEDIATE)) {
                //add correlation ids of received messages to drained
                drained.push_back(msg.getCorrelationId());
                ++count;
            }
            session.acknowledge();
            receiver.close();
            if (!opts.quiet) std::cout << "Drained " << count << " messages from " << *i << std::endl;
        }
        sort(ids.begin(), ids.end());
        sort(drained.begin(), drained.end());

        //check that drained == ids
        StringSet missing;
        set_difference(ids.begin(), ids.end(), drained.begin(), drained.end(), back_inserter(missing));

        StringSet extra;
        set_difference(drained.begin(), drained.end(), ids.begin(), ids.end(), back_inserter(extra));

        if (missing.empty() && extra.empty()) {
            std::cout << "All expected messages were retrieved." << std::endl;
            return 0;
        } else {
            if (!missing.empty()) {
                std::cout << "The following ids were missing:" << std::endl;
                for (StringSet::iterator i = missing.begin(); i != missing.end(); i++) {
                    std::cout << "    '" << *i << "'" << std::endl;
                }
            }
            if (!extra.empty()) {
                std::cout << "The following extra ids were encountered:" << std::endl;
                for (StringSet::iterator i = extra.begin(); i != extra.end(); i++) {
                    std::cout << "    '" << *i << "'" << std::endl;
                }
            }
            return 1;
        }
    }
};
}} // namespace qpid::tests

using namespace qpid::tests;

int main(int argc, char** argv)
{
    try {
        Options opts;
        if (opts.parse(argc, argv)) {
            Controller controller(opts);
            if (opts.init) controller.init();
            if (opts.transfer) controller.transfer();
            if (opts.check) return controller.check();
            return 0;
        }
        return 1;
    } catch(const std::exception& e) {
	std::cerr << argv[0] << ": " << e.what() << std::endl;
    }
    return 2;
}
