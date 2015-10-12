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
#include "Domain.h"
#include "Interconnect.h"
#include "Interconnects.h"
#include "SaslClient.h"
#include "qpid/broker/Broker.h"
#include "qpid/Exception.h"
#include "qpid/SaslFactory.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include <boost/shared_ptr.hpp>
#include <boost/lexical_cast.hpp>
#include <sstream>

namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {
namespace amqp {

namespace {
const std::string NONE("NONE");
const std::string SOURCE("source");
const std::string TARGET("target");
const std::string URL("url");
const std::string USERNAME("username");
const std::string PASSWORD("password");
const std::string SASL_MECHANISMS("sasl_mechanisms");
const std::string SASL_SERVICE("sasl_service");
const std::string MIN_SSF("min_ssf");
const std::string MAX_SSF("max_ssf");
const std::string DURABLE("durable");
const std::string AMQP_SASL_SERVICENAME("amqp");

class Wrapper : public qpid::sys::ConnectionCodec
{
  public:
    Wrapper(boost::shared_ptr<Interconnect> c) : connection(c) {}
    ~Wrapper()
    {
        QPID_LOG(debug, "Wrapper for non-SASL based interconnect has been deleted");
        connection->transportDeleted();
    }

    std::size_t decode(const char* buffer, std::size_t size)
    {
        return connection->decode(buffer, size);
    }
    std::size_t encode(char* buffer, std::size_t size)
    {
        return connection->encode(buffer, size);
    }
    bool canEncode()
    {
        return connection->canEncode();
    }
    void closed()
    {
        connection->closed();
    }
    bool isClosed() const
    {
        QPID_LOG(debug, "Wrapper for non_SASL based interconnect " << (connection->isClosed() ? " IS " : " IS NOT ") << " closed");
        return connection->isClosed();
    }
    qpid::framing::ProtocolVersion getVersion() const
    {
        return connection->getVersion();
    }
  private:
    boost::shared_ptr<Interconnect> connection;
};

bool get(std::string& result, const std::string& key, const qpid::types::Variant::Map& map)
{
    qpid::types::Variant::Map::const_iterator i = map.find(key);
    if (i != map.end()) {
        result = i->second.asString();
        return true;
    } else {
        return false;
    }
}
bool get(int& result, const std::string& key, const qpid::types::Variant::Map& map)
{
    qpid::types::Variant::Map::const_iterator i = map.find(key);
    if (i != map.end()) {
        result = i->second;
        return true;
    } else {
        return false;
    }
}
bool get(qpid::Url& url, const std::string& key, const qpid::types::Variant::Map& map)
{
    qpid::types::Variant::Map::const_iterator i = map.find(key);
    if (i != map.end()) {
        url = qpid::Url(i->second.asString());
        return true;
    } else {
        return false;
    }
}
bool get(const std::string& key, const qpid::types::Variant::Map& map)
{
    qpid::types::Variant::Map::const_iterator i = map.find(key);
    if (i != map.end()) {
        return i->second.asBool();
    } else {
        return false;
    }
}
}

class InterconnectFactory : public BrokerContext, public qpid::sys::ConnectionCodec::Factory, public boost::enable_shared_from_this<InterconnectFactory>
{
  public:
    InterconnectFactory(bool incoming, const std::string& name, const qpid::types::Variant::Map& properties,
                        boost::shared_ptr<Domain>, BrokerContext&);
    InterconnectFactory(bool incoming, const std::string& name, const std::string& source, const std::string& target,
                        boost::shared_ptr<Domain>, BrokerContext&, boost::shared_ptr<Relay>);
    qpid::sys::ConnectionCodec* create(const framing::ProtocolVersion&, qpid::sys::OutputControl&, const std::string&, const qpid::sys::SecuritySettings&);
    qpid::sys::ConnectionCodec* create(qpid::sys::OutputControl&, const std::string&, const qpid::sys::SecuritySettings&);
    qpid::framing::ProtocolVersion supportedVersion() const
    {
        return qpid::framing::ProtocolVersion(1, 0);
    }
    bool connect();
    void failed(int, std::string);
  private:
    bool incoming;
    std::string name;
    std::string source;
    std::string target;
    qpid::Url url;
    qpid::Url::iterator next;
    std::string hostname;
    boost::shared_ptr<Domain> domain;
    qpid::Address address;
    boost::shared_ptr<Relay> relay;
};

InterconnectFactory::InterconnectFactory(bool i, const std::string& n, const qpid::types::Variant::Map& properties, boost::shared_ptr<Domain> d, BrokerContext& c)
    : BrokerContext(c), incoming(i), name(n), url(d->getUrl()), domain(d)
{
    get(source, SOURCE, properties);
    get(target, TARGET, properties);
    next = url.begin();
}

InterconnectFactory::InterconnectFactory(bool i, const std::string& n, const std::string& source_, const std::string& target_,
                                         boost::shared_ptr<Domain> d, BrokerContext& c, boost::shared_ptr<Relay> relay_)
    : BrokerContext(c), incoming(i), name(n), source(source_), target(target_), url(d->getUrl()), domain(d), relay(relay_)
{
    next = url.begin();
}

qpid::sys::ConnectionCodec* InterconnectFactory::create(const qpid::framing::ProtocolVersion&, qpid::sys::OutputControl&, const std::string&, const qpid::sys::SecuritySettings&)
{
    throw qpid::Exception("Not implemented!");
}
qpid::sys::ConnectionCodec* InterconnectFactory::create(qpid::sys::OutputControl& out, const std::string& id, const qpid::sys::SecuritySettings& t)
{
    bool useSasl = domain->getMechanisms() != NONE;
    boost::shared_ptr<Interconnect> connection(new Interconnect(out, id, *this, useSasl, incoming, name, source, target, domain->getName()));
    if (!relay) getInterconnects().add(name, connection);
    else connection->setRelay(relay);

    std::auto_ptr<qpid::sys::ConnectionCodec> codec;
    if (useSasl) {
        QPID_LOG(info, "Using AMQP 1.0 (with SASL layer) on connect");
        codec = std::auto_ptr<qpid::sys::ConnectionCodec>(new qpid::broker::amqp::SaslClient(out, id, connection, domain->sasl(hostname), hostname, domain->getMechanisms(), t));
    } else {
        QPID_LOG(info, "Using AMQP 1.0 (no SASL layer) on connect");
        codec = std::auto_ptr<qpid::sys::ConnectionCodec>(new Wrapper(connection));
    }
    domain->removePending(shared_from_this());//(TODO: add support for retry on connection failure)
    return codec.release();
}

bool InterconnectFactory::connect()
{
    if (next == url.end()) return false;
    address = *next;
    next++;
    hostname = address.host;
    QPID_LOG (info, "Inter-broker connection initiated (" << address << ")");
    std::stringstream identifier;
    identifier << name << "@" << domain->getName();
    getBroker().connect(identifier.str(), address.host, boost::lexical_cast<std::string>(address.port), address.protocol, this, boost::bind(&InterconnectFactory::failed, this, _1, _2));
    return true;
}

void InterconnectFactory::failed(int, std::string text)
{
    QPID_LOG (info, "Inter-broker connection failed (" << address << "): " << text);
    if (!connect()) {
        domain->removePending(shared_from_this());//give up (TODO: add support for periodic retry)
    }
}

Domain::Domain(const std::string& n, const qpid::types::Variant::Map& properties, Broker& b)
    : PersistableObject(n, "domain", properties), name(n), durable(get(DURABLE, properties)),
      broker(b), mechanisms("ANONYMOUS"), service(AMQP_SASL_SERVICENAME), minSsf(0), maxSsf(0),
      agent(b.getManagementAgent())
{
    if (!get(url, URL, properties)) {
        QPID_LOG(error, "No URL specified for domain " << name << "!");
        throw qpid::Exception("A url is required for a domain!");
    } else {
        QPID_LOG(notice, "Created domain " << name << " with url " << url << " from " << properties);
    }
    get(username, USERNAME, properties);
    get(password, PASSWORD, properties);
    get(mechanisms, SASL_MECHANISMS, properties);
    get(service, SASL_SERVICE, properties);
    get(minSsf, MIN_SSF, properties);
    get(maxSsf, MAX_SSF, properties);
    if (agent != 0) {
        domain = _qmf::Domain::shared_ptr(new _qmf::Domain(agent, this, name, durable));
        domain->set_url(url.str());
        domain->set_username(username);
        domain->set_password(password);
        domain->set_mechanisms(mechanisms);
        agent->addObject(domain);
    }
}

Domain::~Domain()
{
    if (domain != 0) domain->resourceDestroy();
}

boost::shared_ptr<qpid::management::ManagementObject> Domain::GetManagementObject() const
{
    return domain;
}

const std::string& Domain::getMechanisms() const
{
    return mechanisms;
}

qpid::Url Domain::getUrl() const
{
    return url;
}

bool Domain::isDurable() const
{
    return durable;
}

std::auto_ptr<qpid::Sasl> Domain::sasl(const std::string& hostname)
{
    return qpid::SaslFactory::getInstance().create(username, password, service, hostname, minSsf, maxSsf, false);
}

void Domain::connect(bool incoming, const std::string& name, const qpid::types::Variant::Map& properties, BrokerContext& context)
{
    boost::shared_ptr<InterconnectFactory> factory(new InterconnectFactory(incoming, name, properties, shared_from_this(), context));
    factory->connect();
    addPending(factory);
}

void Domain::connect(bool incoming, const std::string& name, const std::string& source, const std::string& target, BrokerContext& context, boost::shared_ptr<Relay> relay)
{
    boost::shared_ptr<InterconnectFactory> factory(new InterconnectFactory(incoming, name, source, target, shared_from_this(), context, relay));
    factory->connect();
    addPending(factory);
}

void Domain::addPending(boost::shared_ptr<InterconnectFactory> f)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    pending.insert(f);
}

void Domain::removePending(boost::shared_ptr<InterconnectFactory> f)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    pending.erase(f);
}


}}} // namespace qpid::broker::amqp
