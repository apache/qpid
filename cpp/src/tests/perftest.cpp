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
#include "qpid/client/Message.h"
#include "qpid/sys/Time.h"

#include <iostream>
#include <sstream>

using namespace std;
using namespace qpid;
using namespace client;
using namespace sys;

enum DeliveryMode { TRANSIENT = 1, PERSISTENT = 2};

struct Opts : public TestOptions {

    bool listen;
    bool publish;
    bool purge;
    int count;
    int size;
    bool durable;
    int consumers;
    std::string mode;
    int autoAck;
    bool summary;
    
    Opts() :
        listen(false), publish(false), purge(false),
        count(500000), size(64), consumers(1),
        mode("shared"), autoAck(100),
        summary(false)
    {
        addOptions() 
            ("listen", optValue(listen), "Consume messages.")
            ("publish", optValue(publish), "Produce messages.")
            ("purge", optValue(purge), "Purge shared queues.")
            ("count", optValue(count, "N"), "Messages to send.")
            ("size", optValue(size, "BYTES"), "Size of messages.")
            ("durable", optValue(durable, "N"), "Publish messages as durable.")
            ("consumers", optValue(consumers, "N"), "Number of consumers.")
            ("mode", optValue(mode, "shared|fanout|topic"), "consume mode")
            ("auto-ack", optValue(autoAck, "N"), "ack every N messages.")
            ("summary,s", optValue(summary), "summary output only");
    }
};

Opts opts;
enum Mode { SHARED, FANOUT, TOPIC };
Mode mode;

struct ListenThread : public Runnable { Thread thread; void run(); };
struct PublishThread : public Runnable { Thread thread; void run(); };

// Create and purge the shared queues 
void setup() {
    Connection connection;
    opts.open(connection);
    Session_0_10 session = connection.newSession();
    session.setSynchronous(true); // Make sure this is all completed.
    session.queueDeclare(arg::queue="control"); // Control queue
    if (opts.purge) {
        if (!opts.summary) cout << "Purging shared queues" << endl;
        session.queuePurge(arg::queue="control");
    }
    if (mode==SHARED) {
        session.queueDeclare(arg::queue="perftest", arg::durable=opts.durable); // Shared data queue
        if (opts.purge)		
            session.queuePurge(arg::queue="perftest");
    }
    session.close();
    connection.close();
}

int main(int argc, char** argv) {
    try {
        opts.parse(argc, argv);
        if (opts.mode=="shared") mode=SHARED;
        else if (opts.mode=="fanout") mode = FANOUT;
        else if (opts.mode=="topic") mode = TOPIC;
        else throw Exception("Invalid mode");
        if (!opts.listen && !opts.publish && !opts.purge)
            opts.listen = opts.publish = opts.purge = true;
        setup();
        std::vector<ListenThread> listen(opts.consumers);
        PublishThread publish;
        if (opts.listen) 
            for (int i = 0; i < opts.consumers; ++i)
                listen[i].thread=Thread(listen[i]);
        if (opts.publish)
            publish.thread=Thread(publish);
        if (opts.listen)
            for (int i = 0; i < opts.consumers; ++i)
                listen[i].thread.join();
        if (opts.publish)
            publish.thread.join();
    }
    catch (const std::exception& e) {
        cout << "Unexpected exception: " << e.what() << endl;
    }
}

double secs(Duration d) { return double(d)/TIME_SEC; }
double secs(AbsTime start, AbsTime finish) { return secs(Duration(start,finish)); }


void expect(string actual, string expect) {
    if (expect != actual)
        throw Exception("Expecting "+expect+" but received "+actual);

}

const char* exchange() {
    switch (mode) {
      case SHARED: return "";   // Deafult exchange.
      case FANOUT: return "amq.fanout"; 
      case TOPIC: return "amq.topic"; 
    }
    assert(0);
    return 0;
}

void PublishThread::run() {
    try {
        Connection connection;
        opts.open(connection);
        Session_0_10 session = connection.newSession();

        // Wait for consumers.
        if (!opts.summary) cout << "Waiting for consumers ready " << flush;
        SubscriptionManager subs(session);
        LocalQueue control;
        subs.subscribe(control, "control");
        for (int i = 0; i < opts.consumers; ++i) {
            if (!opts.summary) cout << "." << flush;
            expect(control.pop().getData(), "ready");
        }
        if (!opts.summary) cout << endl;

        // Create test message
        size_t msgSize=max(opts.size, 32);
        Message msg(string(msgSize, 'X'), "perftest");
        char* msgBuf = const_cast<char*>(msg.getData().data());
        if (opts.durable)
	    msg.getDeliveryProperties().setDeliveryMode(PERSISTENT);
        // Time sending message.
        AbsTime start=now();
        if (!opts.summary) cout << "Publishing " << opts.count << " messages " << flush;
        for (int i=0; i<opts.count; i++) {
            sprintf(msgBuf, "%d", i);
            session.messageTransfer(arg::destination=exchange(),
                                    arg::content=msg);
            if (!opts.summary && (i%10000)==0) cout << "." << flush;
        }
        if (!opts.summary) cout << " done." << endl;
        msg.setData("done");    // Send done messages.
        if (mode==SHARED)
            for (int i = 0; i < opts.consumers; ++i)
                session.messageTransfer(arg::destination=exchange(), arg::content=msg);
        else
            session.messageTransfer(arg::destination=exchange(), arg::content=msg);
        AbsTime end=now();

        // Report
        double publish_rate=(opts.count)/secs(start,end);
        if (!opts.summary)
            cout << endl
                 << "publish count:" << opts.count << endl
                 << "publish secs:" << secs(start,end) << endl
                 << "publish rate:" << publish_rate << endl;

        double consume_rate = 0; // Average rate for consumers.
        //  Wait for consumer(s) to finish.
        if (!opts.summary) cout << "Waiting for consumers done " << endl;
        for (int i = 0; i < opts.consumers; ++i) {
            string report=control.pop().getData();
            if (!opts.summary)
                cout << endl << report;
            else {
                double rate=boost::lexical_cast<double>(report);
                consume_rate += rate/opts.consumers;
            }
        }
        end=now();

        // Count total transfers from publisher and to subscribers.
        int transfers;
        if (mode==SHARED)       // each message sent/receivd once.
            transfers=2*opts.count; 
        else                    // sent once, received N times.
            transfers=opts.count*(opts.consumers + 1);
        double total_rate=transfers/secs(start, end);
        if (opts.summary)
            cout << opts.mode << '(' << opts.count
                 << ':' << opts.consumers << ')'
                 << '\t' << publish_rate
                 << '\t' << consume_rate
                 << '\t' << total_rate
                 << endl;
        else
            cout << endl
                 << "total transfers:" << transfers << endl
                 << "total secs:" << secs(start, end) << endl
                 << "total rate:" << total_rate << endl;
		
        connection.close();
    }
    catch (const std::exception& e) {
        cout << "PublishThread exception: " << e.what() << endl;
    }
}

void ListenThread::run() {
    try {
        Connection connection;
        opts.open(connection);
        Session_0_10 session = connection.newSession();

        string consumeQueue;
        if (mode == SHARED) {
            consumeQueue="perftest";
        }
        else {
            consumeQueue=session.getId().str(); // Unique name.
            session.queueDeclare(arg::queue=consumeQueue,
                                 arg::exclusive=true,
                                 arg::autoDelete=true);
            session.queueBind(arg::queue=consumeQueue,
                              arg::exchange=exchange(),
                              arg::routingKey="perftest");
        }
        // Notify publisher we are ready.
        session.messageTransfer(arg::content=Message("ready", "control"));

        SubscriptionManager subs(session);
        LocalQueue consume(AckPolicy(opts.autoAck));
        subs.subscribe(consume, consumeQueue);
        int consumed=0;
        AbsTime start=now();
        Message msg;
        while ((msg=consume.pop()).getData() != "done") {
            ++consumed;
        }
        msg.acknowledge();      // Ack all outstanding messages.
        AbsTime end=now();

        // Report to publisher.
        ostringstream report;
        double consume_rate=consumed/secs(start,end);
        if (opts.summary)
            report << consume_rate;
        else
            report << "consume count: " << consumed << endl
                   << "consume secs: " << secs(start, end) << endl
                   << "consume rate: " << consume_rate << endl;
        
        session.messageTransfer(arg::content=Message(report.str(), "control"));
        connection.close();
    }
    catch (const std::exception& e) {
        cout << "PublishThread exception: " << e.what() << endl;
    }
}

