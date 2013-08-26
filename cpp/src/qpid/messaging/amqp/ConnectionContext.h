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
#include <memory>
#include <string>
#include <boost/shared_ptr.hpp>
#include "qpid/Url.h"
#include "qpid/messaging/ConnectionOptions.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/Monitor.h"
#include "qpid/types/Variant.h"
#include "qpid/messaging/amqp/TransportContext.h"

struct pn_connection_t;
struct pn_link_t;
struct pn_session_t;
struct pn_transport_t;


namespace qpid {
namespace framing {
class ProtocolVersion;
}
namespace sys {
struct SecuritySettings;
}
namespace messaging {
class Duration;
class Message;
namespace amqp {

class DriverImpl;
class ReceiverContext;
class Sasl;
class SessionContext;
class SenderContext;
class Transport;

/**
 *
 */
class ConnectionContext : public qpid::sys::ConnectionCodec, public qpid::messaging::ConnectionOptions, public TransportContext
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
    void detach(boost::shared_ptr<SessionContext>, boost::shared_ptr<SenderContext>);
    void detach(boost::shared_ptr<SessionContext>, boost::shared_ptr<ReceiverContext>);
    void send(boost::shared_ptr<SessionContext>, boost::shared_ptr<SenderContext> ctxt, const qpid::messaging::Message& message, bool sync);
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


    void activateOutput();
    qpid::sys::Codec& getCodec();
    //ConnectionCodec interface:
    std::size_t decode(const char* buffer, std::size_t size);
    std::size_t encode(char* buffer, std::size_t size);
    bool canEncode();
    void closed();
    bool isClosed() const;
    framing::ProtocolVersion getVersion() const;
    //additionally, Transport needs:
    void opened();//signal successful connection
    const qpid::sys::SecuritySettings* getTransportSecuritySettings();

  private:
    typedef std::map<std::string, boost::shared_ptr<SessionContext> > SessionMap;
    qpid::Url url;

    boost::shared_ptr<DriverImpl> driver;
    boost::shared_ptr<Transport> transport;

    pn_transport_t* engine;
    pn_connection_t* connection;
    SessionMap sessions;
    mutable qpid::sys::Monitor lock;
    bool writeHeader;
    bool readHeader;
    bool haveOutput;
    std::string id;
    enum {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    } state;
    std::auto_ptr<Sasl> sasl;
    class CodecSwitch : public qpid::sys::Codec
    {
      public:
        CodecSwitch(ConnectionContext&);
        std::size_t decode(const char* buffer, std::size_t size);
        std::size_t encode(char* buffer, std::size_t size);
        bool canEncode();
      private:
        ConnectionContext& parent;
    };
    CodecSwitch codecSwitch;

    void check();
    void wait();
    void waitUntil(qpid::sys::AbsTime until);
    void wait(boost::shared_ptr<SessionContext>);
    void waitUntil(boost::shared_ptr<SessionContext>, qpid::sys::AbsTime until);
    void wait(boost::shared_ptr<SessionContext>, boost::shared_ptr<ReceiverContext>);
    void wait(boost::shared_ptr<SessionContext>, boost::shared_ptr<SenderContext>);
    void waitUntil(boost::shared_ptr<SessionContext>, boost::shared_ptr<ReceiverContext>, qpid::sys::AbsTime until);
    void waitUntil(boost::shared_ptr<SessionContext>, boost::shared_ptr<SenderContext>, qpid::sys::AbsTime until);
    void checkClosed(boost::shared_ptr<SessionContext>);
    void checkClosed(boost::shared_ptr<SessionContext>, boost::shared_ptr<ReceiverContext>);
    void checkClosed(boost::shared_ptr<SessionContext>, boost::shared_ptr<SenderContext>);
    void checkClosed(boost::shared_ptr<SessionContext>, pn_link_t*);
    void wakeupDriver();
    void attach(pn_session_t*, pn_link_t*, int credit=0);

    std::size_t readProtocolHeader(const char* buffer, std::size_t size);
    std::size_t writeProtocolHeader(char* buffer, std::size_t size);
    std::string getError();
    bool useSasl();
    void setProperties();
};

}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_CONNECTIONCONTEXT_H*/
