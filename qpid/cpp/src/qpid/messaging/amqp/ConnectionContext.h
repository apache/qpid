#ifndef QPID_MESSAGING_AMQP_CONNECTIONCONTEXT_H
#define QPID_MESSAGING_AMQP_CONNECTIONCONTEXT_H

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
#include <deque>
#include <map>
#include <string>
#include <boost/shared_ptr.hpp>
#include "qpid/Url.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/types/Variant.h"
extern "C" {
#include <proton/engine.h>
#include <proton/driver.h>
}

namespace qpid {
namespace messaging {
class Duration;
class Message;
namespace amqp {

class ReceiverContext;
class SessionContext;
class SenderContext;

/**
 *
 */
class ConnectionContext : qpid::sys::Runnable
{
  public:
    ConnectionContext(const std::string& url, const qpid::types::Variant::Map& options);
    ~ConnectionContext();
    void open();
    bool isOpen() const;
    void close();
    boost::shared_ptr<SessionContext> newSession(bool transactional, const std::string& name);
    boost::shared_ptr<SessionContext> getSession(const std::string& name) const;
    void endSession(boost::shared_ptr<SessionContext>);
    void attach(boost::shared_ptr<SessionContext>, boost::shared_ptr<SenderContext>);
    void attach(boost::shared_ptr<SessionContext>, boost::shared_ptr<ReceiverContext>);
    void send(boost::shared_ptr<SenderContext> ctxt, const qpid::messaging::Message& message, bool sync);
    bool fetch(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk, qpid::messaging::Message& message, qpid::messaging::Duration timeout);
    bool get(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk, qpid::messaging::Message& message, qpid::messaging::Duration timeout);
    void acknowledge(boost::shared_ptr<SessionContext> ssn, qpid::messaging::Message* message, bool cumulative);

    void setOption(const std::string& name, const qpid::types::Variant& value);
    std::string getAuthenticatedUsername();

    void setCapacity(boost::shared_ptr<SenderContext>, uint32_t);
    uint32_t getCapacity(boost::shared_ptr<SenderContext>);
    uint32_t getUnsettled(boost::shared_ptr<SenderContext>);

    void setCapacity(boost::shared_ptr<ReceiverContext>, uint32_t);
    uint32_t getCapacity(boost::shared_ptr<ReceiverContext>);
    uint32_t getAvailable(boost::shared_ptr<ReceiverContext>);
    uint32_t getUnsettled(boost::shared_ptr<ReceiverContext>);

    /*
    void doInput();
    bool doOutput();
    time_t doTick(time_t now);
    bool read();
    void write();
    */
    void readProtocolHeader();
    void writeProtocolHeader();

  private:
    class Buffer
    {
      public:
        Buffer(size_t size=65536);
        ~Buffer();
        char* position();
        size_t available();
        size_t capacity();
        char* start();
        void advance(size_t bytes);
        void consume(size_t bytes);
      private:
        char* const data;
        const size_t size;
        size_t used;
    };
    typedef std::map<std::string, boost::shared_ptr<SessionContext> > SessionMap;
    qpid::Url url;
    pn_driver_t* driver;
    pn_connector_t* socket;
    pn_connection_t* connection;
    qpid::types::Variant::Map options;
    SessionMap sessions;
    mutable qpid::sys::Monitor lock;
    qpid::sys::Thread driverThread;
    Buffer inputBuffer;
    Buffer outputBuffer;
    bool waitingToWrite;
    qpid::sys::AtomicValue<bool> active;

    void wait();
    void wakeupDriver();
    void run();
    void attach(pn_session_t*, pn_link_t*, int credit=0);

    /*
    friend ssize_t intercept_input(pn_transport_t *transport, char *bytes, size_t available, void* context, pn_input_fn_t *next);
    friend ssize_t intercept_output(pn_transport_t *transport, char *bytes, size_t size, void* context, pn_output_fn_t *next);
    friend time_t intercept_tick(pn_transport_t *transport, time_t now, void* context, pn_tick_fn_t *next);
    */
};

}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_CONNECTIONCONTEXT_H*/
