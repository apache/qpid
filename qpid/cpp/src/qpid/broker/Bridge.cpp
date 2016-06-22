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
#include "qpid/broker/Bridge.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/FedOps.h"
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/LinkRegistry.h"
#include "qpid/broker/SessionState.h"

#include "qpid/management/ManagementAgent.h"
#include "qpid/types/Variant.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/framing/Uuid.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include <iostream>

using qpid::framing::FieldTable;
using qpid::framing::Uuid;
using qpid::framing::Buffer;
using qpid::framing::AMQFrame;
using qpid::framing::AMQContentBody;
using qpid::framing::AMQHeaderBody;
using qpid::framing::MessageProperties;
using qpid::framing::MessageTransferBody;
using qpid::types::Variant;
using qpid::management::ManagementAgent;
using std::string;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace {
const std::string QPID_REPLICATE("qpid.replicate");
const std::string NONE("none");
const uint8_t EXPLICIT_ACK(0); // msg.accept required to be sent
const uint8_t IMPLIED_ACK(1);  // msg.accept assumed, not sent
}

namespace qpid {
namespace broker {

void Bridge::PushHandler::handle(framing::AMQFrame& frame)
{
    conn->received(frame);
}

Bridge::Bridge(const std::string& _name, Link* _link, framing::ChannelId _id,
               CancellationListener l, const _qmf::ArgsLinkBridge& _args,
               InitializeCallback init, const std::string& _queueName, const string& ae) :
    link(_link), channel(_id), args(_args),
    listener(l), name(_name),
    queueName(_queueName.empty() ? "qpid.bridge_queue_" + name + "_" + link->getBroker()->getFederationTag()
              : _queueName),
    altEx(ae), persistenceId(0),
    conn(0), initialize(init), detached(false),
    useExistingQueue(!_queueName.empty()),
    sessionName("qpid.bridge_session_" + name + "_" + link->getBroker()->getFederationTag())
{
    // If both acks (i_sync) and limited credit is configured, then we'd
    // better be able to sync before running out of credit or we
    // may stall (note: i_credit==0 means "unlimited")
    if (args.i_credit && args.i_sync && args.i_sync > args.i_credit)
        throw Exception("The credit value must be greater than configured sync (ack) interval.");

    ManagementAgent* agent = link->getBroker()->getManagementAgent();
    if (agent != 0) {
        mgmtObject = _qmf::Bridge::shared_ptr(new _qmf::Bridge
            (agent, this, link, name, args.i_durable, args.i_src, args.i_dest,
             args.i_key, args.i_srcIsQueue, args.i_srcIsLocal,
             args.i_tag, args.i_excludes, args.i_dynamic, args.i_sync,
             args.i_credit));
        mgmtObject->set_channelId(channel);
        agent->addObject(mgmtObject);
    }
    QPID_LOG(debug, "Bridge " << name << " created from " << args.i_src << " to " << args.i_dest);
}

Bridge::~Bridge()
{
    mgmtObject->resourceDestroy();
}

void Bridge::create(amqp_0_10::Connection& c)
{
    detached = false;           // Reset detached in case we are recovering.
    conn = &c;

    SessionHandler& sessionHandler = c.getChannel(channel);
    sessionHandler.setErrorListener(shared_from_this());
    if (args.i_srcIsLocal) {
        if (args.i_dynamic)
            throw Exception("Dynamic routing not supported for push routes");
        // Point the bridging commands at the local connection handler
        pushHandler.reset(new PushHandler(&c));
        channelHandler.reset(new framing::ChannelHandler(channel, pushHandler.get()));

        session.reset(new framing::AMQP_ServerProxy::Session(*channelHandler));
        peer.reset(new framing::AMQP_ServerProxy(*channelHandler));

        session->attach(sessionName, false);
        session->commandPoint(0,0);
    } else {
        sessionHandler.attachAs(sessionName);
        // Point the bridging commands at the remote peer broker
        peer.reset(new framing::AMQP_ServerProxy(sessionHandler.out));
    }

    if (args.i_srcIsLocal) sessionHandler.getSession()->disableReceiverTracking();

    if (initialize) {
        initialize(*this, sessionHandler);  // custom subscription initializer supplied
    } else {
        // will a temp queue be created for this bridge?
        const bool temp_queue = !args.i_srcIsQueue && !useExistingQueue;
        // UI convention: user specifies 0 for infinite credit
        const uint32_t credit = (args.i_credit == 0) ? LinkRegistry::INFINITE_CREDIT : args.i_credit;
        // use explicit acks only for non-temp queues, useless for temp queues since they are
        // destroyed when the session drops (can't resend unacked msgs)
        const uint8_t ack_mode = (args.i_sync && !temp_queue) ? EXPLICIT_ACK : IMPLIED_ACK;

        // configure command.sync frequency
        FieldTable options;
        uint32_t freq = 0;
        if (ack_mode == EXPLICIT_ACK) {  // user explicitly configured syncs
            freq = uint32_t(args.i_sync);
        } else if (credit && credit != LinkRegistry::INFINITE_CREDIT) {
            // force occasional sync to keep from stalling due to lack of credit
            freq = (credit + 1)/2;
        }
        if (freq)
            options.setInt("qpid.sync_frequency", freq);

        // create a subscription on the remote
        if (args.i_srcIsQueue) {
            peer->getMessage().subscribe(args.i_src, args.i_dest, ack_mode, 0, false, "", 0, options);
            peer->getMessage().flow(args.i_dest, 0, credit);  // message credit
            peer->getMessage().flow(args.i_dest, 1, LinkRegistry::INFINITE_CREDIT);     // byte credit
            QPID_LOG(debug, "Activated bridge " << name << " for route from queue " << args.i_src << " to " << args.i_dest);
        } else {
            if (!useExistingQueue) {
                FieldTable queueSettings;

                if (args.i_tag.size()) {
                    queueSettings.setString("qpid.trace.id", args.i_tag);
                } else {
                    const string& peerTag = c.getFederationPeerTag();
                    if (peerTag.size())
                        queueSettings.setString("qpid.trace.id", peerTag);
                }

                if (args.i_excludes.size()) {
                    queueSettings.setString("qpid.trace.exclude", args.i_excludes);
                } else {
                    const string& localTag = link->getBroker()->getFederationTag();
                    if (localTag.size())
                        queueSettings.setString("qpid.trace.exclude", localTag);
                }

                bool durable = false;//should this be an arg, or would we use srcIsQueue for durable queues?
                bool exclusive = true;  // only exclusive if the queue is owned by the bridge
                bool autoDelete = exclusive && !durable;//auto delete transient queues?
                peer->getQueue().declare(queueName, altEx, false, durable, exclusive, autoDelete, queueSettings);
            }
            if (!args.i_dynamic)
                peer->getExchange().bind(queueName, args.i_src, args.i_key, FieldTable());
            peer->getMessage().subscribe(queueName, args.i_dest, ack_mode, 0, false, "", 0, options);
            peer->getMessage().flow(args.i_dest, 0, credit);
            peer->getMessage().flow(args.i_dest, 1, LinkRegistry::INFINITE_CREDIT);
            if (args.i_dynamic) {
                Exchange::shared_ptr exchange = link->getBroker()->getExchanges().get(args.i_src);
                if (exchange.get() == 0)
                    throw Exception("Exchange not found for dynamic route");
                exchange->registerDynamicBridge(this);
                QPID_LOG(debug, "Activated bridge " << name << " for dynamic route for exchange " << args.i_src);
            } else {
                QPID_LOG(debug, "Activated bridge " << name << " for static route from exchange " << args.i_src << " to " << args.i_dest);
            }
        }
    }
    if (args.i_srcIsLocal) sessionHandler.getSession()->enableReceiverTracking();
}

void Bridge::cancel(amqp_0_10::Connection& c)
{
    // If &c != conn then we have failed over so the old connection is closed.
    if (&c == conn && resetProxy()) {
        peer->getMessage().cancel(args.i_dest);
        peer->getSession().detach(sessionName);
    }
    QPID_LOG(debug, "Cancelled bridge " << name);
}

/** Notify the bridge that the connection has closed */
void Bridge::closed()
{
    if (args.i_dynamic) {
        Exchange::shared_ptr exchange = link->getBroker()->getExchanges().find(args.i_src);
        if (exchange.get()) exchange->removeDynamicBridge(this);
    }
    QPID_LOG(debug, "Closed bridge " << name);
}

/** Shut down the bridge */
void Bridge::close()
{
    listener(this); // ask the LinkRegistry to destroy us
}

void Bridge::setPersistenceId(uint64_t pId) const
{
    persistenceId = pId;
}


const std::string Bridge::ENCODED_IDENTIFIER("bridge.v2");
const std::string Bridge::ENCODED_IDENTIFIER_V1("bridge");

bool Bridge::isEncodedBridge(const std::string& key)
{
    return key == ENCODED_IDENTIFIER || key == ENCODED_IDENTIFIER_V1;
}


Bridge::shared_ptr Bridge::decode(LinkRegistry& links, Buffer& buffer)
{
    string kind;
    buffer.getShortString(kind);

    string   host;
    uint16_t port;
    string   src;
    string   dest;
    string   key;
    string   id;
    string   excludes;
    string   name;

    Link::shared_ptr link;
    if (kind == ENCODED_IDENTIFIER_V1) {
        /** previous versions identified the bridge by host:port, not by name, and
         * transport wasn't provided.  Try to find a link using those paramters.
         */
        buffer.getShortString(host);
        port = buffer.getShort();

        link = links.getLink(host, port);
        if (!link) {
            QPID_LOG(error, "Bridge::decode() failed: cannot find Link for host=" << host << ", port=" << port);
            return Bridge::shared_ptr();
        }
    } else {
        string linkName;

        buffer.getShortString(name);
        buffer.getShortString(linkName);
        link = links.getLink(linkName);
        if (!link) {
            QPID_LOG(error, "Bridge::decode() failed: cannot find Link named='" << linkName << "'");
            return Bridge::shared_ptr();
        }
    }

    bool durable(buffer.getOctet());
    buffer.getShortString(src);
    buffer.getShortString(dest);
    buffer.getShortString(key);
    bool is_queue(buffer.getOctet());
    bool is_local(buffer.getOctet());
    buffer.getShortString(id);
    buffer.getShortString(excludes);
    bool dynamic(buffer.getOctet());
    uint16_t sync = buffer.getShort();
    uint32_t credit = buffer.getLong();

    if (kind == ENCODED_IDENTIFIER_V1) {
        /** previous versions did not provide a name for the bridge, so create one
         */
        name = createName(link->getName(), src, dest, key);
    }

    return links.declare(name, *link, durable, src, dest, key, is_queue,
                         is_local, id, excludes, dynamic, sync, credit).first;
}

void Bridge::encode(Buffer& buffer) const
{
    buffer.putShortString(ENCODED_IDENTIFIER);
    buffer.putShortString(name);
    buffer.putShortString(link->getName());
    buffer.putOctet(args.i_durable ? 1 : 0);
    buffer.putShortString(args.i_src);
    buffer.putShortString(args.i_dest);
    buffer.putShortString(args.i_key);
    buffer.putOctet(args.i_srcIsQueue ? 1 : 0);
    buffer.putOctet(args.i_srcIsLocal ? 1 : 0);
    buffer.putShortString(args.i_tag);
    buffer.putShortString(args.i_excludes);
    buffer.putOctet(args.i_dynamic ? 1 : 0);
    buffer.putShort(args.i_sync);
    buffer.putLong(args.i_credit);
}

uint32_t Bridge::encodedSize() const
{
    return ENCODED_IDENTIFIER.size() + 1  // +1 byte length
        + name.size() + 1
        + link->getName().size() + 1
        + 1                // durable
        + args.i_src.size()  + 1
        + args.i_dest.size() + 1
        + args.i_key.size()  + 1
        + 1                // srcIsQueue
        + 1                // srcIsLocal
        + args.i_tag.size() + 1
        + args.i_excludes.size() + 1
        + 1               // dynamic
        + 2               // sync
        + 4;              // credit
}

management::ManagementObject::shared_ptr Bridge::GetManagementObject(void) const
{
    return mgmtObject;
}

management::Manageable::status_t Bridge::ManagementMethod(uint32_t methodId,
                                                          management::Args& /*args*/,
                                                          string&)
{
    if (methodId == _qmf::Bridge::METHOD_CLOSE) {
        //notify that we are closed
        QPID_LOG(debug, "Bridge::close() method called on bridge '" << name << "'");
        close();
        return management::Manageable::STATUS_OK;
    } else {
        return management::Manageable::STATUS_UNKNOWN_METHOD;
    }
}

void Bridge::propagateBinding(const string& key, const string& tagList,
                              const string& op,  const string& origin,
                              qpid::framing::FieldTable* extra_args)
{
    const string& localTag = link->getBroker()->getFederationTag();
    const string& peerTag  = conn->getFederationPeerTag();

    if (tagList.find(peerTag) == tagList.npos) {
         FieldTable bindArgs;
         if (extra_args) {
             for (qpid::framing::FieldTable::ValueMap::iterator i=extra_args->begin(); i != extra_args->end(); ++i) {
                 bindArgs.insert((*i));
             }
         }
         string newTagList(tagList + string(tagList.empty() ? "" : ",") + localTag);

         bindArgs.setString(QPID_REPLICATE, NONE);
         bindArgs.setString(qpidFedOp, op);
         bindArgs.setString(qpidFedTags, newTagList);
         if (origin.empty())
             bindArgs.setString(qpidFedOrigin, localTag);
         else
             bindArgs.setString(qpidFedOrigin, origin);

         conn->requestIOProcessing(
             weakCallback<Bridge>(
                 boost::bind(&Bridge::ioThreadPropagateBinding, _1,
                             queueName, args.i_src, key, bindArgs),
                 this));
    }
}

void Bridge::sendReorigin()
{
    FieldTable bindArgs;

    bindArgs.setString(qpidFedOp, fedOpReorigin);
    bindArgs.setString(qpidFedTags, link->getBroker()->getFederationTag());

    conn->requestIOProcessing(
        weakCallback<Bridge>(
            boost::bind(&Bridge::ioThreadPropagateBinding, _1,
                        queueName, args.i_src, args.i_key, bindArgs),
            this));
}

bool Bridge::resetProxy()
{
    SessionHandler& sessionHandler = conn->getChannel(channel);
    if (!sessionHandler.getSession()) peer.reset();
    else peer.reset(new framing::AMQP_ServerProxy(sessionHandler.out));
    return peer.get();
}

void Bridge::ioThreadPropagateBinding(const string& queue, const string& exchange, const string& key, FieldTable args)
{
    if (resetProxy()) {
        peer->getExchange().bind(queue, exchange, key, args);
    } else {
      // link's periodic maintenance visit will attempt to recover
    }
}

bool Bridge::containsLocalTag(const string& tagList) const
{
    const string& localTag = link->getBroker()->getFederationTag();
    return (tagList.find(localTag) != tagList.npos);
}

const string& Bridge::getLocalTag() const
{
    return link->getBroker()->getFederationTag();
}

// SessionHandler::ErrorListener methods.
void Bridge::connectionException(
    framing::connection::CloseCode code, const std::string& msg)
{
    if (errorListener) errorListener->connectionException(code, msg);
}

void Bridge::channelException(
    framing::session::DetachCode code, const std::string& msg)
{
    if (errorListener) errorListener->channelException(code, msg);
}

void Bridge::executionException(
    framing::execution::ErrorCode code, const std::string& msg)
{
    if (errorListener) errorListener->executionException(code, msg);
}

void Bridge::incomingExecutionException(
    framing::execution::ErrorCode code, const std::string& msg)
{
    if (errorListener) errorListener->incomingExecutionException(code, msg);
}

void Bridge::detach() {
    detached = true;
    if (errorListener) errorListener->detach();
}

std::string Bridge::createName(const std::string& linkName,
                               const std::string& src,
                               const std::string& dest,
                               const std::string& key)
{
    std::stringstream keystream;
    keystream << linkName << "!" << src << "!" << dest << "!" << key;
    return keystream.str();
}

}}
