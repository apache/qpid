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
#include "Bridge.h"
#include "ConnectionState.h"
#include "LinkRegistry.h"

#include "qpid/management/ManagementAgent.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"

using qpid::framing::FieldTable;
using qpid::framing::Uuid;
using qpid::framing::Buffer;
using qpid::management::ManagementAgent;

namespace qpid {
namespace broker {

Bridge::Bridge(Link* _link, framing::ChannelId _id, CancellationListener l,
               const management::ArgsLinkBridge& _args) : 
    link(_link), id(_id), args(_args),
    mgmtObject(new management::Bridge(this, link, id, args.i_durable, args.i_src, args.i_dest,
                                      args.i_key, args.i_src_is_queue, args.i_src_is_local,
                                      args.i_tag, args.i_excludes)),
    listener(l), name(Uuid(true).str()), persistenceId(0)
{
    if (!args.i_durable)
        management::ManagementAgent::getAgent()->addObject(mgmtObject);
}

Bridge::~Bridge() 
{
    mgmtObject->resourceDestroy(); 
}

void Bridge::create(ConnectionState& c)
{
    channelHandler.reset(new framing::ChannelHandler(id, &(c.getOutput())));
    session.reset(new framing::AMQP_ServerProxy::Session(*channelHandler));
    peer.reset(new framing::AMQP_ServerProxy(*channelHandler));

    session->attach(name, false);

    if (args.i_src_is_local) {
        //TODO: handle 'push' here... simplest way is to create frames and pass them to Connection::received()
    } else {
        if (args.i_src_is_queue) {
            peer->getMessage().subscribe(args.i_src, args.i_dest, 1, 0, false, "", 0, FieldTable());
            peer->getMessage().flow(args.i_dest, 0, 0xFFFFFFFF);
            peer->getMessage().flow(args.i_dest, 1, 0xFFFFFFFF);
        } else {
            string queue = "bridge_queue_";
            queue += Uuid(true).str();
            FieldTable queueSettings;
            if (args.i_tag.size()) {
                queueSettings.setString("qpid.trace.id", args.i_tag);
            }
            if (args.i_excludes.size()) {
                queueSettings.setString("qpid.trace.exclude", args.i_excludes);
            }

            bool durable = false;//should this be an arg, or would be use src_is_queue for durable queues?
            bool autoDelete = !durable;//auto delete transient queues?
            peer->getQueue().declare(queue, "", false, durable, true, autoDelete, queueSettings);
            peer->getExchange().bind(queue, args.i_src, args.i_key, FieldTable());
            peer->getMessage().subscribe(queue, args.i_dest, 1, 0, false, "", 0, FieldTable());
            peer->getMessage().flow(args.i_dest, 0, 0xFFFFFFFF);
            peer->getMessage().flow(args.i_dest, 1, 0xFFFFFFFF);
        }
    }
}

void Bridge::cancel()
{
    peer->getMessage().cancel(args.i_dest);
    peer->getSession().detach(name);
}

void Bridge::destroy()
{
    listener(this);
}

void Bridge::setPersistenceId(uint64_t id) const
{
    if (mgmtObject != 0 && persistenceId == 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();
        agent->addObject (mgmtObject, id);
    }
    persistenceId = id;
}

const string& Bridge::getName() const
{
    return name;
}

Bridge::shared_ptr Bridge::decode(LinkRegistry& links, Buffer& buffer)
{
    string   host;
    uint16_t port;
    string   src;
    string   dest;
    string   key;
    string   id;
    string   excludes;

    buffer.getShortString(host);
    port = buffer.getShort();
    bool durable(buffer.getOctet());
    buffer.getShortString(src);
    buffer.getShortString(dest);
    buffer.getShortString(key);
    bool is_queue(buffer.getOctet());
    bool is_local(buffer.getOctet());
    buffer.getShortString(id);
    buffer.getShortString(excludes);

    return links.declare(host, port, durable, src, dest, key,
                         is_queue, is_local, id, excludes).first;
}

void Bridge::encode(Buffer& buffer) const 
{
    buffer.putShortString(string("bridge"));
    buffer.putShortString(link->getHost());
    buffer.putShort(link->getPort());
    buffer.putOctet(args.i_durable ? 1 : 0);
    buffer.putShortString(args.i_src);
    buffer.putShortString(args.i_dest);
    buffer.putShortString(args.i_key);
    buffer.putOctet(args.i_src_is_queue ? 1 : 0);
    buffer.putOctet(args.i_src_is_local ? 1 : 0);
    buffer.putShortString(args.i_tag);
    buffer.putShortString(args.i_excludes);
}

uint32_t Bridge::encodedSize() const 
{ 
    return link->getHost().size() + 1 // short-string (host)
        + 7                // short-string ("bridge")
        + 2                // port
        + 1                // durable
        + args.i_src.size()  + 1
        + args.i_dest.size() + 1
        + args.i_key.size()  + 1
        + 1                // src_is_queue
        + 1                // src_is_local
        + args.i_tag.size() + 1
        + args.i_excludes.size() + 1;
}

management::ManagementObject::shared_ptr Bridge::GetManagementObject (void) const
{
    return dynamic_pointer_cast<management::ManagementObject>(mgmtObject);
}

management::Manageable::status_t Bridge::ManagementMethod(uint32_t methodId, management::Args& /*args*/)
{
    if (methodId == management::Bridge::METHOD_CLOSE) {  
        //notify that we are closed
        destroy();
        return management::Manageable::STATUS_OK;
    } else {
        return management::Manageable::STATUS_UNKNOWN_METHOD;
    }
}

}}
