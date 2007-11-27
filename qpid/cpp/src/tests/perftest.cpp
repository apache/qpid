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

#include "TestOptions.h"

#include "qpid/client/Session_0_10.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Completion.h"
#include "qpid/client/Message.h"
#include "qpid/sys/Time.h"

#include <boost/lexical_cast.hpp>
#include <boost/bind.hpp>
#include <boost/function.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

#include <iostream>
#include <sstream>
#include <numeric>
#include <algorithm>

using namespace std;
using namespace qpid;
using namespace client;
using namespace sys;
using boost::lexical_cast;
using boost::bind;

enum Mode { SHARED, FANOUT, TOPIC };
const char* modeNames[] = { "shared", "fanout", "topic" };

// istream/ostream ops so Options can read/display Mode.
istream& operator>>(istream& in, Mode& mode) {
    string s;
    in >> s;
    int i = find(modeNames, modeNames+3, s) - modeNames;
    if (i >= 3)  throw Exception("Invalid mode: "+s);
    mode = Mode(i);
    return in;
}

ostream& operator<<(ostream& out, Mode mode) {
    return out << modeNames[mode];
}


struct Opts : public TestOptions {

    // Actions
    bool setup, control, publish, subscribe;

    // Publisher
    size_t pubs;
    size_t count ;
    size_t size;
    bool confirm;
    bool durable;

    // Subscriber
    size_t subs;
    size_t ack;

    // General
    size_t qt;
    Mode mode;
    bool summary;
    
    Opts() :
        setup(false), control(false), publish(false), subscribe(false),
        pubs(1), count(500000), size(64), confirm(false), durable(false),
        subs(1), ack(0),
        qt(1), mode(SHARED), summary(false)
    {
        addOptions()
            ("setup", optValue(setup), "Create shared queues.")
            ("control", optValue(control), "Run test, print report.")
            ("publish", optValue(publish), "Publish messages.")
            ("subscribe", optValue(subscribe), "Subscribe for messages.")

            ("mode", optValue(mode, "shared|fanout|topic"), "Test mode."
             "\nshared: --qt queues, --npubs publishers and --nsubs subscribers per queue.\n"
             "\nfanout: --npubs publishers, --nsubs subscribers, fanout exchange."
             "\ntopic: --qt topics, --npubs publishers and --nsubs subscribers per topic.\n")

            ("npubs", optValue(pubs, "N"), "Create N publishers.")
            ("count", optValue(count, "N"), "Each publisher sends N messages.")
            ("size", optValue(size, "BYTES"), "Size of messages in bytes.")
            ("pub-confirm", optValue(confirm), "Publisher use confirm-mode.")
            ("durable", optValue(durable, "N"), "Publish messages as durable.")

            ("nsubs", optValue(subs, "N"), "Create N subscribers.")
            ("sub-ack", optValue(ack, "N"), "N>0: Subscriber acks batches of N.\n"
             "N==0: Subscriber uses unconfirmed mode")
            
            ("qt", optValue(qt, "N"), "Create N queues or topics.")
            ("summary,s", optValue(summary), "Summary output only.");
    }

    // Computed values
    size_t totalPubs;
    size_t totalSubs;
    size_t transfers;
    size_t subQuota;

    void parse(int argc, char** argv) {
        TestOptions::parse(argc, argv);
        switch (mode) {
          case SHARED:
            if (count % subs) {
                count += subs - (count % subs);
                cout << "WARNING: Adjusted --count to " << count
                     << " the nearest multiple of --nsubs" << endl;
            }                    
            totalPubs = pubs*qt;
            totalSubs = subs*qt;
            subQuota = (pubs*count)/subs;
            break;
          case FANOUT:
            if (qt != 1) cerr << "WARNING: Fanout mode, ignoring --qt="
                              << qt << endl;
            qt=1;
            totalPubs = pubs;
            totalSubs = subs;
            subQuota = totalPubs*count;
            break;
          case TOPIC:
            totalPubs = pubs*qt;
            totalSubs = subs*qt;
            subQuota = pubs*count;
            break;
        }
        transfers=(totalPubs*count) + (totalSubs*subQuota);
    }
};


Opts opts;

struct Client : public Runnable {
    Connection connection;
    Session_0_10 session;
    Thread thread;

    Client() {
        opts.open(connection);
        session = connection.newSession();
    }

    ~Client() {
        session.close();
        connection.close();
    }
};

struct Setup : public Client {
    
    void queueInit(string name, bool durable=false) {
        session.queueDeclare(arg::queue=name, arg::durable=durable);
        session.queuePurge(arg::queue=name);
    }

    void run() {
        queueInit("pub_start");
        queueInit("pub_done");
        queueInit("sub_ready");
        queueInit("sub_done");
        if (opts.mode==SHARED) {
            for (size_t i = 0; i < opts.qt; ++i) {
                ostringstream qname;
                qname << "perftest" << i;
                queueInit(qname.str(), opts.durable); 
            }
        }
        // Make sure this is all completed before we return.
        session.execution().sendSyncRequest();
    }
};

void expect(string actual, string expect) {
    if (expect != actual)
        throw Exception("Expecting "+expect+" but received "+actual);

}

double secs(Duration d) { return double(d)/TIME_SEC; }
double secs(AbsTime start, AbsTime finish) {
    return secs(Duration(start,finish));
}


// Collect rates & print stats.
class Stats {
    vector<double> values;
    double sum;

  public:
    Stats() : sum(0) {}
    
    // Functor to collect rates.
    void operator()(const string& data) {
        double d=lexical_cast<double>(data);
        values.push_back(d);
        sum += d;
    }
    
    double mean() const {
        return sum/values.size();
    }

    double stdev() const {
        if (values.size() <= 1) return 0;
        double avg = mean();
        double ssq = 0;
        for (vector<double>::const_iterator i = values.begin();
             i != values.end(); ++i) {
            double x=*i;
            x -= avg;
            ssq += x*x;
        }
        return sqrt(ssq/(values.size()-1));
    }
    
    ostream& print(ostream& out) {
        ostream_iterator<double> o(out, "\n");
        copy(values.begin(), values.end(), o);
        out << "Average: " << mean();
        if (values.size() > 1)
            out << " (std.dev. " << stdev() << ")";
        return out << endl;
    }
};
    

// Manage control queues, collect and print reports.
struct Controller : public Client {
 
   SubscriptionManager subs;

    Controller() : subs(session) {}

    /** Process messages from queue by applying a functor. */
    void process(size_t n, string queue,
                 boost::function<void (const string&)> msgFn)
    {
        if (!opts.summary) 
            cout << "Processing " << n << " messages from "
                 << queue << " " << flush;
        LocalQueue lq;
        subs.setFlowControl(n, SubscriptionManager::UNLIMITED, false);
        subs.subscribe(lq, queue);
        for (size_t i = 0; i < n; ++i) {
            if (!opts.summary) cout << "." << flush;
            msgFn(lq.pop().getData());
        }
        if (!opts.summary) cout << " done." << endl;
    }

    void send(size_t n, string queue, string data) {
        if (!opts.summary)
            cout << "Sending " << data << " " << n << " times to " << queue
                 << endl;
        Message msg(data, queue);
        for (size_t i = 0; i < n; ++i) 
            session.messageTransfer(arg::content=msg);
    }

    void run() {                // Controller
        try {
            // Wait for subscribers to be ready.
            process(opts.totalSubs, "sub_ready", bind(expect, _1, "ready"));

            Stats pubRates;
            Stats subRates;

            AbsTime start=now();
            send(opts.totalPubs, "pub_start", "start"); // Start publishers
            process(opts.totalPubs, "pub_done", boost::ref(pubRates));
            process(opts.totalSubs, "sub_done", boost::ref(subRates));
            AbsTime end=now(); 
            double time=secs(start, end);

            if (!opts.summary) {
                cout << endl << "Publish rates: " << endl;
                pubRates.print(cout);
                cout << endl << "Subscribe rates: " << endl;
                subRates.print(cout);
                cout << endl << "Total transfers: " << opts.transfers << endl;
                cout << "Total time (secs): " << time << endl;
                cout << "Total rate: " << opts.transfers/time << endl;
            }
            else {
                cout << pubRates.mean() << "\t"
                     << subRates.mean() << "\t"
                     << opts.transfers/time << endl;
            }
        }
        catch (const std::exception& e) {
            cout << "Controller exception: " << e.what() << endl;
            exit(1);
        }
    }
};

struct PublishThread : public Client {
    string destination;
    string routingKey;

    PublishThread() {};
    
    PublishThread(string key, string dest=string()) {
        destination=dest;
        routingKey=key;
    }
    
    void run() {                // Publisher
        Completion completion;
        try {
            size_t msgSize=max(opts.size, sizeof(size_t));
            Message msg(string(msgSize, 'X'), routingKey);
            if (opts.durable)
                msg.getDeliveryProperties().setDeliveryMode(framing::PERSISTENT);

            SubscriptionManager subs(session);
            LocalQueue lq(AckPolicy(opts.ack));
            subs.setFlowControl(1, SubscriptionManager::UNLIMITED, false); 
            subs.subscribe(lq, "pub_start"); 
            expect(lq.pop().getData(), "start");
            
            AbsTime start=now();
            for (size_t i=0; i<opts.count; i++) {
                // Stamp the iteration into the message data, avoid
                // any heap allocation.
                char* data = const_cast<char*>(msg.getData().data());
                *reinterpret_cast<uint32_t*>(data) = i;
                completion = session.messageTransfer(
                    arg::destination=destination,
                    arg::content=msg,
                    arg::confirmMode=opts.confirm);
            }
            if (opts.confirm) completion.sync();
            AbsTime end=now();
            double time=secs(start,end);

            // Send result to controller.
            msg.setData(lexical_cast<string>(opts.count/time));
            msg.getDeliveryProperties().setRoutingKey("pub_done");
            session.messageTransfer(arg::content=msg);
            session.close();
        }
        catch (const std::exception& e) {
            cout << "PublishThread exception: " << e.what() << endl;
            exit(1);
        }
    }
};

struct SubscribeThread : public Client {

    string queue;

    SubscribeThread() {}
    
    SubscribeThread(string q) { queue = q; }

    SubscribeThread(string key, string ex) {
        queue=session.getId().str(); // Unique name.
        session.queueDeclare(arg::queue=queue,
                             arg::exclusive=true,
                             arg::autoDelete=true,
                             arg::durable=opts.durable);
        session.queueBind(arg::queue=queue,
                          arg::exchange=ex,
                          arg::routingKey=key);
    }
    
    void run() {                // Subscribe
        try {
            SubscriptionManager subs(session);
            LocalQueue lq(AckPolicy(opts.ack));
            subs.setConfirmMode(opts.ack > 0);
            subs.setFlowControl(opts.subQuota, SubscriptionManager::UNLIMITED,
                                false);
            subs.subscribe(lq, queue);
            // Notify controller we are ready.
            session.messageTransfer(arg::content=Message("ready", "sub_ready"));

            Message msg;
            AbsTime start=now();
            for (size_t i = 0; i < opts.subQuota; ++i) {
                msg=lq.pop();
                // FIXME aconway 2007-11-23: Verify message sequence numbers.
                // Need an array of counters, one per publisher and need
                // publisher ID in the message for multiple publishers.
            }
            if (opts.ack !=0)
                msg.acknowledge(); // Cumulative ack for final batch.
            AbsTime end=now();

            // FIXME aconway 2007-11-23: close the subscription,
            // release any pending messages.

            // Report to publisher.
            Message result(lexical_cast<string>(opts.subQuota/secs(start,end)),
                           "sub_done");
            session.messageTransfer(arg::content=result);
            session.close();
        }
        catch (const std::exception& e) {
            cout << "Publisher exception: " << e.what() << endl;
            exit(1);
        }
    }
};

int main(int argc, char** argv) {
    string exchange;
    switch (opts.mode) {
      case FANOUT: exchange="amq.fanout"; break;
      case TOPIC: exchange="amq.topic"; break;
      case SHARED: break;
    }

    try {
        opts.parse(argc, argv);
        if (!opts.setup && !opts.control && !opts.publish && !opts.subscribe)
            opts.setup = opts.control = opts.publish = opts.subscribe = true;

        if (opts.setup) Setup().run();          // Set up queues

        boost::ptr_vector<Client> subs(opts.subs);
        boost::ptr_vector<Client> pubs(opts.pubs);

        // Start pubs/subs for each queue/topic.
        for (size_t i = 0; i < opts.qt; ++i) {
            ostringstream key;
            key << "perftest" << i; // Queue or topic name.
            if (opts.publish) {
                for (size_t j = 0; j < opts.pubs; ++j)  {
                    pubs.push_back(new PublishThread(key.str(), exchange));
                    pubs.back().thread=Thread(pubs.back());
                }
            }
            if (opts.subscribe) {
                for (size_t j = 0; j < opts.subs; ++j)  {
                    if (opts.mode==SHARED)
                        subs.push_back(new SubscribeThread(key.str()));
                    else
                        subs.push_back(new SubscribeThread(key.str(),exchange));
                    subs.back().thread=Thread(subs.back());
                }
            }
        }

        if (opts.control) Controller().run();


        // Wait for started threads.
        if (opts.publish) {
            for (boost::ptr_vector<Client>::iterator i=pubs.begin();
                 i != pubs.end();
                 ++i) 
                i->thread.join();
        }
            

        if (opts.subscribe) {
            for (boost::ptr_vector<Client>::iterator i=subs.begin();
                 i != subs.end();
                 ++i) 
                i->thread.join();
        }
        return 0;
    }
    catch (const std::exception& e) {
        cout << "Unexpected exception: " << e.what() << endl; 
       return 1;
    }
}
