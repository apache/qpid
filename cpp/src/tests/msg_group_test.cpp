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
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Session.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/FailoverUpdates.h>
#include <qpid/Options.h>
#include <qpid/log/Logger.h>
#include <qpid/log/Options.h>
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/SystemInfo.h"

#include <iostream>
#include <memory>
#include <stdlib.h>

using namespace qpid::messaging;
using namespace qpid::types;
using namespace std;

namespace qpid {
namespace tests {

struct Options : public qpid::Options
{
    bool help;
    std::string url;
    std::string address;
    std::string connectionOptions;
    uint messages;
    uint capacity;
    uint ackFrequency;
    bool failoverUpdates;
    qpid::log::Options log;
    uint senders;
    uint receivers;
    uint groupSize;
    bool printReport;
    std::string groupKey;
    bool durable;
    bool allowDuplicates;
    bool randomizeSize;
    bool stickyConsumer;
    uint timeout;
    uint interleave;
    std::string prefix;
    uint sendRate;

    Options(const std::string& argv0=std::string())
        : qpid::Options("Options"),
          help(false),
          url("amqp:tcp:127.0.0.1"),
          messages(10000),
          capacity(1000),
          ackFrequency(100),
          failoverUpdates(false),
          log(argv0),
          senders(2),
          receivers(2),
          groupSize(10),
          printReport(false),
          groupKey("qpid.no_group"),
          durable(false),
          allowDuplicates(false),
          randomizeSize(false),
          stickyConsumer(false),
          timeout(10),
          interleave(1),
          sendRate(0)
    {
        addOptions()
          ("ack-frequency", qpid::optValue(ackFrequency, "N"), "Ack frequency (0 implies none of the messages will get accepted)")
          ("address,a", qpid::optValue(address, "ADDRESS"), "address to send and receive from")
          ("allow-duplicates", qpid::optValue(allowDuplicates), "Ignore the delivery of duplicated messages")
          ("broker,b", qpid::optValue(url, "URL"), "url of broker to connect to")
          ("capacity", qpid::optValue(capacity, "N"), "Pre-fetch window (0 implies no pre-fetch)")
          ("connection-options", qpid::optValue(connectionOptions, "OPTIONS"), "options for the connection")
          ("durable", qpid::optValue(durable, "yes|no"), "Mark messages as durable.")
          ("failover-updates", qpid::optValue(failoverUpdates), "Listen for membership updates distributed via amq.failover")
          ("group-key", qpid::optValue(groupKey, "KEY"), "Key of the message header containing the group identifier.")
          ("group-prefix", qpid::optValue(prefix, "STRING"), "Add 'prefix' to the start of all generated group identifiers.")
          ("group-size", qpid::optValue(groupSize, "N"), "Number of messages per a group.")
          ("interleave", qpid::optValue(interleave, "N"), "Simultaineously interleave messages from N different groups.")
          ("messages,m", qpid::optValue(messages, "N"), "Number of messages to send per each sender.")
          ("receivers,r", qpid::optValue(receivers, "N"), "Number of message consumers.")
          ("randomize-group-size", qpid::optValue(randomizeSize), "Randomize the number of messages per group to [1...group-size].")
          ("send-rate", qpid::optValue(sendRate,"N"), "Send at rate of N messages/second. 0 means send as fast as possible.")
          ("senders,s", qpid::optValue(senders, "N"), "Number of message producers.")
          ("sticky-consumers", qpid::optValue(stickyConsumer), "If set, verify that all messages in a group are consumed by the same client [TBD].")
          ("timeout", qpid::optValue(timeout, "N"), "Fail with a stall error should all consumers remain idle for timeout seconds.")
          ("print-report", qpid::optValue(printReport), "Dump message group statistics to stdout.")
          ("help", qpid::optValue(help), "print this usage statement");
        add(log);
        //("check-redelivered", qpid::optValue(checkRedelivered), "Fails with exception if a duplicate is not marked as redelivered (only relevant when ignore-duplicates is selected)")
        //("tx", qpid::optValue(tx, "N"), "batch size for transactions (0 implies transaction are not used)")
        //("rollback-frequency", qpid::optValue(rollbackFrequency, "N"), "rollback frequency (0 implies no transaction will be rolledback)")
    }

    bool parse(int argc, char** argv)
    {
        try {
            qpid::Options::parse(argc, argv);
            if (address.empty()) throw qpid::Exception("Address must be specified!");
            if (senders == 0 && receivers == 0) throw qpid::Exception("No senders and No receivers?");
            if (messages == 0) throw qpid::Exception("The message count cannot be zero.");
            qpid::log::Logger::instance().configure(log);
            if (help) {
                std::cout << *this << std::endl << std::endl
                          << "Verifies the behavior of grouped messages." << std::endl;
                return false;
            } else {
                return true;
            }
        } catch (const std::exception& e) {
            std::cerr << *this << std::endl << std::endl << e.what() << std::endl;
            return false;
        }
    }
};

const string EOS("eos");
const string SN("sn");


// class that monitors group state across all publishers and consumers.  tracks the next
// expected sequence for each group, and total messages consumed.
class GroupChecker
{
    qpid::sys::Mutex lock;

    uint consumerCt;
    uint producerCt;
    uint totalMsgs;
    uint totalMsgsConsumed;
    uint totalMsgsPublished;
    bool allowDuplicates;
    uint duplicateMsgs;

    typedef std::map<std::string, uint> SequenceMap;
    SequenceMap sequenceMap;

    // Statistics - for each group, store the names of all clients that consumed messages
    // from that group, and the number of messages consumed per client.
    typedef std::map<std::string, uint> ClientCounter;
    typedef std::map<std::string, ClientCounter> GroupStatistics;
    GroupStatistics statistics;

public:

    GroupChecker( uint messages, uint consumers, uint producers, bool d) :
        consumerCt(consumers), producerCt(producers),
        totalMsgs(0), totalMsgsConsumed(0), totalMsgsPublished(0), allowDuplicates(d),
        duplicateMsgs(0)
    {
        // if consumering only - we a draining a queue of 'messages' queued messages.
        if (producerCt != 0) {
            totalMsgs = producers * messages;
        } else {
            totalMsgs = messages;
        }
    }

    bool checkSequence( const std::string& groupId,
                        uint sequence, const std::string& client )
    {
        qpid::sys::Mutex::ScopedLock l(lock);

        QPID_LOG(debug, "Client " << client << " has received " << groupId << ":" << sequence);

        GroupStatistics::iterator gs = statistics.find(groupId);
        if (gs == statistics.end()) {
            statistics[groupId][client] = 1;
        } else {
            gs->second[client]++;
        }
        // now verify
        SequenceMap::iterator s = sequenceMap.find(groupId);
        if (s == sequenceMap.end()) {
            QPID_LOG(debug,  "Client " << client << " thinks this is the first message from group " << groupId << ":" << sequence);
            // if duplication allowed, it is possible that the last msg(s) of an old sequence are redelivered on reconnect.
            // in this case, set the sequence from the first msg.
            sequenceMap[groupId] = (allowDuplicates) ? sequence : 0;
            s = sequenceMap.find(groupId);
        } else if (sequence < s->second) {
            duplicateMsgs++;
            QPID_LOG(debug, "Client " << client << " thinks this message is a duplicate! " << groupId << ":" << sequence);
            return allowDuplicates;
        }
        totalMsgsConsumed++;
        return sequence == s->second++;
    }

    void sendingSequence( const std::string& groupId,
                          uint sequence, bool eos,
                          const std::string& client )
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        ++totalMsgsPublished;

        QPID_LOG(debug, "Client " << client << " sending " << groupId << ":" << sequence <<
                 ((eos) ? " (last)" : ""));
    }

    bool eraseGroup( const std::string& groupId, const std::string& name )
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        QPID_LOG(debug, "Deleting group " << groupId << " (by client " << name << ")");
        return sequenceMap.erase( groupId ) == 1;
    }

    uint getNextExpectedSequence( const std::string& groupId )
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        return sequenceMap[groupId];
    }

    bool allMsgsPublished() // true when done publishing msgs
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        return (producerCt == 0 || totalMsgsPublished >= totalMsgs);
    }

    bool allMsgsConsumed()  // true when done consuming msgs
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        return (consumerCt == 0 ||
                (totalMsgsConsumed >= totalMsgs && sequenceMap.size() == 0));
    }

    uint getTotalMessages()
    {
        return totalMsgs;
    }

    uint getConsumedTotal()
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        return totalMsgsConsumed;
    }

    uint getPublishedTotal()
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        return totalMsgsPublished;
    }

    ostream& print(ostream& out)
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        out << "Total Published: " << totalMsgsPublished << ", Total Consumed: " << totalMsgsConsumed <<
          ", Duplicates detected: " << duplicateMsgs << std::endl;
        out << "Total Groups: " << statistics.size() << std::endl;
        unsigned long consumers = 0;
        for (GroupStatistics::iterator gs = statistics.begin(); gs != statistics.end(); ++gs) {
            out << "  GroupId: " << gs->first;
            consumers += gs->second.size(); // # of consumers that processed this group
            if (gs->second.size() == 1)
                out << " completely consumed by a single client." << std::endl;
            else
                out << " consumed by " << gs->second.size() << " different clients." << std::endl;

            for (ClientCounter::iterator cc = gs->second.begin(); cc != gs->second.end(); ++cc) {
                out << "    Client: " << cc->first << " consumed " << cc->second << " messages from the group." << std::endl;
            }
        }
        out << "Average # of consumers per group: " << ((statistics.size() != 0) ? (double(consumers)/statistics.size()) : 0) << std::endl;
        return out;
    }
};


namespace {
    // rand() is not thread safe.  Create a singleton obj to hold a lock while calling
    // rand() so it can be called safely by multiple concurrent clients.
    class Randomizer {
        qpid::sys::Mutex lock;
    public:
        uint operator()(uint max) {
            qpid::sys::Mutex::ScopedLock l(lock);
            return (rand() % max) + 1;
        }
    };

    static Randomizer randomizer;
}


// tag each generated message with a group identifer
//
class GroupGenerator {

    const std::string groupPrefix;
    const uint groupSize;
    const bool randomizeSize;
    const uint interleave;

    uint groupSuffix;
    uint total;

    struct GroupState {
        std::string id;
        const uint size;
        uint count;
        GroupState( const std::string& i, const uint s )
            : id(i), size(s), count(0) {}
    };
    typedef std::list<GroupState> GroupList;
    GroupList groups;
    GroupList::iterator current;

    // add a new group identifier to the list
    void newGroup() {
        std::ostringstream groupId(groupPrefix, ios_base::out|ios_base::ate);
        groupId << std::string(":") << groupSuffix++;
        uint size = (randomizeSize) ? randomizer(groupSize) : groupSize;
        QPID_LOG(trace, "New group: GROUPID=[" << groupId.str() << "] size=" << size << " this=" << this);
        GroupState group( groupId.str(), size );
        groups.push_back( group );
    }

public:
    GroupGenerator( const std::string& prefix,
                    const uint t,
                    const uint size,
                    const bool randomize,
                    const uint i)
        : groupPrefix(prefix), groupSize(size),
          randomizeSize(randomize), interleave(i), groupSuffix(0), total(t)
    {
        QPID_LOG(trace, "New group generator: PREFIX=[" << prefix << "] total=" << total << " size=" << size << " rand=" << randomize << " interleave=" << interleave << " this=" << this);
        for (uint i = 0; i < 1 || i < interleave; ++i) {
            newGroup();
        }
        current = groups.begin();
    }

    bool genGroup(std::string& groupId, uint& seq, bool& eos)
    {
        if (!total) return false;
        --total;
        if (current == groups.end())
            current = groups.begin();
        groupId = current->id;
        seq = current->count++;
        if (current->count == current->size) {
            QPID_LOG(trace, "Last msg for " << current->id << ", " << current->count << " this=" << this);
            eos = true;
            if (total >= interleave) {  // need a new group to replace this one
                newGroup();
                groups.erase(current++);
            } else ++current;
        } else {
            ++current;
            eos = total < interleave;   // mark eos on the last message of each group
        }
        QPID_LOG(trace, "SENDING GROUPID=[" << groupId << "] seq=" << seq << " eos=" << eos << " this=" << this);
        return true;
    }
};



class Client : public qpid::sys::Runnable
{
public:
    typedef boost::shared_ptr<Client> shared_ptr;
    enum State {ACTIVE, DONE, FAILURE};
    Client( const std::string& n, const Options& o ) : name(n), opts(o), state(ACTIVE), stopped(false) {}
    virtual ~Client() {}
    State getState() { return state; }
    void testFailed( const std::string& reason ) { state = FAILURE; error << "Client '" << name << "' failed: " << reason; }
    void clientDone() { if (state == ACTIVE) state = DONE; }
    qpid::sys::Thread& getThread() { return thread; }
    const std::string getErrorMsg() { return error.str(); }
    void stop() {stopped = true;}
    const std::string& getName() { return name; }

protected:
    const std::string name;
    const Options& opts;
    qpid::sys::Thread thread;
    ostringstream error;
    State state;
    bool stopped;
};


class Consumer : public Client
{
    GroupChecker& checker;

public:
    Consumer(const std::string& n, const Options& o, GroupChecker& c ) : Client(n, o), checker(c) {};
    virtual ~Consumer() {};

    void run()
    {
        Connection connection;
        try {
            connection = Connection(opts.url, opts.connectionOptions);
            connection.open();
            std::auto_ptr<FailoverUpdates> updates(opts.failoverUpdates ? new FailoverUpdates(connection) : 0);
            Session session = connection.createSession();
            Receiver receiver = session.createReceiver(opts.address);
            receiver.setCapacity(opts.capacity);
            Message msg;
            uint count = 0;

            while (!stopped) {
                if (receiver.fetch(msg, Duration::SECOND)) { // msg retrieved
                    qpid::types::Variant::Map& properties = msg.getProperties();
                    std::string groupId = properties[opts.groupKey];
                    uint groupSeq = properties[SN];
                    bool eof = properties[EOS];

                    QPID_LOG(trace, "RECVING GROUPID=[" << groupId << "] seq=" << groupSeq << " eos=" << eof << " name=" << name);

                    qpid::sys::usleep(10);

                    if (!checker.checkSequence( groupId, groupSeq, name )) {
                        ostringstream msg;
                        msg << "Check sequence failed.  Group=" << groupId << " rcvd seq=" << groupSeq << " expected=" << checker.getNextExpectedSequence( groupId );
                        testFailed( msg.str() );
                        break;
                    } else if (eof) {
                        if (!checker.eraseGroup( groupId, name )) {
                            ostringstream msg;
                            msg << "Erase group failed.  Group=" << groupId << " rcvd seq=" << groupSeq;
                            testFailed( msg.str() );
                            break;
                        }
                    }

                    ++count;
                    if (opts.ackFrequency && (count % opts.ackFrequency == 0)) {
                        session.acknowledge();
                    }
                    // Clear out message properties & content for next iteration.
                    msg = Message(); // TODO aconway 2010-12-01: should be done by fetch
                } else if (checker.allMsgsConsumed())   // timed out, nothing else to do?
                    break;
            }
            session.acknowledge();
            session.close();
            connection.close();
        } catch(const std::exception& error) {
            ostringstream msg;
            msg << "consumer error: " << error.what();
            testFailed( msg.str() );
            connection.close();
        }
        clientDone();
        QPID_LOG(trace, "Consuming client " << name << " completed.");
    }
};



class Producer : public Client
{
    GroupChecker& checker;
    GroupGenerator generator;

public:
    Producer(const std::string& n, const Options& o, GroupChecker& c)
        : Client(n, o), checker(c),
          generator( n, o.messages, o.groupSize, o.randomizeSize, o.interleave )
    {};
    virtual ~Producer() {};

    void run()
    {
        Connection connection;
        try {
            connection = Connection(opts.url, opts.connectionOptions);
            connection.open();
            std::auto_ptr<FailoverUpdates> updates(opts.failoverUpdates ? new FailoverUpdates(connection) : 0);
            Session session = connection.createSession();
            Sender sender = session.createSender(opts.address);
            if (opts.capacity) sender.setCapacity(opts.capacity);
            Message msg;
            msg.setDurable(opts.durable);
            std::string groupId;
            uint seq;
            bool eos;
            uint sent = 0;

            qpid::sys::AbsTime start = qpid::sys::now();
            int64_t interval = 0;
            if (opts.sendRate) interval = qpid::sys::TIME_SEC/opts.sendRate;

            while (!stopped && generator.genGroup(groupId, seq, eos)) {
                msg.getProperties()[opts.groupKey] = groupId;
                msg.getProperties()[SN] = seq;
                msg.getProperties()[EOS] = eos;
                checker.sendingSequence( groupId, seq, eos, name );

                sender.send(msg);
                ++sent;

                if (opts.sendRate) {
                    qpid::sys::AbsTime waitTill(start, sent*interval);
                    int64_t delay = qpid::sys::Duration(qpid::sys::now(), waitTill);
                    if (delay > 0) qpid::sys::usleep(delay/qpid::sys::TIME_USEC);
                }
            }
            session.sync();
            session.close();
            connection.close();
        } catch(const std::exception& error) {
            ostringstream msg;
            msg << "producer '" << name << "' error: " << error.what();
            testFailed(msg.str());
            connection.close();
        }
        clientDone();
        QPID_LOG(trace, "Producing client " << name << " completed.");
    }
};


}} // namespace qpid::tests

using namespace qpid::tests;

int main(int argc, char ** argv)
{
    int status = 0;
    try {
        Options opts;
        if (opts.parse(argc, argv)) {

            GroupChecker state( opts.messages,
                                opts.receivers,
                                opts.senders,
                                opts.allowDuplicates);
            std::vector<Client::shared_ptr> clients;

            if (opts.randomizeSize) srand((unsigned int)qpid::sys::SystemInfo::getProcessId());

            // fire off the producers && consumers
            for (size_t j = 0; j < opts.senders; ++j)  {
                ostringstream name;
                name << opts.prefix << "P_" << j;
                clients.push_back(Client::shared_ptr(new Producer( name.str(), opts, state )));
                clients.back()->getThread() = qpid::sys::Thread(*clients.back());
            }
            for (size_t j = 0; j < opts.receivers; ++j)  {
                ostringstream name;
                name << opts.prefix << "C_" << j;
                clients.push_back(Client::shared_ptr(new Consumer( name.str(), opts, state )));
                clients.back()->getThread() = qpid::sys::Thread(*clients.back());
            }

            // wait for all pubs/subs to finish.... or for consumers to fail or stall.
            uint stalledTime = 0;
            bool clientFailed = false;
            while (!clientFailed && (!state.allMsgsPublished() || !state.allMsgsConsumed())) {
                uint lastCount;

                lastCount = state.getConsumedTotal();
                qpid::sys::usleep( 1000000 );

                // check each client for failures
                for (std::vector<Client::shared_ptr>::iterator i = clients.begin();
                     i != clients.end(); ++i) {
                    QPID_LOG(debug, "Client " << (*i)->getName() << " state=" << (*i)->getState());
                    if ((*i)->getState() == Client::FAILURE) {
                        QPID_LOG(error, argv[0] << ": test failed with client error: " << (*i)->getErrorMsg());
                        clientFailed = true;
                        break;  // exit test.
                    }
                }

                // check for stalled consumers
                if (!clientFailed && !state.allMsgsConsumed()) {
                    if (lastCount == state.getConsumedTotal()) {
                        if (++stalledTime >= opts.timeout) {
                            clientFailed = true;
                            break;  // exit test
                        }
                    } else {
                        stalledTime = 0;
                    }
                }
                QPID_LOG(debug, "Consumed to date = " << state.getConsumedTotal() <<
                         " Published to date = " << state.getPublishedTotal() <<
                         " total=" << state.getTotalMessages());
            }

            if (clientFailed) {
                if (stalledTime >= opts.timeout) {
                    QPID_LOG(error, argv[0] << ": test failed due to stalled consumer." );
                    status = 2;
                } else {
                    status = 1;
                }
            }

            // Wait for started threads.
            for (std::vector<Client::shared_ptr>::iterator i = clients.begin();
                 i != clients.end(); ++i) {
                (*i)->stop();
                (*i)->getThread().join();
            }

            if (opts.printReport && !status) state.print(std::cout);
        } else status = 4;
    } catch(const std::exception& error) {
        QPID_LOG(error, argv[0] << ": " << error.what());
        status = 3;
    }
    QPID_LOG(trace, "TEST DONE [" << status << "]");

    return status;
}
