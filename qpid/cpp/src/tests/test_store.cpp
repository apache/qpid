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
#include "qpid/log/Statement.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include <boost/algorithm/string.hpp>
#include <boost/cast.hpp>
#include <boost/lexical_cast.hpp>

using namespace qpid;
using namespace broker;
using namespace std;
using namespace boost;
using namespace qpid::sys;

struct TestStoreOptions : public Options {

    string name;

    TestStoreOptions() : Options("Test Store Options") {
        addOptions()
            ("test-store-name", optValue(name, "NAME"), "Name to identify test store instance.");
    }
};

struct Completer : public Runnable {
    intrusive_ptr<PersistableMessage> message;
    int usecs;
    Completer(intrusive_ptr<PersistableMessage> m, int u) : message(m), usecs(u) {}
    void run() {
        qpid::sys::usleep(usecs);
        message->enqueueComplete();
        delete this;
    }
};
    
class TestStore : public NullMessageStore {
  public:
    TestStore(const string& name_, Broker& broker_) : name(name_), broker(broker_) {}

    ~TestStore() {
        for_each(threads.begin(), threads.end(), boost::bind(&Thread::join, _1));
    }

    void enqueue(TransactionContext* ,
                 const boost::intrusive_ptr<PersistableMessage>& msg,
                 const PersistableQueue& )
    {
        string data = polymorphic_downcast<Message*>(msg.get())->getFrames().getContent();

        // Check the message for special instructions.
        size_t i, j; 
        if (starts_with(data, TEST_STORE_DO)
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
            else if (starts_with(action, ASYNC)) {
                std::string delayStr(action.substr(ASYNC.size()));
                int delay = lexical_cast<int>(delayStr);
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

  private:
    static const string TEST_STORE_DO, EXCEPTION, EXIT_PROCESS, ASYNC;
    string name;
    Broker& broker;
    vector<Thread> threads;
};

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
        broker->setStore (new TestStore(options.name, *broker));
    }

    void initialize(qpid::Plugin::Target&) {}
};

static TestStorePlugin pluginInstance;
