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
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Time.h"

#include <boost/lexical_cast.hpp>
#include <boost/bind.hpp>
#include <boost/function.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

#include <iostream>
#include <sstream>
#include <numeric>
#include <algorithm>
#include <unistd.h>


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

    // Queue policy
    uint32_t queueMaxCount;
    uint64_t queueMaxSize;

    // Publisher
    size_t pubs;
    size_t count ;
    size_t size;
    bool confirm;
    bool durable;
    bool uniqueData;

    // Subscriber
    size_t subs;
    size_t ack;

    // General
    size_t qt;
    size_t iterations;
    Mode mode;
    bool summary;
	uint32_t intervalSub;
	uint32_t intervalPub;

    static const std::string helpText;
    
    Opts() :
        TestOptions(helpText),
        setup(false), control(false), publish(false), subscribe(false),
        pubs(1), count(500000), size(1024), confirm(true), durable(false), uniqueData(false),
        subs(1), ack(0),
        qt(1), iterations(1), mode(SHARED), summary(false),
		intervalSub(0), intervalPub(0)
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
            ("pub-confirm", optValue(confirm, "yes|no"), "Publisher use confirm-mode.")
            ("durable", optValue(durable, "yes|no"), "Publish messages as durable.")
            ("unique-data", optValue(uniqueData, "yes|no"), "Make data for each message unique.")

            ("nsubs", optValue(subs, "N"), "Create N subscribers.")
            ("sub-ack", optValue(ack, "N"), "N>0: Subscriber acks batches of N.\n"
             "N==0: Subscriber uses unconfirmed mode")
            
            ("qt", optValue(qt, "N"), "Create N queues or topics.")
            ("iterations", optValue(iterations, "N"), "Desired number of iterations of the test.")
            ("summary,s", optValue(summary), "Summary output: pubs/sec subs/sec transfers/sec Mbytes/sec")

            ("queue_max_count", optValue(queueMaxCount, "N"), "queue policy: count to trigger 'flow to disk'")
            ("queue_max_size", optValue(queueMaxSize, "N"), "queue policy: accumulated size to trigger 'flow to disk'")

            ("interval_sub", optValue(intervalSub, "ms"), ">=0 delay between msg consume")
            ("interval_pub", optValue(intervalPub, "ms"), ">=0 delay between msg publish");
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

const std::string Opts::helpText=
"There are two ways to use perftest: single process or multi-process.\n\n"
"If none of the --setup, --publish, --subscribe or --control options\n"
"are given perftest will run a single-process test.\n"
"For a  multi-process test first run:\n"
"  perftest --setup <other options>\n"
"and wait for it to complete. The remaining process should run concurrently::\n"
"Run --npubs times: perftest --publish <other options>\n"
"Run --nsubs times: perftest --subscribe <other options>\n"
"Run once:          perftest --control <other options>\n"
"Note the <other options> must be identical for all processes.\n";

Opts opts;

struct Client : public Runnable {
    Connection connection;
    Session_0_10 session;
    Thread thread;

    Client() {
        opts.open(connection);
        session = connection.newSession(ASYNC);
    }

    ~Client() {
        session.close();
        connection.close();
    }
};

struct Setup : public Client {
    
    void queueInit(string name, bool durable=false, const framing::FieldTable& settings=framing::FieldTable()) {
        session.queueDeclare(arg::queue=name, arg::durable=durable, arg::arguments=settings);
        session.queuePurge(arg::queue=name).sync();
    }

    void run() {
        queueInit("pub_start");
        queueInit("pub_done");
        queueInit("sub_ready");
        queueInit("sub_done");
        if (opts.mode==SHARED) {
            framing::FieldTable settings;//queue policy settings
            settings.setInt("qpid.max_count", opts.queueMaxCount);
            settings.setInt("qpid.max_size", opts.queueMaxSize);
            for (size_t i = 0; i < opts.qt; ++i) {
                ostringstream qname;
                qname << "perftest" << i;
                queueInit(qname.str(), opts.durable, settings); 
            }
        }
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
        try {
            double d=lexical_cast<double>(data);
            values.push_back(d);
            sum += d;
        } catch (const std::exception&) {
            throw Exception("Bad report: "+data);
        }
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

    void process(size_t n, LocalQueue lq, string queue,
                 boost::function<void (const string&)> msgFn)
    {
        session.messageFlow(queue, 0, n); 
        if (!opts.summary) 
            cout << "Processing " << n << " messages from "
                 << queue << " " << flush;
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

            LocalQueue pubDone;
            LocalQueue subDone;
            subs.setFlowControl(0, SubscriptionManager::UNLIMITED, false);
            subs.subscribe(pubDone, "pub_done");
            subs.subscribe(subDone, "sub_done");

            double txrateTotal(0);
            double mbytesTotal(0);
            double pubRateTotal(0);
            double subRateTotal(0);

            for (size_t j = 0; j < opts.iterations; ++j) {
                AbsTime start=now();
                send(opts.totalPubs, "pub_start", "start"); // Start publishers

                Stats pubRates;
                Stats subRates;

                process(opts.totalPubs, pubDone, "pub_done", boost::ref(pubRates));
                process(opts.totalSubs, subDone, "sub_done", boost::ref(subRates));

                AbsTime end=now(); 

                double time=secs(start, end);
                double txrate=opts.transfers/time;
                double mbytes=(txrate*opts.size)/(1024*1024);
                
                if (!opts.summary) {
                    cout << endl << "Total " << opts.transfers << " transfers of "
                         << opts.size << " bytes in "
                         << time << " seconds." << endl;
                    cout << endl << "Publish transfers/sec:    " << endl;
                    pubRates.print(cout);
                    cout << endl << "Subscribe transfers/sec:  " << endl;
                    subRates.print(cout);
                    cout << endl
                         << "Total transfers/sec:      " << txrate << endl
                         << "Total Mbytes/sec: " << mbytes << endl;
                }
                else {
                    cout << pubRates.mean() << "\t"
                         << subRates.mean() << "\t"
                         << txrate << "\t"
                         << mbytes << endl;
                }

                txrateTotal += txrate;
                mbytesTotal += mbytes;
                pubRateTotal += pubRates.mean();
                subRateTotal += subRates.mean();
            }
            if (opts.iterations > 1) {
                cout << "Averages: "<< endl
                     << (pubRateTotal / opts.iterations) << "\t"
                     << (subRateTotal / opts.iterations) << "\t"
                     << (txrateTotal / opts.iterations) << "\t"
                     << (mbytesTotal / opts.iterations) << endl;
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
            string data;
            size_t offset(0);
            if (opts.uniqueData) {
                offset = 5;
                data += "data:";//marker (requested for latency testing tool scripts)
                data += string(sizeof(size_t), 'X');//space for seq no
                data += string(reinterpret_cast<const char*>(session.getId().data()), session.getId().size());
                if (opts.size > data.size()) {
                    data += string(opts.size - data.size(), 'X');
                } else if(opts.size < data.size()) {
                    cout << "WARNING: Increased --size to " << data.size()
                         << " to honour --unique-data" << endl;
                }
            } else {
                size_t msgSize=max(opts.size, sizeof(size_t));
                data = string(msgSize, 'X');                
            }

            Message msg(data, routingKey);
            if (opts.durable)
                msg.getDeliveryProperties().setDeliveryMode(framing::PERSISTENT);


            SubscriptionManager subs(session);
            LocalQueue lq;
            subs.setFlowControl(1, SubscriptionManager::UNLIMITED, true); 
            subs.subscribe(lq, "pub_start"); 
            
            for (size_t j = 0; j < opts.iterations; ++j) {
                expect(lq.pop().getData(), "start");
                AbsTime start=now();
                for (size_t i=0; i<opts.count; i++) {
                    // Stamp the iteration into the message data, avoid
                    // any heap allocation.
                    const_cast<std::string&>(msg.getData()).replace(offset, sizeof(uint32_t), 
                                                                    reinterpret_cast<const char*>(&i), sizeof(uint32_t));
                    completion = session.messageTransfer(
                        arg::destination=destination,
                        arg::content=msg,
                        arg::confirmMode=opts.confirm);
		            if (opts.intervalPub) ::usleep(opts.intervalPub*1000);
                }
                if (opts.confirm) completion.sync();
                AbsTime end=now();
                double time=secs(start,end);
                
                // Send result to controller.
                Message report(lexical_cast<string>(opts.count/time), "pub_done");
                session.messageTransfer(arg::content=report);
            }
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

    void verify(bool cond, const char* test, uint32_t expect, uint32_t actual) {
        if (!cond) {
            Message error(
                QPID_MSG("Sequence error: expected  n" << test << expect << " but got " << actual),
                "sub_done");
            session.messageTransfer(arg::content=error);
            throw Exception(error.getData());
        }
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

            
            for (size_t j = 0; j < opts.iterations; ++j) {
                if (j > 0) {
                    //need to allocate some more credit
                    session.messageFlow(queue, 0, opts.subQuota); 
                }
                Message msg;
                AbsTime start=now();
                size_t expect=0;
                for (size_t i = 0; i < opts.subQuota; ++i) {
                    msg=lq.pop();
		            if (opts.intervalSub) ::usleep(opts.intervalSub*1000);
                    // TODO aconway 2007-11-23: check message order for. 
                    // multiple publishers. Need an acorray of counters,
                    // one per publisher and a publisher ID in the
                    // message. Careful not to introduce a lot of overhead
                    // here, e.g. no std::map, std::string etc.
                    //
                    // For now verify order only for a single publisher.
                    size_t offset = opts.uniqueData ? 5 /*marker is 'data:'*/ : 0;
                    size_t n = *reinterpret_cast<const uint32_t*>(msg.getData().data() + offset);
                    if (opts.pubs == 1) {
                        if (opts.subs == 1 || opts.mode == FANOUT) verify(n==expect, "==", expect, n);
                        else verify(n>=expect, ">=", expect, n);
                        expect = n+1;
                    }
                }
                if (opts.ack !=0)
                    msg.acknowledge(); // Cumulative ack for final batch.
                AbsTime end=now();

                // Report to publisher.
                Message result(lexical_cast<string>(opts.subQuota/secs(start,end)),
                               "sub_done");
                session.messageTransfer(arg::content=result);
            }
            session.close();
        }
        catch (const std::exception& e) {
            cout << "SubscribeThread exception: " << e.what() << endl;
            exit(1);
        }
    }
};

int main(int argc, char** argv) {
    
    try {
        opts.parse(argc, argv);

        string exchange;
        switch (opts.mode) {
            case FANOUT: exchange="amq.fanout"; break;
            case TOPIC: exchange="amq.topic"; break;
            case SHARED: break;
        }
        
        bool singleProcess=
            (!opts.setup && !opts.control && !opts.publish && !opts.subscribe);
        if (singleProcess)
            opts.setup = opts.control = opts.publish = opts.subscribe = true;

        if (opts.setup) Setup().run();          // Set up queues

        boost::ptr_vector<Client> subs(opts.subs);
        boost::ptr_vector<Client> pubs(opts.pubs);

        // Start pubs/subs for each queue/topic.
        for (size_t i = 0; i < opts.qt; ++i) {
            ostringstream key;
            key << "perftest" << i; // Queue or topic name.
            if (opts.publish) {
                size_t n = singleProcess ? opts.pubs : 1;
                for (size_t j = 0; j < n; ++j)  {
                    pubs.push_back(new PublishThread(key.str(), exchange));
                    pubs.back().thread=Thread(pubs.back());
                }
            }
            if (opts.subscribe) {
                size_t n = singleProcess ? opts.subs : 1;
                for (size_t j = 0; j < n; ++j)  {
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
        cout << endl << e.what() << endl; 
    }
    return 1;
}

                                            
