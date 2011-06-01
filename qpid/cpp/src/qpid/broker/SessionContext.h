#ifndef QPID_BROKER_SESSIONCONTEXT_H
#define QPID_BROKER_SESSIONCONTEXT_H

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

#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/Invoker.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/broker/OwnershipToken.h"
#include "qpid/SessionId.h"

#include <boost/noncopyable.hpp>

namespace qpid {
namespace broker {

class SessionContext : public OwnershipToken, public sys::OutputControl
{
 protected:
    class AsyncCommandManager;

 public:
    class AsyncCommandContext;

    virtual ~SessionContext(){}
    virtual bool isLocal(const ConnectionToken* t) const = 0;
    virtual bool isAttached() const = 0;
    virtual ConnectionState& getConnection() = 0;
    virtual framing::AMQP_ClientProxy& getProxy() = 0;
    virtual Broker& getBroker() = 0;
    virtual uint16_t getChannel() const = 0;
    virtual const SessionId& getSessionId() const = 0;
    virtual void addPendingExecutionSync() = 0;
    // pass async command context to Session, completion must not occur
    // until -after- this call returns.
    virtual void registerAsyncCommand(boost::intrusive_ptr<AsyncCommandContext>&) = 0;

    // class for commands that need to complete asynchronously
    friend class AsyncCommandContext;
    class AsyncCommandContext : virtual public RefCounted
    {
     private:
        framing::SequenceNumber id;
        bool requiresAccept;
        bool syncBitSet;
        boost::intrusive_ptr<SessionContext::AsyncCommandManager> manager;

     public:
        AsyncCommandContext() : id(0), requiresAccept(false), syncBitSet(false) {}
        virtual ~AsyncCommandContext() {}

        framing::SequenceNumber getId() { return id; }
        void setId(const framing::SequenceNumber seq) { id = seq; }
        bool getRequiresAccept() { return requiresAccept; }
        void setRequiresAccept(const bool a) { requiresAccept = a; }
        bool getSyncBitSet() { return syncBitSet; }
        void setSyncBitSet(const bool s) { syncBitSet = s; }
        void setManager(SessionContext::AsyncCommandManager *m) { manager.reset(m); }

        /** notify session that this command has completed */
        void completed(const framing::Invoker::Result& r)
        {
            boost::intrusive_ptr<AsyncCommandContext> context(this);
            manager->completePendingCommand( context, r );
            manager.reset(0);
        }

        // to force completion as fast as possible (like when Sync arrives)
        virtual void flush() = 0;
    };

 protected:
    class AsyncCommandManager : public RefCounted
    {
     public:
        virtual void completePendingCommand(boost::intrusive_ptr<AsyncCommandContext>&,
                                            const framing::Invoker::Result&) = 0;
    };
 };

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSIONCONTEXT_H*/
