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
 *
 * Message store for tests, with two roles:
 *
 * 1. Dump store events to a text file that can be compared to expected event
 *    sequence
 *
 * 2. Emulate hard-to-recreate conditions such as asynchronous completion delays
 *    or store errors.
 *
 * Messages with specially formatted contents trigger various actions.
 * See class Action below for available actions and message format..
 *
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

namespace {

bool startswith(const string& s, const string& prefix) {
    return s.compare(0, prefix.size(), prefix) == 0;
}

void split(const string& s, vector<string>& result, const char* sep=" \t\n") {
    size_t i = s.find_first_not_of(sep);
    while (i != string::npos) {
        size_t j = s.find_first_of(sep, i);
        if (j == string::npos) {
            result.push_back(s.substr(i));
            break;
        }
        result.push_back(s.substr(i, j-i));
        i = s.find_first_not_of(sep, j);
    }
}

}

/**
 * Action message format is TEST_STORE_DO [<name>...]:<action> [<args>...]
 *
 * A list of store <name> can be included so the action only executes on one of
 * the named stores. This is useful in a cluster setting where the same message
 * is replicated to all broker's stores but should only trigger an action on
 * specific ones. If no <name> is given, execute on any store.
 *
 */
class Action {
  public:
    /** Available actions */
    enum ActionEnum {
        NONE,
        THROW,                  ///< Throw an exception from enqueue
        DELAY,                  ///< Delay completion, takes an ID string to complete.
        COMPLETE,               ///< Complete a previously delayed message, takes ID

        N_ACTIONS               // Count of actions, must be last
    };

    string name;
    ActionEnum index;
    vector<string> storeNames, args;

    Action(const string& s) {
        index = NONE;
        if (!startswith(s, PREFIX)) return;
        size_t colon = s.find_first_of(":");
        if (colon == string::npos) return;
        assert(colon >= PREFIX.size());
        split(s.substr(PREFIX.size(), colon-PREFIX.size()), storeNames);
        split(s.substr(colon+1), args);
        if (args.empty()) return;
        for (size_t i = 0; i < N_ACTIONS; ++i) {
            if (args[0] == ACTION_NAMES[i]) {
                name = args[0];
                index = ActionEnum(i);
                args.erase(args.begin());
                break;
            }
        }
    }

    bool executeIn(const string& storeName) {
        return storeNames.empty() ||
            find(storeNames.begin(), storeNames.end(), storeName) !=storeNames.end();
    }

  private:
    static string PREFIX;
    static const char* ACTION_NAMES[N_ACTIONS];
};

string Action::PREFIX("TEST_STORE_DO");

const char* Action::ACTION_NAMES[] = { "none", "throw", "delay", "complete" };


struct TestStoreOptions : public Options {

    string name;
    string dump;
    string events;

    TestStoreOptions() : Options("Test Store Options") {
        addOptions()
            ("test-store-name", optValue(name, "NAME"),
             "Name of test store instance.")
            ("test-store-dump", optValue(dump, "FILE"),
             "File to dump enqueued messages.")
            ("test-store-events", optValue(events, "FILE"),
             "File to log events, 1 line per event.")
            ;
    }
};


class TestStore : public NullMessageStore {
  public:
    TestStore(const TestStoreOptions& opts, Broker& broker_)
        : options(opts), name(opts.name), broker(broker_)
    {
        QPID_LOG(info, "TestStore name=" << name
                 << " dump=" << options.dump
                 << " events=" << options.events)

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
        ostringstream o;
        string data = getContent(pmsg);
        o << "<enqueue " << queue.getName() << " " << data;
        if (tx) o << " tx=" << getId(*tx);
        o << ">";
        log(o.str());

        // Dump the message if there is a dump file.
        if (dump.get()) {
            *dump << "Message(" << data.size() << "): " << data << endl;
        }
        string logPrefix = "TestStore "+name+": ";
        Action action(data);
        bool doComplete = true;
        if (action.index && action.executeIn(name)) {
            switch (action.index) {

              case Action::THROW:
                throw Exception(logPrefix + data);
                break;

              case Action::DELAY: {
                  if (action.args.empty()) {
                      QPID_LOG(error, logPrefix << "async-id needs argument: " << data);
                      break;
                  }
                  asyncIds[action.args[0]] = pmsg;
                  QPID_LOG(debug, logPrefix << "delayed completion " << action.args[0]);
                  doComplete = false;
                  break;
              }

              case Action::COMPLETE: {
                  if (action.args.empty()) {
                      QPID_LOG(error, logPrefix << "complete-id needs argument: " << data);
                      break;
                  }
                  AsyncIds::iterator i = asyncIds.find(action.args[0]);
                  if (i != asyncIds.end()) {
                      i->second->enqueueComplete();
                      QPID_LOG(debug, logPrefix << "completed " << action.args[0]);
                      asyncIds.erase(i);
                  } else {
                      QPID_LOG(info, logPrefix << "not found for completion " << action.args[0]);
                  }
                  break;
              }

              default:
                QPID_LOG(error, logPrefix << "unknown action: " << data);
            }
        }
        if (doComplete) pmsg->enqueueComplete();
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
    typedef map<string, boost::intrusive_ptr<PersistableMessage> > AsyncIds;

    TestStoreOptions options;
    string name;
    Broker& broker;
    vector<Thread> threads;
    std::auto_ptr<ofstream> dump;
    std::auto_ptr<ofstream> events;
    AsyncIds asyncIds;
};

int TestStore::TxContext::nextId(1);

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
