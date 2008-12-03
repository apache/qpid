#include <qpid/Options.h>
#include <qpid/client/Message.h>
#include <qpid/client/Connection.h>
#include <qpid/client/AsyncSession.h>
#include <qpid/client/SubscriptionManager.h>
#include <qpid/client/QueueOptions.h>
#include <qpid/sys/Time.h>
#include <boost/ptr_container/ptr_vector.hpp>
#include <boost/bind.hpp>
#include <algorithm>
#include <limits>
#include <unistd.h>
#include <iostream>
#include <fstream>
#include <netinet/in.h>
#include <netinet/tcp.h>

using namespace std;
using namespace qpid;
using namespace qpid::client;
using namespace qpid::sys;

struct Opts : public qpid::Options {
    bool help;
    bool sms, smr, tc, report, durable, clean;
    string host;
    int port;
    string host2;
    int port2;
    int rate;
    int start_from;
    int id;
    int symbols;
    int sessions;
    int messages;
    int depth;
    int sym_a;
    int sym_b;
    
    // port 5672
    Opts() : help(false), sms(false), smr(false), tc(false), report(false), durable(false), clean(false),
             host("127.0.0.1"), port(5672), host2("127.0.0.1"), port2(5672), 
             rate(10000), start_from(0), id(0), symbols(10), sessions(10), messages(10), depth(10000), sym_a(0), sym_b(9)
    {
        addOptions()
            ("help", optValue(help), "print this help message")
            ("sms", optValue(sms), "session sender")
            ("smr", optValue(smr), "session receiver")
            ("tc", optValue(tc), "trading component")
            ("report", optValue(report), "report results when test is complete")
            ("host,h", optValue(host, "HOST"), "Broker host to connect to")
            ("port,p", optValue(port, "PORT"), "Broker port to connect to")
            ("HOST,H", optValue(host2, "HOST2"), 
             "Broker host2 to connect to (applicable to TC only)")
            ("PORT,P", optValue(port2, "PORT2"), 
             "Broker port2 to connect to (applicable to TC only)")
            ("id", optValue(id, "N"), "Id number for this process")
            ("start_from", optValue(start_from, "START"), "Client numbers start from N")
            ("symbols", optValue(symbols, "N"), "Use N stock symbols")
            ("sessions", optValue(sessions, "N"), "Use N sessions (sms)")
            ("messages", optValue(messages, "N"), "Send N messages")
            ("durable_msg", optValue(durable), "use durable messages")
            ("clean", optValue(clean), "purge queues")
            ("depth", optValue(depth, "DEPTH"), "queue depth")
            ("rate", optValue(rate, "N"), "Send messages at rate of N per second, 0 means fast as possible.")
            ("sym_a", optValue(sym_a, "a"), "Start symbols from TSX.a")
            ("sym_b", optValue(sym_b, "b"), "Last symbol is TSX.b")
            ;
    }
};

ostream& operator<<(ostream&o, const Opts& opts) {
    o << static_cast<const qpid::Options&>(opts) << endl;
    return o;
}

Opts opts; // Global, used by all functions.

int
getRandomNumber(int low, int high)
{
  struct timeval tv;
  gettimeofday(&tv, 0);

  srand48(tv.tv_usec);
  double r = drand48();

  int ret = int( double(high - low + 1) * r) + low;

  return ret;
}

// Order entry message format.
struct OrderEntry {
    int sessionId;
    int msgId;
    int responses;
    int responseId;
    int next_sess;
    int add_size;
    AbsTime time[4];

    OrderEntry() : sessionId(), msgId(), responses(), responseId(), time() {
       add_size = getRandomNumber(200, 500) - sizeof(OrderEntry);
    }
    void clone(OrderEntry* oe) {
      sessionId = oe->sessionId;
      msgId = oe->msgId;
      responses = oe->responses;
      responseId = oe->responseId;
      next_sess = oe->next_sess;
      add_size = oe->add_size;
      time[0] = oe->time[0];
      time[1] = oe->time[1];
      time[2] = oe->time[2]; 
      time[3] = oe->time[3];
    }
};
//} __attribute__ ((packed));

// Trivial "encoding" just copies struct memory. Assumes all machines
// are same architecture and compiler.
template <class T> Message makeMessage(const T& data, const string& key=string(), const string& /*exch*/=string()) {
    Message message(string(reinterpret_cast<const char*>(&data), sizeof(data)) + string(data.add_size, 'A'), key);
// Using default exchange
//    message.getDeliveryProperties().setExchange(exch);
    if (!opts.durable) {
      message.getDeliveryProperties().setDeliveryMode(qpid::framing::TRANSIENT);
    } else {
      message.getDeliveryProperties().setDeliveryMode(qpid::framing::PERSISTENT);
    }
    return message;
}

ostream& printMs(ostream& o, Duration d) {
    return o << (d / TIME_MSEC) << "ms";
}

// Format a delay as a string.
string delayStr(AbsTime begin, AbsTime end) {
    ostringstream os;
    printMs(os, Duration(begin, end));
    return os.str();
}

    string timeStr(AbsTime t) {
        ostringstream os;
        os << Duration(t);
        return os.str();
    }

ostream& operator << (ostream& o, const OrderEntry& oe) {
    return o << "OrderEntry["
             << "session=" << oe.sessionId
             << " message=" << oe.msgId 
             << " responses=" << oe.responses
             << " responseId=" << oe.responseId
	     << " additional size=" << oe.add_size
             << " delay[0-1]=" << delayStr(oe.time[0], oe.time[1])
             << " delay[1-2]=" << delayStr(oe.time[2], oe.time[3])
             << " TS[0]=" << timeStr(oe.time[0]) 
             << " TS[1]=" << timeStr(oe.time[1])
             << " TS[2]=" << timeStr(oe.time[2])
             << " TS[3]=" << timeStr(oe.time[3])
             //<< " body=" << oe.body
             << "]";
}

const char* RESULTS="results";

// Base class for all the clients.
struct Client : public Runnable {
    int id;
    Connection connection;
    AsyncSession amqpSession;
    Thread thread;

    Client() : id(opts.id) {
        ConnectionSettings cs;
        cs.host = opts.host;
        cs.port = opts.port;
        cs.tcpNoDelay = true;
        connection.open(cs);
        amqpSession = async(connection.newSession());
        // Declare symbol queues
        for (int i = 0; i < opts.symbols; ++i) { 
            if (opts.clean)
              amqpSession.queueDelete(arg::queue=symbolQueue(i));
            QueueOptions options;
            // options.setSizePolicy(FLOW_TO_DISK, 0, opts.depth);
            amqpSession.queueDeclare(arg::queue=symbolQueue(i), arg::durable=true, arg::arguments=options);
        }
        // Declare session response queues
        //for (int i = 0; i < opts.sessions; ++i) 
        //    amqpSession.queueDeclare(arg::queue=sessionQueue(i), arg::durable=true);
        amqpSession.queueDeclare(arg::queue=RESULTS);
    }

    ~Client() {
        amqpSession.close();
        connection.close();
        join();
    }

    void start() { 
        thread = Thread(*this);
    }

    void join() {
        thread.join();
        thread = Thread();      // Avoid double join.
    }

    void sleep_until(const AbsTime& t) {
        Duration d(now(), t);
        if (int64_t(d) > 0)
            qpid::sys::usleep(int64_t(d)/TIME_USEC);
    }

    // Name of symbol queue n
    string symbolQueue(int n) {
        ostringstream sym;
        sym << "TSX." << n%opts.symbols;
        return sym.str();
    }

    // Name of session n
    string sessionQueue(int n) {
        ostringstream sym;
        sym << "SMR." << n;
        return sym.str();
    }

};

struct Clean : public Client {
    void run() {
        if (opts.durable)
            cout << "DURABLE messages" << endl;
        else
            cout << "TRANSIENT messages" << endl;
        cout << "Cleaning queues" << endl;
        // Purge any old data in the test queues.
        // NOTE: the sync() call waits till the command completes.
        // Otherwise we might start the tests before the purges were complete.
        for (int i = 0; i < opts.symbols; ++i) { 
            amqpSession.queuePurge(arg::queue=symbolQueue(i));
            amqpSession.sync();
        }
//        for (int i = 0; i < opts.sessions; ++i) { 
//            amqpSession.queuePurge(arg::queue=sessionQueue(i));
//            amqpSession.sync();
//        }
        amqpSession.queuePurge(arg::queue=RESULTS);
        amqpSession.sync();
    }
};
    
// Base class for subscriber clients
struct SubClient : public Client, public MessageListener {
    SubscriptionManager subs;
    SubClient() : subs(amqpSession) {}
    ~SubClient() { stop(); }
    void stop() { subs.stop(); join(); }
};
    
    
struct Sms : public Client {

    Sms() {
        // Declare session response queues
        int ini = opts.start_from;
        for (int i = ini; i < (ini+opts.sessions); ++i) {
            if (opts.clean)
              amqpSession.queueDelete(arg::queue=sessionQueue(i));
            QueueOptions options;
            // options.setSizePolicy(FLOW_TO_DISK, 0, opts.depth);
            amqpSession.queueDeclare(arg::queue=sessionQueue(i), arg::durable=true, arg::arguments=options);
        }
    }
    void run() {
        int my_id = id+opts.start_from; 
        cout << "SMS " << my_id << endl;
        AbsTime start = now();
        int64_t interval = opts.rate ? TIME_SEC/opts.rate : 0;
        for (int i = 0;  i < opts.messages; ++i) {
            OrderEntry oe;
            oe.sessionId = my_id;
            oe.msgId = i;
            if (opts.rate)
                sleep_until(AbsTime(start, i*interval)); 
            int s = opts.sym_a + i%(opts.sym_b - opts.sym_a); 
            string sym = symbolQueue(s);
	    oe.responseId = 0;
            oe.responses = ((i+1)%3 == 0) ? 2 : 1; // 2 responses for every 3rd message.
            oe.next_sess = oe.sessionId+1;
            oe.time[0] = now();
            // Send to queue sym via default exchange
            amqpSession.messageTransfer(arg::content=makeMessage(oe, sym));
            if (i && i%1000==0)
                cout << "SMS " << my_id << " sent " << i << " messages" << endl;
        }
        cout << "SMS " << my_id << " done" << endl;
    }
};


struct Tc : public SubClient {

    Connection connection2;
    AsyncSession out_sess;
    bool use_other_session;
    int msgs;
    Tc() : use_other_session(false), msgs(0) {
        if (opts.host != opts.host2) {
            ConnectionSettings cs2;
            cs2.host = opts.host2;
            cs2.port = opts.port2;
            cs2.tcpNoDelay = true;
            connection2.open(cs2);
            out_sess = async(connection2.newSession());
            use_other_session = true;
            // Declare session response queues
            int ini = opts.start_from;
            for (int i = ini; i < ini+opts.sessions; ++i) {
                if (opts.clean)
                    out_sess.queueDelete(arg::queue=sessionQueue(i));
                QueueOptions options;
                /// options.setSizePolicy(FLOW_TO_DISK, 0, opts.depth);
                out_sess.queueDeclare(arg::queue=sessionQueue(i), arg::durable=true, arg::arguments=options);
                if(opts.clean) {
                  out_sess.queuePurge(arg::queue=sessionQueue(i));
                  out_sess.sync();
                }
            }
        } else {
            int ini = opts.start_from;    
            for (int i = ini; i < ini+opts.sessions; ++i) {
                if (opts.clean)
                    amqpSession.queueDelete(arg::queue=sessionQueue(i));
                QueueOptions options;
                // options.setSizePolicy(FLOW_TO_DISK, 0, opts.depth);
                amqpSession.queueDeclare(arg::queue=sessionQueue(i), arg::durable=true, arg::arguments=options);
                if(opts.clean) {
                  amqpSession.queuePurge(arg::queue=sessionQueue(i));
                  amqpSession.sync();
                }
            }
            //cout << "Warning: source and destination for TC are identical" << endl;
        }
    }
    
    ~Tc() { if(use_other_session) connection2.close(); }

    void received(Message& m) {
        //assert(m.getData().size() == sizeof(OrderEntry));
        OrderEntry oe(*reinterpret_cast<const OrderEntry*>(m.getData().data()));
        oe.time[1] = now();
        for (int i = 0; i < oe.responses; ++i) {
            msgs++;
            oe.responseId = i+1;
            string key = sessionQueue(oe.sessionId);
            if (i > 0) {
                if (oe.sessionId+i == opts.sessions+opts.start_from) {
                    key = sessionQueue(opts.start_from); 
                } else {
                    key = sessionQueue(oe.sessionId+1);
                }
            }
//if(id == 0)
//cout << "[" << msgs << "] RESPOND TO " << i << "/" << oe.responses << " " << oe.sessionId << " " << key << endl;

            oe.time[2]=now();
            if(use_other_session) { 
                out_sess.messageTransfer(arg::content=makeMessage(oe, key));
            } else {
                amqpSession.messageTransfer(arg::content=makeMessage(oe, key));
            }
            if (msgs && msgs%1000==0)
                cout << "TC " << id << " processed" << msgs << endl;
            //cout << "Tc " << id << " sent to " << key << endl;
        }
    }
    
    void run() {
        if ((id >= opts.sym_a) && (id <= opts.sym_b)) {
            cout << "TC " << symbolQueue(id) << endl;
            //QPID_LOG(info, "TC subscribing to " << symbolQueue(id));
            subs.subscribe(*this, symbolQueue(id));
            subs.run();
        }
    }
};

// Average results per SMR or overall.
struct SMRStats {
    int sessionId;
    long messages;              // Total received.
    AbsTime begin, end;         // Total time from first sent to last received.
    int64_t delay[3];           // Total latency 0-1, 2-3, 0-3
    int64_t max_d;
    int add_size;

    SMRStats() : sessionId(), messages(0), begin(), end(), delay(), add_size(0) 
    {
      delay[0]=0;
      delay[1]=0; 
      max_d=0;
    }

    int64_t time() const {  return Duration(begin, end); }

    long throughput() const {
        int64_t timeMs=time()/TIME_MSEC;
        return 1000*messages/timeMs;
    }

    int64_t latency(int i) const { return delay[i]/messages; }
        
    void add(const OrderEntry& oe) {
        if (messages == 0) begin = oe.time[0];
        end = oe.time[3];
        int64_t d1 = Duration(oe.time[0], oe.time[1]);
        int64_t d2 = Duration(oe.time[2], oe.time[3]);
        int64_t d3 = Duration(oe.time[0], oe.time[3]);
        delay[0] += d1;
        delay[1] += d2;
        delay[2] += d3;
        if (d3 > max_d) max_d = d3;
        messages++;
    }
};


ostream& operator<<(ostream& o, const SMRStats& r) {
    o << "SMRStats["
      << "session=" << r.sessionId
      << " time=";
    printMs(o, r.time());
    o << " messages=" << r.messages
      << " latency(0)=" << r.latency(0)
      << " latency(1)=" << r.latency(1)
      << " throughput=" << r.throughput()
      << "]";
    return o;
}

struct Smr : public SubClient {

    SMRStats result;
    int expect;              // # messages to expect
    list<OrderEntry*> msgList;
    int my_id;

    Smr() : expect(opts.messages + opts.messages/3) {
        // Declare session response queues
        int ini = opts.start_from;
        for (int i = ini; i < ini+opts.sessions; ++i) {
            if (opts.clean)
              amqpSession.queueDelete(arg::queue=sessionQueue(i));
            QueueOptions options;
            // options.setSizePolicy(FLOW_TO_DISK, 0, opts.depth);
            amqpSession.queueDeclare(arg::queue=sessionQueue(i), arg::durable=true, arg::arguments=options);
        }
    }

    void received(Message& m) {
        //assert(m.getData().size() == sizeof(OrderEntry));
        OrderEntry oe(*reinterpret_cast<const OrderEntry*>(m.getData().data()));
        oe.time[3] = now();
        //QPID_LOG(info, "SMR " << my_id << " received: " << oe);
//cout << " id: " << oe.msgId << " session id: " << oe.sessionId << endl; 
//" received data: " << (m.getData().data() + sizeof(OrderEntry))  << endl;
        result.add(oe);

        OrderEntry* new_oe = new OrderEntry();
        new_oe->clone(&oe);
        msgList.push_back(new_oe);
        if (result.messages && result.messages%1000==0)
            cout << "SMR " << my_id << " received " << result.messages << endl;
        if (result.messages == expect) {
            subs.stop();
            amqpSession.messageTransfer(
                arg::content=makeMessage(result, RESULTS));
            cout << result << endl;

            std::stringstream filename;
            filename << "ReceivedMessages" << id+opts.start_from << ".dat";
            ofstream* datFileStream_ = new ofstream(filename.str().c_str(), ios::trunc);
            for(list<OrderEntry*>::const_iterator i = msgList.begin(); 
                  i != msgList.end(); ++i) 
            {
              (*datFileStream_) << **i << endl; 
              delete *i;
            }
            msgList.clear();
            delete datFileStream_;
        }
    }

    void run() {
        my_id = id + opts.start_from;
        cout << "SMR " << my_id << endl;
        result.sessionId = my_id;
        //QPID_LOG(info, "SMR subscribing to " << sessionQueue(my_id));
        subs.subscribe(*this, sessionQueue(my_id));
        subs.run();
        cout << "SMR " << my_id << " done" << endl;
    }
};

struct Report : public SubClient {
    long reports;
    long messages;
    int64_t time;
    int64_t latency[2];
    int64_t max_l;
    long throughput;
    
    int expect;                 // # reports expected
    Report() : reports(0), messages(0), time(0), latency(), throughput(0),
               expect(opts.sessions) {
        latency[0] = 0;
        latency[1] = 0;
        latency[2] = 0;
    }
    
    void received(Message& m) {
        max_l = 0;
        assert(m.getData().size() == sizeof(SMRStats));
        SMRStats r(*reinterpret_cast<const SMRStats*>(m.getData().data()));
        //QPID_LOG(info, "Report received: " << r);
        reports++;
        messages += r.messages;
        time += r.time();
        latency[0] += r.latency(0);
        latency[1] += r.latency(1);
        latency[2] += r.latency(2);
        if (r.max_d > max_l) max_l = r.max_d;
        throughput += r.throughput();
        
        if (--expect == 0) {
            subs.stop();
            cout  << endl << "Aggregate results: " << endl
                  << "Sessions: " << opts.sessions << " (from " << opts.start_from << ")" << endl
                  << "Messages per smr: " << messages/reports << endl
                  << "Avg time per smr: ";
            printMs(cout, time/reports);
            cout << endl
                 << "Avg throughput (msgs/sec) per smr: " << throughput/reports << endl
                 << "Avg latency per smr (0-1, 2-3): ";
            printMs(cout, latency[0]/reports);
            cout << ", ";
            printMs(cout, latency[1]/reports);
            cout << endl
                 << "Avg latency (0-3): ";
            printMs(cout, (latency[2])/reports);
            cout << endl 
                 << "Max message latency: ";
            printMs(cout, max_l);
            cout << endl;
        }
    }

    void run() {
        //QPID_LOG(info, "Reporter subscribing to " << RESULTS);
        subs.subscribe(*this, RESULTS);
        subs.run();
    }
};

template <class C> struct Clients {
    boost::ptr_vector<C> clients;

    Clients(bool enabled, int count) {
        if (!enabled) return;
        for (int i = 0; i < count; ++i) {
            C* cc = new C();
            clients.push_back(cc);
            clients.back().id = i;
        }
    }
    void run() {
        for_each(clients.begin(), clients.end(), mem_fun_ref(&Client::start));
    }
    void join() {
        for_each(clients.begin(), clients.end(), mem_fun_ref(&Client::join));
    }
    void stop() {
        for_each(clients.begin(), clients.end(), mem_fun_ref(&SubClient::stop));
    }
};    
    
int main(int argc, char** argv) {
    try {
        opts.parse(argc, argv);
        // If no action specified.
        if (!opts.sms && !opts.tc && !opts.smr ) {
            opts.sms = opts.tc = opts.smr = true;
        }

        if (opts.help) 
            cout << opts;
        else {
            if (opts.clean) Clean().run();
            Clients<Sms> sms(opts.sms, opts.sessions);
            Clients<Tc> tc(opts.tc, opts.symbols);
            Clients<Smr> smr(opts.smr, opts.sessions);

            smr.run();
            tc.run();
            sms.run(); 

            sms.join();
            smr.join();
            if (opts.sms && opts.smr) 
                tc.stop();      // Leave TCs running if SMS or SMR are in a different process
            else
                tc.join();
            if (opts.report) Report().run();
        }
        return 0;
    }
    catch (const exception& e) {
        cerr << "Exception: " << e.what() << endl;
        return 1;
    }
}
