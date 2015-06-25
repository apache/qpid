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
#include "Interconnects.h"
#include "Interconnect.h"
#include "Connection.h"
#include "Domain.h"
#include "SaslClient.h"
#include "qpid/broker/Broker.h"
#include "qpid/Exception.h"
#include "qpid/SaslFactory.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/log/Statement.h"
#include <boost/shared_ptr.hpp>
#include <assert.h>

namespace qpid {
namespace broker {
namespace amqp {

namespace {
const std::string INCOMING_TYPE("incoming");
const std::string OUTGOING_TYPE("outgoing");
const std::string DOMAIN_TYPE("domain");
}

bool Interconnects::createObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                                 const std::string& /*userId*/, const std::string& /*connectionId*/)
{
    if (type == DOMAIN_TYPE) {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
        DomainMap::iterator i = domains.find(name);
        if (i == domains.end()) {
            boost::shared_ptr<Domain> domain(new Domain(name, properties, broker));
            domains[name] = domain;
            if (domain->isDurable()) broker.getStore().create(*domain);
            return true;
        } else {
            throw qpid::Exception(QPID_MSG("A domain named " << name << " already exists"));
        }
    } else if (type == INCOMING_TYPE || type == OUTGOING_TYPE) {
        QPID_LOG(notice, "Creating interconnect " << name << ", " << properties);
        boost::shared_ptr<Domain> domain;
        {
            qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
            qpid::types::Variant::Map::const_iterator p = properties.find(DOMAIN_TYPE);
            if (p != properties.end()) {
                std::string domainName = p->second;
                DomainMap::iterator i = domains.find(domainName);
                if (i != domains.end()) {
                    domain = i->second;
                } else {
                    throw qpid::Exception(QPID_MSG("No such domain: " << domainName));
                }
            } else {
                throw qpid::Exception(QPID_MSG("Domain must be specified"));
            }
        }
        domain->connect(type == INCOMING_TYPE, name, properties, *context);
        return true;
    } else {
        return false;
    }
}
bool Interconnects::deleteObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& /*properties*/,
                                 const std::string& /*userId*/, const std::string& /*connectionId*/)
{
    if (type == DOMAIN_TYPE) {
        boost::shared_ptr<Domain> domain;
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
        DomainMap::iterator i = domains.find(name);
        if (i != domains.end()) {
            domain = i->second;
            domains.erase(i);
            if (domain->isDurable()) broker.getStore().destroy(*domain);
            return true;
        } else {
            throw qpid::Exception(QPID_MSG("No such domain: " << name));
        }
    } else if (type == INCOMING_TYPE || type == OUTGOING_TYPE) {
        boost::shared_ptr<Interconnect> interconnect;
        {
            qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
            InterconnectMap::iterator i = interconnects.find(name);
            if (i != interconnects.end()) {
                interconnect = i->second;
                interconnects.erase(i);
            } else {
                throw qpid::Exception(QPID_MSG("No such interconnection: " << name));
            }
        }
        if (interconnect) interconnect->deletedFromRegistry();
        return true;
    } else {
        return false;
    }
}

bool Interconnects::recoverObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                   uint64_t persistenceId)
{
    if (type == DOMAIN_TYPE) {
        boost::shared_ptr<Domain> domain(new Domain(name, properties, broker));
        domain->setPersistenceId(persistenceId);
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
        domains[name] = domain;
        return true;
    } else {
        return false;
    }
}


bool Interconnects::add(const std::string& name, boost::shared_ptr<Interconnect> connection)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    InterconnectMap::iterator i = interconnects.find(name);
    if (i == interconnects.end()) {
        interconnects[name] = connection;
        return true;
    } else return false;
}
boost::shared_ptr<Interconnect> Interconnects::get(const std::string& name)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    InterconnectMap::const_iterator i = interconnects.find(name);
    if (i != interconnects.end()) return i->second;
    else return boost::shared_ptr<Interconnect>();
}
bool Interconnects::remove(const std::string& name)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    InterconnectMap::iterator i = interconnects.find(name);
    if (i != interconnects.end()) {
        interconnects.erase(i);
        return true;
    } else return false;
}

boost::shared_ptr<Domain> Interconnects::findDomain(const std::string& name)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    DomainMap::iterator i = domains.find(name);
    if (i == domains.end()) {
        return boost::shared_ptr<Domain>();
    } else {
        return i->second;
    }
}
void Interconnects::setContext(BrokerContext& c)
{
    context = &c;
    assert(&(context->getInterconnects()) == this);
}

Interconnects::Interconnects() : context(0) {}

}}} // namespace qpid::broker::amqp
