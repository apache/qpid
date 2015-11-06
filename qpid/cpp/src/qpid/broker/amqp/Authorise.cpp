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
#include "Authorise.h"
#include "Exception.h"
#include "Filter.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/broker/AclModule.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Queue.h"
#include "qpid/types/Variant.h"
#include <map>
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace broker {
namespace amqp {
namespace {
const std::string B_TRUE("true");
const std::string B_FALSE("false");
const std::string POLICY_TYPE("qpid.policy_type");
}

Authorise::Authorise(const std::string& u, AclModule* a) : user(u), acl(a) {}
void Authorise::access(const std::string& name)
{
    if (acl) {
        std::map<acl::Property, std::string> params;
        if (!acl->authorise(user, acl::ACT_ACCESS, acl::OBJ_EXCHANGE, name, &params))
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG("ACL denied exchange access request from " << user));
    }
}
void Authorise::access(boost::shared_ptr<Exchange> exchange)
{
    if (acl) {
        std::map<acl::Property, std::string> params;
        params.insert(std::make_pair(acl::PROP_TYPE, exchange->getType()));
        params.insert(std::make_pair(acl::PROP_DURABLE, exchange->isDurable() ? B_TRUE : B_FALSE));
        if (!acl->authorise(user, acl::ACT_ACCESS, acl::OBJ_EXCHANGE, exchange->getName(), &params))
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG("ACL denied exchange access request from " << user));
    }
}
void Authorise::access(boost::shared_ptr<Queue> queue)
{
    if (acl) {
        const QueueSettings& settings = queue->getSettings();
        std::map<acl::Property, std::string> params;
        boost::shared_ptr<Exchange> altex = queue->getAlternateExchange();
        if (altex)
            params.insert(std::make_pair(acl::PROP_ALTERNATE, altex->getName()));
        params.insert(std::make_pair(acl::PROP_DURABLE, settings.durable ? B_TRUE : B_FALSE));
        params.insert(std::make_pair(acl::PROP_EXCLUSIVE, queue->hasExclusiveOwner() ? B_TRUE : B_FALSE));
        params.insert(std::make_pair(acl::PROP_AUTODELETE, settings.autodelete ? B_TRUE : B_FALSE));
        qpid::types::Variant::Map::const_iterator i = settings.original.find(POLICY_TYPE);
        if (i != settings.original.end())
            params.insert(std::make_pair(acl::PROP_POLICYTYPE, i->second.asString()));
        if (settings.maxDepth.hasCount())
            params.insert(std::make_pair(acl::PROP_MAXQUEUECOUNT, boost::lexical_cast<std::string>(settings.maxDepth.getCount())));
        if (settings.maxDepth.hasCount())
            params.insert(std::make_pair(acl::PROP_MAXQUEUESIZE, boost::lexical_cast<std::string>(settings.maxDepth.getSize())));
        if (!acl->authorise(user, acl::ACT_ACCESS, acl::OBJ_QUEUE, queue->getName(), &params) )
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG("ACL denied queue access request from " << user));
    }
}

void Authorise::incoming(boost::shared_ptr<Exchange> exchange)
{
    access(exchange);
    //can't check publish permission here as do not yet know routing key
}
void Authorise::incoming(boost::shared_ptr<Queue> queue)
{
    access(queue);
    if (acl) {
        if (!acl->authorise(user, acl::ACT_PUBLISH, acl::OBJ_EXCHANGE, std::string()/*default exchange*/, queue->getName()))
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG(user << " cannot publish to queue " << queue->getName()));
    }
}
void Authorise::outgoing(boost::shared_ptr<Exchange> exchange, boost::shared_ptr<Queue> queue, const Filter& filter)
{
    access(exchange);
    if (acl) {
        std::map<qpid::acl::Property, std::string> params;
        params.insert(std::make_pair(acl::PROP_QUEUENAME, queue->getName()));
        params.insert(std::make_pair(acl::PROP_ROUTINGKEY, filter.getBindingKey(exchange)));

        if (!acl->authorise(user, acl::ACT_BIND, acl::OBJ_EXCHANGE, exchange->getName(), &params))
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG("ACL denied exchange bind request from " << user));

        if (!acl->authorise(user, acl::ACT_CONSUME, acl::OBJ_QUEUE, queue->getName(), NULL))
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG("ACL denied queue subscribe request from " << user));
    }
}

void Authorise::outgoing(boost::shared_ptr<Queue> queue)
{
    access(queue);
    if (acl) {
        if (!acl->authorise(user, acl::ACT_CONSUME, acl::OBJ_QUEUE, queue->getName(), NULL))
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG("ACL denied queue subscribe request from " << user));
    }
}

void Authorise::route(boost::shared_ptr<Exchange> exchange, const Message& msg)
{
    if (acl && acl->doTransferAcl()) {
        if (!acl->authorise(user, acl::ACT_PUBLISH, acl::OBJ_EXCHANGE, exchange->getName(), msg.getRoutingKey()))
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG(user << " cannot publish to " << exchange->getName() << " with routing-key " << msg.getRoutingKey()));
    }
}

void Authorise::interlink()
{
    if (acl && acl->userAclRules()) {
        if (!acl->authorise(user, acl::ACT_CREATE, acl::OBJ_LINK, "")){
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG("ACL denied " << user << "  a AMQP 1.0 link"));
        }
    }
}

void Authorise::access(const std::string& node, bool queueRequested, bool exchangeRequested)
{
    if (acl) {
        std::map<acl::Property, std::string> params;
        bool checkExchange = true;
        bool checkQueue = true;
        if (exchangeRequested) checkQueue = false;
        else if (queueRequested) checkExchange = false;

        bool allowExchange = !checkExchange || acl->authorise(user, acl::ACT_ACCESS, acl::OBJ_EXCHANGE, node, &params);
        bool allowQueue = !checkQueue || acl->authorise(user, acl::ACT_ACCESS, acl::OBJ_QUEUE, node, &params);

        if (!allowQueue || !allowExchange) {
            throw Exception(qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS, QPID_MSG("ACL denied access request to " << node << " from " << user));
        }
    }
}

}}} // namespace qpid::broker::amqp
