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

struct Opts : public TestOptions {

    bool listen;
    bool publish;
    int count;
    int size;
    bool durable;
    int consumers;
    std::string mode;
    int autoAck;
    
    Opts() :
        listen(false), publish(false), count(500000), size(64), consumers(1),
        mode("shared"), autoAck(100)
    {
        addOptions() 
            ("listen", optValue(listen), "Consume messages.")
            ("publish", optValue(publish), "Produce messages.")
            ("count", optValue(count, "N"), "Messages to send.")
            ("size", optValue(size, "BYTES"), "Size of messages.")
            ("durable", optValue(durable, "N"), "Publish messages as durable.")
            ("consumers", optValue(consumers, "N"), "Number of consumers.")
            ("mode", optValue(mode, "shared|fanout|topic"), "consume mode")
            ("auto-ack", optValue(autoAck, "N"), "ack every N messages.");
    }
};

Opts opts;
enum Mode { SHARED, FANOUT, TOPIC };
Mode mode;

struct ListenThread : public Runnable { Thread thread; void run(); };
struct PublishThread : public Runnable { Thread thread; void run(); };

// Create and purge the shared queues 
void setup() {
    cout << "Create shared queues" << endl;
    Connection connection;
    opts.open(connection);
    Session_0_10 session = connection.newSession();
    session.setSynchronous(true); // Make sure this is all completed.
    session.queueDeclare(arg::queue="control"); // Control queue
    session.queuePurge(arg::queue="control");
    if (mode==SHARED) {
        session.queueDeclare(arg::queue="perftest"); // Shared data queue
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
        if (!opts.listen && !opts.publish)
            opts.listen = opts.publish = true;
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
        cout << "Publisher wating for consumers " << flush;
        SubscriptionManager subs(session);
        LocalQueue control;
        subs.subscribe(control, "control");
        for (int i = 0; i < opts.consumers; ++i) {
            cout << "." << flush;
            expect(control.pop().getData(), "ready");
        }
        cout << endl;

        // Create test message
        size_t msgSize=max(opts.size, 32);
        Message msg(string(msgSize, 'X'), "perftest");
        char* msgBuf = const_cast<char*>(msg.getData().data());
        if (opts.durable)
	    msg.getDeliveryProperties().setDeliveryMode(framing::PERSISTENT);
        // Time sending message.
        AbsTime start=now();
        cout << "Publishing " << opts.count << " messages " << flush;
        for (int i=0; i<opts.count; i++) {
            sprintf(msgBuf, "%d", i);
            session.messageTransfer(arg::destination=exchange(),
                                    arg::content=msg);
            if ((i%10000)==0) cout << "." << flush;
        }
        cout << " done." << endl;
        msg.setData("done");    // Send done messages.
        if (mode==SHARED)
            for (int i = 0; i < opts.consumers; ++i)
                session.messageTransfer(arg::destination=exchange(), arg::content=msg);
        else
            session.messageTransfer(arg::destination=exchange(), arg::content=msg);
        AbsTime end=now();

        // Report
        cout << endl;
        cout << "publish count:" << opts.count << endl;
        cout << "publish secs:" << secs(start,end) << endl;
        cout << "publish rate:" << (opts.count)/secs(start,end) << endl;

        //  Wait for consumer(s) to finish.
        cout << "Publisher wating for consumer reports. " << endl;
        for (int i = 0; i < opts.consumers; ++i) {
            string report=control.pop().getData();
            if (report.find("consume") != 0)
                throw Exception("Expected consumer report, got: "+report);
            cout << endl << report;
        }
        end=now();

        // Count total transfers from publisher and to subscribers.
        int transfers;
        if (mode==SHARED)       // each message sent/receivd once.
            transfers=2*opts.count; 
        else                    // sent once, received N times.
            transfers=opts.count*(opts.consumers + 1);
        
        cout << endl
             << "total transfers:" << transfers << endl
             << "total secs:" << secs(start, end) << endl
             << "total transfers/sec:" << transfers/secs(start, end) << endl;
		
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
        if (!opts.publish)
            cout << "Consuming " << flush;
        while ((msg=consume.pop()).getData() != "done") {
            ++consumed;
            if (!opts.publish && (consumed%10000) == 0)
                cout << "." << flush;
        }
        if (!opts.publish)
            cout << endl;
        msg.acknowledge();      // Ack all outstanding messages.
        AbsTime end=now();

        // Report to publisher.
        ostringstream report;
        report << "consume count: " << consumed << endl
               << "consume secs: " << secs(start, end) << endl
               << "consume rate: " << consumed/secs(start,end) << endl;
        session.messageTransfer(arg::content=Message(report.str(), "control"));
        connection.close();
    }
    catch (const std::exception& e) {
        cout << "PublishThread exception: " << e.what() << endl;
    }
}

