/*
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
 */

#include "qmf/Agent.h"
#include "qmf/engine/Agent.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Condition.h"

using namespace std;
using namespace qmf;
using namespace qpid::messaging;
using qpid::sys::Duration;

namespace qmf {
    class AgentImpl {
    public:
        AgentImpl(const string& vendor, const string& product, const string& instance, const string& domain, bool internalStore,
                  AgentHandler* handler, Notifiable* notifiable);
        ~AgentImpl();
        void setAttribute(const string& name, const Variant& value) { agentEngine.setAttr(name.c_str(), value); }
        void setStoreDir(const string& path) { agentEngine.setStoreDir(path.c_str()); }
        void setConnection(Connection& conn) { agentEngine.setConnection(conn); }
        void registerClass(SchemaClass* cls) { agentEngine.registerClass(cls); }
        uint32_t invokeHandler(uint32_t limit, Duration timeout);
        //    const ObjectId* addObject(AgentObject& obj, bool persistent, uint64_t oid);
        //    const ObjectId* addObject(AgentObject& obj, bool persistent, uint32_t oidLo, uint32_t oidHi);
        void raiseEvent(Event& event);
        void queryResponse(uint32_t context, AgentObject& object);
        void queryComplete(uint32_t context);
        void methodResponse(uint32_t context, const Variant::Map& args, const Variant& exception);
    private:
        const string vendor;
        const string product;
        const string instance;
        const string domain;
        const bool internalStore;
        AgentHandler* handler;
        Notifiable* notifiable;
        engine::Agent agentEngine;
        qpid::sys::Mutex lock;
        qpid::sys::Condition cond;
    };
}

AgentImpl::AgentImpl(const string& _vendor, const string& _product, const string& _instance, const string& _domain,
                     bool _internalStore, AgentHandler* _handler, Notifiable* _notifiable) :
    vendor(_vendor), product(_product), instance(_instance.empty() ? "TODO" : _instance),
    domain(_domain.empty() ? "default" : _domain), internalStore(_internalStore),
    handler(_handler), notifiable(_notifiable),
    agentEngine(vendor.c_str(), product.c_str(), instance.c_str(), domain.c_str(), internalStore)
{
}

AgentImpl::~AgentImpl()
{
}

void AgentImpl::registerClass(SchemaClass* /*cls*/)
{
}

uint32_t AgentImpl::invokeHandler(uint32_t limit, Duration timeout)
{
    engine::AgentEvent event;
    bool valid;
    qpid::sys::AbsTime endTime(qpid::sys::now(), timeout);

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        valid = agentEngine.getEvent(event);
        while (!valid) {
            if (!cond.wait(lock, endTime))
                return 0;
            valid = agentEngine.getEvent(event);
        }
    }

    uint32_t count = 0;
    while (valid) {
        // TODO: Process event
        count++;
        if (limit > 0 && count == limit)
            break;
        agentEngine.popEvent();
        valid = agentEngine.getEvent(event);
    }

    return count;
}

//    const ObjectId* AgentImpl::addObject(AgentObject& obj, bool persistent, uint64_t oid);
//    const ObjectId* AgentImpl::addObject(AgentObject& obj, bool persistent, uint32_t oidLo, uint32_t oidHi);

void AgentImpl::raiseEvent(Event& /*event*/)
{
}

void AgentImpl::queryResponse(uint32_t /*context*/, AgentObject& /*object*/)
{
}

void AgentImpl::queryComplete(uint32_t /*context*/)
{
}

void AgentImpl::methodResponse(uint32_t /*context*/, const Variant::Map& /*args*/, const Variant& /*exception*/)
{
}


//==================================================================
// Wrappers
//==================================================================
Agent::Agent(const string& vendor, const string& product, const string& instance, const string& domain,
             bool internalStore, AgentHandler* handler, Notifiable* notifiable) {
    impl = new AgentImpl(vendor, product, instance, domain, internalStore, handler, notifiable); }
Agent::~Agent() { delete impl; }
void Agent::setAttribute(const string& name, const qpid::messaging::Variant& value) { impl->setAttribute(name, value); }
void Agent::setStoreDir(const string& path) { impl->setStoreDir(path); }
void Agent::setConnection(qpid::messaging::Connection& conn) { impl->setConnection(conn); }
void Agent::registerClass(SchemaClass* cls) { impl->registerClass(cls); }
uint32_t Agent::invokeHandler(uint32_t limit, qpid::sys::Duration timeout) { return impl->invokeHandler(limit, timeout); }
//const ObjectId* Agent::addObject(AgentObject& obj, bool persistent, uint64_t oid);
//const ObjectId* Agent::addObject(AgentObject& obj, bool persistent, uint32_t oidLo, uint32_t oidHi);
void Agent::raiseEvent(Event& event) { impl->raiseEvent(event); }
void Agent::queryResponse(uint32_t context, AgentObject& object) { impl->queryResponse(context, object); }
void Agent::queryComplete(uint32_t context) { impl->queryComplete(context); }
void Agent::methodResponse(uint32_t context, const Variant::Map& args, const Variant& exception) { impl->methodResponse(context, args, exception); }

