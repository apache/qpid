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
#include "Connection.h"

#include "qpid/management/ManagementAgent.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/Uuid.h"

using qpid::framing::FieldTable;
using qpid::framing::Uuid;

namespace qpid {
namespace broker {

Bridge::Bridge(framing::ChannelId id, Connection& c, CancellationListener l, const management::ArgsLinkBridge& _args) : 
    args(_args), channel(id, &(c.getOutput())), peer(channel), 
    mgmtObject(new management::Bridge(this, &c, id, args.i_src, args.i_dest, args.i_key, args.i_src_is_queue, args.i_src_is_local)),
    connection(c), listener(l)
{
    management::ManagementAgent::getAgent()->addObject(mgmtObject);
}

Bridge::~Bridge() 
{ 
    mgmtObject->resourceDestroy(); 
}

void Bridge::create()
{
    framing::AMQP_ServerProxy::Session session(channel);
    session.open(0);

    //peer.getSession().open(0);

    if (args.i_src_is_local) {
        //TODO: handle 'push' here... simplest way is to create frames and pass them to Connection::received()
    } else {
        if (args.i_src_is_queue) {
            peer.getMessage().subscribe(0, args.i_src, args.i_dest, false, 0, 0, false, FieldTable());
            peer.getMessage().flow(args.i_dest, 0, 0xFFFFFFFF);
            peer.getMessage().flow(args.i_dest, 1, 0xFFFFFFFF);
        } else {
            string queue = "bridge_queue_";
            queue += Uuid(true).str();
            peer.getQueue().declare(0, queue, "", false, false, true, true, FieldTable());
            peer.getQueue().bind(0, queue, args.i_dest, args.i_key, FieldTable());
            peer.getMessage().subscribe(0, queue, args.i_dest, false, 0, 0, false, FieldTable());
            peer.getMessage().flow(args.i_dest, 0, 0xFFFFFFFF);
            peer.getMessage().flow(args.i_dest, 1, 0xFFFFFFFF);
        }
    }

}

void Bridge::cancel()
{
    peer.getMessage().cancel(args.i_dest);    
    peer.getSession().close();
}

management::ManagementObject::shared_ptr Bridge::GetManagementObject (void) const
{
    return dynamic_pointer_cast<management::ManagementObject>(mgmtObject);
}

management::Manageable::status_t Bridge::ManagementMethod(uint32_t methodId, management::Args& /*args*/)
{
    if (methodId == management::Bridge::METHOD_CLOSE) {  
        //notify that we are closed
        listener(this);
        //request time on the connections io thread
        connection.getOutput().activateOutput();
        return management::Manageable::STATUS_OK;
    } else {
        return management::Manageable::STATUS_UNKNOWN_METHOD;
    }
}

}}
