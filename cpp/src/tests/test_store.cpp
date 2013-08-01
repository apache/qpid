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


/**@file
 * Plug-in message store for tests.
 *
 * Add functionality as required, build up a comprehensive set of
 * features to support persistent behavior tests.
 *
 * Current features special "action" messages can:
 *  - raise exception from enqueue.
 *  - force host process to exit.
 *  - do async completion after a delay.
 */

#include "qpid/broker/NullMessageStore.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Thread.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include "qpid/RefCounted.h"
#include "qpid/Msg.h"
#include <boost/cast.hpp>
#include <boost/lexical_cast.hpp>
#include <memory>
#include <ostream>
#include <fstream>
#include <sstream>

using namespace std;
using namespace boost;
using namespace qpid;
using namespace qpid::broker;
using namespace qpid::sys;

namespace qpid {
namespace tests {

struct TestStoreOptions : public Options {

    string name;
    string dump;
    string events;

    TestStoreOptions() : Options("Test Store Options") {
        addOptions()
            ("test-store-name", optValue(name, "NAME"), "Name of test store instance.")
            ("test-store-dump", optValue(dump, "FILE"), "File to dump enqueued messages.")
            ("test-store-events", optValue(events, "FILE"), "File to log events, 1 line per event.")
            ;
    }
};

struct Completer : public Runnable {
    boost::intrusive_ptr<PersistableMessage> message;
    int usecs;
    Completer(boost::intrusive_ptr<PersistableMessage> m, int u) : message(m), usecs(u) {}
    void run() {
        qpid::sys::usleep(usecs);
        message->enqueueComplete();
        delete this;
    }
};

class TestStore : public NullMessageStore {
  public:
    TestStore(const TestStoreOptions& opts, Broker& broker_)
        : options(opts), name(opts.name), broker(broker_)
    {
        QPID_LOG(info, "TestStore name=" << name
                 << " dump=" << options.dump
                 << " events=" << options.events);

        if (!options.dump.empty())
            dump.reset(new ofstream(options.dump.c_str()));
        if (!options.events.empty())
            events.reset(new ofstream(options.events.c_str()));
    }

    ~TestStore() {
        for_each(threads.begin(), threads.end(), boost::bind(&Thread::join, _1));
    }

    // Dummy transaction context.
    struct TxContext : public TPCTransactionContext {
        static int nextId;
        string id;
        TxContext() : id(lexical_cast<string>(nextId++)) {}
        TxContext(string xid) : id(xid) {}
    };

    static string getId(const TransactionContext& tx) {
        const TxContext* tc = dynamic_cast<const TxContext*>(&tx);
        assert(tc);
        return tc->id;
    }


    bool isNull() const { return false; }

    void log(const string& msg) {
        QPID_LOG(info, "test_store: " << msg);
        if (events.get()) *events << msg << endl << std::flush;
    }

    auto_ptr<TransactionContext> begin() {
        auto_ptr<TxContext> tx(new TxContext());
        log(Msg() << "<begin tx " << tx->id << ">");
        return auto_ptr<TransactionContext>(tx);
    }

    auto_ptr<TPCTransactionContext> begin(const std::string& xid)  {
        auto_ptr<TxContext> tx(new TxContext(xid));
        log(Msg() << "<begin tx " << tx->id << ">");
        return auto_ptr<TPCTransactionContext>(tx);
    }

    string getContent(const intrusive_ptr<PersistableMessage>& msg) {
        intrusive_ptr<broker::Message::Encoding> enc(
            dynamic_pointer_cast<broker::Message::Encoding>(msg));
        return enc->getContent();
    }

    void enqueue(TransactionContext* tx,
                 const boost::intrusive_ptr<PersistableMessage>& pmsg,
                 const PersistableQueue& queue)
    {
        QPID_LOG(debug, "TestStore enqueue " << queue.getName());
        qpid::broker::amqp_0_10::MessageTransfer* msg = dynamic_cast<qpid::broker::amqp_0_10::MessageTransfer*>(pmsg.get());
        assert(msg);

        ostringstream o;
        o << "<enqueue " << queue.getName() << " " << getContent(msg);
        if (tx) o << " tx=" << getId(*tx);
        o << ">";
        log(o.str());

        // Dump the message if there is a dump file.
        if (dump.get()) {
            msg->getFrames().getMethod()->print(*dump);
            *dump  << endl << "  ";
            msg->getFrames().getHeaders()->print(*dump);
            *dump << endl << "  ";
            *dump << msg->getFrames().getContentSize() << endl;
        }

        // Check the message for special instructions.
        string data = msg->getFrames().getContent();
        size_t i = string::npos;
        size_t j = string::npos;
        if (strncmp(data.c_str(), TEST_STORE_DO.c_str(), strlen(TEST_STORE_DO.c_str())) == 0
            && (i = data.find(name+"[")) != string::npos
            && (j = data.find("]", i)) != string::npos)
        {
            size_t start = i+name.size()+1;
            string action = data.substr(start, j-start);

            if (action == EXCEPTION) {
                throw Exception(QPID_MSG("TestStore " << name << " throwing exception for: " << data));
            }
            else if (action == EXIT_PROCESS) {
                // FIXME aconway 2009-04-10: this is a dubious way to
                // close the process at best, it can cause assertions or seg faults
                // rather than clean exit.
                QPID_LOG(critical, "TestStore " << name << " forcing process exit for: " << data);
                exit(0);
            }
            else if (strncmp(action.c_str(), ASYNC.c_str(), strlen(ASYNC.c_str())) == 0) {
                std::string delayStr(action.substr(ASYNC.size()));
                int delay = boost::lexical_cast<int>(delayStr);
                threads.push_back(Thread(*new Completer(msg, delay)));
            }
            else {
                QPID_LOG(error, "TestStore " << name << " unknown action " << action);
                msg->enqueueComplete();
            }
        }
        else
            msg->enqueueComplete();
    }

    void dequeue(TransactionContext* tx,
                 const boost::intrusive_ptr<PersistableMessage>& msg,
                 const PersistableQueue& queue)
    {
        QPID_LOG(debug, "TestStore dequeue " << queue.getName());
        ostringstream o;
        o<< "<dequeue " << queue.getName() << " " << getContent(msg);
        if (tx) o << " tx=" << getId(*tx);
        o << ">";
        log(o.str());
    }

    void prepare(TPCTransactionContext& txn) {
        log(Msg() << "<prepare tx=" << getId(txn) << ">");
    }

    void commit(TransactionContext& txn) {
        log(Msg() << "<commit tx=" << getId(txn) << ">");
    }

    void abort(TransactionContext& txn) {
        log(Msg() << "<abort tx=" << getId(txn) << ">");
    }


  private:
    static const string TEST_STORE_DO, EXCEPTION, EXIT_PROCESS, ASYNC;
    TestStoreOptions options;
    string name;
    Broker& broker;
    vector<Thread> threads;
    std::auto_ptr<ofstream> dump;
    std::auto_ptr<ofstream> events;
};

int TestStore::TxContext::nextId(1);

const string TestStore::TEST_STORE_DO = "TEST_STORE_DO: ";
const string TestStore::EXCEPTION = "exception";
const string TestStore::EXIT_PROCESS = "exit_process";
const string TestStore::ASYNC="async ";

struct TestStorePlugin : public Plugin {

    TestStoreOptions options;

    Options* getOptions() { return &options; }

    void earlyInitialize (Plugin::Target& target)
    {
        Broker* broker = dynamic_cast<Broker*>(&target);
        if (!broker) return;
        boost::shared_ptr<MessageStore> p(new TestStore(options, *broker));
        broker->setStore (p);
    }

    void initialize(qpid::Plugin::Target&) {}
};

static TestStorePlugin pluginInstance;

}} // namespace qpid::tests
