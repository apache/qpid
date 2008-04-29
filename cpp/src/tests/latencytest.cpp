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
#include <iostream>
#include <memory>
#include <sstream>
#include <vector>
#include <unistd.h>

#include "TestOptions.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Message.h"
#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionManager.h"

using namespace qpid;
using namespace qpid::client;
using namespace qpid::sys;
using std::string;

typedef std::vector<std::string> StringSet;

struct Args : public qpid::TestOptions {
    uint size;
    uint count;
    uint rate;
    uint reportFrequency;
    uint queues;
    uint prefetch;
    uint ack;
    bool durable;
    string base;

    Args() : size(256), count(1000), rate(0), reportFrequency(1), queues(1), 
             prefetch(100), ack(0),
             durable(false), base("latency-test")
    {
        addOptions()            

            ("size", optValue(size, "N"), "message size")
            ("queues", optValue(queues, "N"), "number of queues")
            ("count", optValue(count, "N"), "number of messages to send")
            ("rate", optValue(rate, "N"), "target message rate (causes count to be ignored)")
            ("report-frequency", optValue(reportFrequency, "N"), 
             "number of seconds to wait between reports (ignored unless rate specified)")
            ("prefetch", optValue(prefetch, "N"), "prefetch count (0 implies no flow control, and no acking)")
            ("ack", optValue(ack, "N"), "Ack frequency in messages (defaults to half the prefetch value)")
            ("durable", optValue(durable, "yes|no"), "use durable messages")
            ("queue-base-name", optValue(base, "<name>"), "base name for queues")
            ("tcp-nodelay", optValue(con.tcpNoDelay), "Turn on tcp-nodelay");
    }
};

const std::string chars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");

Args opts;

uint64_t current_time()
{
    Duration t(now());
    return t;
}

struct Stats 
{
    Mutex lock;
    uint count;
    double minLatency;
    double maxLatency;
    double totalLatency;

    Stats();
    void update(double l);
    void print();
    void reset();
};

class Client : public Runnable
{
protected:
    Connection connection;
    Session session;
    Thread thread;
    string queue;

public:
    Client(const string& q);
    virtual ~Client() {}

    void start();
    void join();
    void run();
    virtual void test() = 0;
};

class Receiver : public Client, public MessageListener
{    
    SubscriptionManager mgr;
    uint count;
    Stats& stats;

public:
    Receiver(const string& queue, Stats& stats);
    void test();
    void received(Message& msg);
    Stats getStats();
};


class Sender : public Client
{
    string generateData(uint size);
    void sendByRate();
    void sendByCount();
public:
    Sender(const string& queue);
    void test();
};


class Test
{
    const string queue;
    Stats stats;
    Receiver receiver;
    Sender sender;
    AbsTime begin;
    
public:
    Test(const string& q) : queue(q), receiver(queue, stats), sender(queue), begin(now()) {}
    void start();
    void join();
    void report();
};


Client::Client(const string& q) : queue(q)
{
    opts.open(connection);
    session = connection.newSession(ASYNC);       
}

void Client::start()
{
    thread = Thread(this);
}

void Client::join()
{
    thread.join();
}

void Client::run()
{
    try{
        test();
        session.close();
        connection.close();
    } catch(const std::exception& e) {
        std::cout << "Error in receiver: " << e.what() << std::endl;
    }
}

Receiver::Receiver(const string& q, Stats& s) : Client(q), mgr(session), count(0), stats(s)
{
    session.queueDeclare(arg::queue=queue, arg::durable=opts.durable, arg::autoDelete=true);
    uint msgCount = session.queueQuery(arg::queue=queue).get().getMessageCount();
    if (msgCount) {
        std::cout << "Warning: found " << msgCount << " msgs on " << queue << ". Purging..." << std::endl;
        session.queuePurge(arg::queue=queue);
    }
    if (opts.prefetch) {
        mgr.setAckPolicy(AckPolicy(opts.ack ? opts.ack : (opts.prefetch / 2)));
        mgr.setFlowControl(opts.prefetch, SubscriptionManager::UNLIMITED, true);
    } else {
        mgr.setAcceptMode(1/*not-required*/);
        mgr.setFlowControl(SubscriptionManager::UNLIMITED, SubscriptionManager::UNLIMITED, false);
    }
    mgr.subscribe(*this, queue);    
}

void Receiver::test()
{
    mgr.run();
    mgr.cancel(queue);
}

void Receiver::received(Message& msg)
{
    ++count;
    uint64_t sentAt = msg.getDeliveryProperties().getTimestamp();
    //uint64_t sentAt = msg.getHeaders().getTimestamp("sent-at");// TODO: add support for uint64_t as a field table type
    uint64_t receivedAt = current_time();

    stats.update(((double) (receivedAt - sentAt)) / TIME_MSEC);

    if (!opts.rate && count >= opts.count) {
        mgr.stop();
    }
}

void Stats::update(double latency)
{
    Mutex::ScopedLock l(lock);
    count++;
    if (minLatency == 0 || minLatency > latency) minLatency = latency;
    if (maxLatency == 0 || maxLatency < latency) maxLatency = latency;
    totalLatency += latency;
}

Stats::Stats() : count(0), minLatency(0), maxLatency(0), totalLatency(0) {}

void Stats::print()
{
    Mutex::ScopedLock l(lock);
    std::cout << "Latency(ms): min=" << minLatency << ", max=" << maxLatency << ", avg=" << (totalLatency / count); 
}

void Stats::reset()
{
    Mutex::ScopedLock l(lock);
    count = 0;
    totalLatency = maxLatency = minLatency = 0;           
}

Sender::Sender(const string& q) : Client(q) {}

void Sender::test()
{
    if (opts.rate) sendByRate();
    else sendByCount();
}

void Sender::sendByCount()
{
    Message msg(generateData(opts.size), queue);
    if (opts.durable) {
        msg.getDeliveryProperties().setDeliveryMode(framing::PERSISTENT);
    }

    for (uint i = 0; i < opts.count; i++) {
        uint64_t sentAt(current_time());
        msg.getDeliveryProperties().setTimestamp(sentAt);
        //msg.getHeaders().setTimestamp("sent-at", sentAt);//TODO add support for uint64_t to field tables
        session.messageTransfer(arg::content=msg, arg::acceptMode=1);
    }
    session.sync();
}

void Sender::sendByRate()
{
    Message msg(generateData(opts.size), queue);
    if (opts.durable) {
        msg.getDeliveryProperties().setDeliveryMode(framing::PERSISTENT);
    }

    //calculate metrics required for target rate
    uint msgsPerMsec = opts.rate / 1000;

    while (true) {
        uint64_t start(current_time());
        for (uint i = 0; i < msgsPerMsec; i++) {
            uint64_t sentAt(current_time());
            msg.getDeliveryProperties().setTimestamp(sentAt);
            //msg.getHeaders().setTimestamp("sent-at", sentAt);//TODO add support for uint64_t to field tables
            session.messageTransfer(arg::content=msg, arg::acceptMode=1);
        }
        uint64_t timeTaken = (current_time() - start) / TIME_USEC;
        if (timeTaken < 1000) {
            usleep(1000 - timeTaken);
        } else if (timeTaken > 1000) {
            double timeMsecs = (double) timeTaken / 1000;       
            std::cout << "Could not achieve desired rate. Sent " << msgsPerMsec << " in " 
                      << (timeMsecs) << "ms (" << ((msgsPerMsec * 1000 * 1000) / timeTaken) << " msgs/s)" << std::endl;
        }
    }
}

string Sender::generateData(uint size)
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


void Test::start() 
{ 
    receiver.start(); 
    begin = AbsTime(now());
    sender.start(); 
}

void Test::join() 
{ 
    sender.join(); 
    receiver.join(); 
    AbsTime end = now();
    Duration time(begin, end);
    double msecs(time / TIME_MSEC);
    std::cout << "Sent " << opts.count << " msgs through " << queue 
              << " in " << msecs << "ms (" << (opts.count * 1000 / msecs) << " msgs/s) ";
    stats.print();
    std::cout << std::endl;
}

void Test::report() 
{ 
    stats.print();
    std::cout << std::endl;
    stats.reset();
}

int main(int argc, char** argv)
{
    try {
        opts.parse(argc, argv);
        boost::ptr_vector<Test> tests(opts.queues);
        for (uint i = 0; i < opts.queues; i++) {
            std::ostringstream out;
            out << opts.base << "-" << (i+1);
            tests.push_back(new Test(out.str()));
        }
        for (boost::ptr_vector<Test>::iterator i = tests.begin(); i != tests.end(); i++) {
            i->start();
        }
        if (opts.rate) {
            while (true) {
                usleep(opts.reportFrequency * 1000 * 1000);
                //print latency report:
                for (boost::ptr_vector<Test>::iterator i = tests.begin(); i != tests.end(); i++) {
                    i->report();
                }
            }
        } else {
            for (boost::ptr_vector<Test>::iterator i = tests.begin(); i != tests.end(); i++) {
                i->join();
            }
        }

        return 0;
    } catch(const std::exception& e) {
	std::cout << e.what() << std::endl;
    }
    return 1;
}
