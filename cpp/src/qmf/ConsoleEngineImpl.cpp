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

#include "qmf/ConsoleEngineImpl.h"
#include "qmf/MessageImpl.h"
#include "qmf/SchemaImpl.h"
#include "qmf/Typecode.h"
#include "qmf/ObjectImpl.h"
#include "qmf/ObjectIdImpl.h"
#include "qmf/QueryImpl.h"
#include "qmf/ValueImpl.h"
#include "qmf/Protocol.h"
#include "qmf/SequenceManager.h"
#include "qmf/BrokerProxyImpl.h"
#include <qpid/framing/Buffer.h>
#include <qpid/framing/Uuid.h>
#include <qpid/framing/FieldTable.h>
#include <qpid/framing/FieldValue.h>
#include <qpid/log/Statement.h>
#include <qpid/sys/Time.h>
#include <qpid/sys/SystemInfo.h>
#include <string.h>
#include <iostream>
#include <fstream>

using namespace std;
using namespace qmf;
using namespace qpid::framing;
using namespace qpid::sys;

namespace {
    const char* QMF_EXCHANGE = "qpid.management";
}

#define STRING_REF(s) {if (!s.empty()) item.s = const_cast<char*>(s.c_str());}

ConsoleEvent ConsoleEventImpl::copy()
{
    ConsoleEvent item;

    ::memset(&item, 0, sizeof(ConsoleEvent));
    item.kind      = kind;
    item.agent     = agent.get() ? agent->envelope : 0;
    item.classKey  = classKey.get();
    item.object    = object;
    item.context   = context;
    item.event     = event;
    item.timestamp = timestamp;

    STRING_REF(name);

    return item;
}

ConsoleEngineImpl::ConsoleEngineImpl(ConsoleEngine* e, const ConsoleSettings& s) :
    envelope(e), settings(s)
{
    bindingList.push_back(pair<string, string>(string(), "schema.#"));
    if (settings.rcvObjects && settings.rcvEvents && settings.rcvHeartbeats && !settings.userBindings) {
        bindingList.push_back(pair<string, string>(string(), "console.#"));
    } else {
        if (settings.rcvObjects && !settings.userBindings)
            bindingList.push_back(pair<string, string>(string(), "console.obj.#"));
        else
            bindingList.push_back(pair<string, string>(string(), "console.obj.*.*.org.apache.qpid.broker.agent"));
        if (settings.rcvEvents)
            bindingList.push_back(pair<string, string>(string(), "console.event.#"));
        if (settings.rcvHeartbeats)
            bindingList.push_back(pair<string, string>(string(), "console.heartbeat.#"));
    }
}

ConsoleEngineImpl::~ConsoleEngineImpl()
{
    // This function intentionally left blank.
}

bool ConsoleEngineImpl::getEvent(ConsoleEvent& event) const
{
    Mutex::ScopedLock _lock(lock);
    if (eventQueue.empty())
        return false;
    event = eventQueue.front()->copy();
    return true;
}

void ConsoleEngineImpl::popEvent()
{
    Mutex::ScopedLock _lock(lock);
    if (!eventQueue.empty())
        eventQueue.pop_front();
}

void ConsoleEngineImpl::addConnection(BrokerProxy& broker, void* /*context*/)
{
    Mutex::ScopedLock _lock(lock);
    brokerList.push_back(broker.impl);
}

void ConsoleEngineImpl::delConnection(BrokerProxy& broker)
{
    Mutex::ScopedLock _lock(lock);
    for (vector<BrokerProxyImpl*>::iterator iter = brokerList.begin();
         iter != brokerList.end(); iter++)
        if (*iter == broker.impl) {
            brokerList.erase(iter);
            break;
        }
}

uint32_t ConsoleEngineImpl::packageCount() const
{
    Mutex::ScopedLock _lock(lock);
    return packages.size();
}

const string& ConsoleEngineImpl::getPackageName(uint32_t idx) const
{
    const static string empty;

    Mutex::ScopedLock _lock(lock);
    if (idx >= packages.size())
        return empty;

    PackageList::const_iterator iter = packages.begin();
    for (uint32_t i = 0; i < idx; i++) iter++;
    return iter->first;
}

uint32_t ConsoleEngineImpl::classCount(const char* packageName) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(packageName);
    if (pIter == packages.end())
        return 0;

    const ObjectClassList& oList = pIter->second.first;
    const EventClassList& eList = pIter->second.second;

    return oList.size() + eList.size();
}

const SchemaClassKey* ConsoleEngineImpl::getClass(const char* packageName, uint32_t idx) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(packageName);
    if (pIter == packages.end())
        return 0;

    const ObjectClassList& oList = pIter->second.first;
    const EventClassList& eList = pIter->second.second;
    uint32_t count = 0;
    
    for (ObjectClassList::const_iterator oIter = oList.begin();
         oIter != oList.end(); oIter++) {
        if (count == idx)
            return oIter->second->getClassKey();
        count++;
    }

    for (EventClassList::const_iterator eIter = eList.begin();
         eIter != eList.end(); eIter++) {
        if (count == idx)
            return eIter->second->getClassKey();
        count++;
    }

    return 0;
}

ClassKind ConsoleEngineImpl::getClassKind(const SchemaClassKey* key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return CLASS_OBJECT;

    const EventClassList& eList = pIter->second.second;
    if (eList.find(key->impl) != eList.end())
        return CLASS_EVENT;
    return CLASS_OBJECT;
}

const SchemaObjectClass* ConsoleEngineImpl::getObjectClass(const SchemaClassKey* key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return 0;

    const ObjectClassList& oList = pIter->second.first;
    ObjectClassList::const_iterator iter = oList.find(key->impl);
    if (iter == oList.end())
        return 0;
    return iter->second->envelope;
}

const SchemaEventClass* ConsoleEngineImpl::getEventClass(const SchemaClassKey* key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return 0;

    const EventClassList& eList = pIter->second.second;
    EventClassList::const_iterator iter = eList.find(key->impl);
    if (iter == eList.end())
        return 0;
    return iter->second->envelope;
}

void ConsoleEngineImpl::bindPackage(const char* packageName)
{
    stringstream key;
    key << "console.obj.*.*." << packageName << ".#";
    Mutex::ScopedLock _lock(lock);
    bindingList.push_back(pair<string, string>(string(), key.str()));
    for (vector<BrokerProxyImpl*>::iterator iter = brokerList.begin();
         iter != brokerList.end(); iter++)
        (*iter)->addBinding(QMF_EXCHANGE, key.str());
}

void ConsoleEngineImpl::bindClass(const SchemaClassKey* classKey)
{
    stringstream key;
    key << "console.obj.*.*." << classKey->getPackageName() << "." << classKey->getClassName() << ".#";
    Mutex::ScopedLock _lock(lock);
    bindingList.push_back(pair<string, string>(string(), key.str()));
    for (vector<BrokerProxyImpl*>::iterator iter = brokerList.begin();
         iter != brokerList.end(); iter++)
        (*iter)->addBinding(QMF_EXCHANGE, key.str());
}

void ConsoleEngineImpl::bindClass(const char* packageName, const char* className)
{
    stringstream key;
    key << "console.obj.*.*." << packageName << "." << className << ".#";
    Mutex::ScopedLock _lock(lock);
    bindingList.push_back(pair<string, string>(string(), key.str()));
    for (vector<BrokerProxyImpl*>::iterator iter = brokerList.begin();
         iter != brokerList.end(); iter++)
        (*iter)->addBinding(QMF_EXCHANGE, key.str());
}

/*
void ConsoleEngineImpl::startSync(const Query& query, void* context, SyncQuery& sync)
{
}

void ConsoleEngineImpl::touchSync(SyncQuery& sync)
{
}

void ConsoleEngineImpl::endSync(SyncQuery& sync)
{
}
*/

void ConsoleEngineImpl::learnPackage(const string& packageName)
{
    Mutex::ScopedLock _lock(lock);
    if (packages.find(packageName) == packages.end())
        packages.insert(pair<string, pair<ObjectClassList, EventClassList> >
                        (packageName, pair<ObjectClassList, EventClassList>(ObjectClassList(), EventClassList())));
}

void ConsoleEngineImpl::learnClass(SchemaObjectClassImpl::Ptr cls)
{
    Mutex::ScopedLock _lock(lock);
    const SchemaClassKey* key = cls->getClassKey();
    PackageList::iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return;

    ObjectClassList& list = pIter->second.first;
    if (list.find(key->impl) == list.end())
        list[key->impl] = cls;
}

void ConsoleEngineImpl::learnClass(SchemaEventClassImpl::Ptr cls)
{
    Mutex::ScopedLock _lock(lock);
    const SchemaClassKey* key = cls->getClassKey();
    PackageList::iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return;

    EventClassList& list = pIter->second.second;
    if (list.find(key->impl) == list.end())
        list[key->impl] = cls;
}

bool ConsoleEngineImpl::haveClass(const SchemaClassKeyImpl& key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key.getPackageName());
    if (pIter == packages.end())
        return false;

    const ObjectClassList& oList = pIter->second.first;
    const EventClassList& eList = pIter->second.second;

    return oList.find(&key) != oList.end() || eList.find(&key) != eList.end();
}

SchemaObjectClassImpl::Ptr ConsoleEngineImpl::getSchema(const SchemaClassKeyImpl& key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key.getPackageName());
    if (pIter == packages.end())
        return SchemaObjectClassImpl::Ptr();

    const ObjectClassList& oList = pIter->second.first;
    ObjectClassList::const_iterator iter = oList.find(&key);
    if (iter == oList.end())
        return SchemaObjectClassImpl::Ptr();

    return iter->second;
}

//==================================================================
// Wrappers
//==================================================================

ConsoleEngine::ConsoleEngine(const ConsoleSettings& settings) : impl(new ConsoleEngineImpl(this, settings)) {}
ConsoleEngine::~ConsoleEngine() { delete impl; }
bool ConsoleEngine::getEvent(ConsoleEvent& event) const { return impl->getEvent(event); }
void ConsoleEngine::popEvent() { impl->popEvent(); }
void ConsoleEngine::addConnection(BrokerProxy& broker, void* context) { impl->addConnection(broker, context); }
void ConsoleEngine::delConnection(BrokerProxy& broker) { impl->delConnection(broker); }
uint32_t ConsoleEngine::packageCount() const { return impl->packageCount(); }
const char* ConsoleEngine::getPackageName(uint32_t idx) const { return impl->getPackageName(idx).c_str(); }
uint32_t ConsoleEngine::classCount(const char* packageName) const { return impl->classCount(packageName); }
const SchemaClassKey* ConsoleEngine::getClass(const char* packageName, uint32_t idx) const { return impl->getClass(packageName, idx); }
ClassKind ConsoleEngine::getClassKind(const SchemaClassKey* key) const { return impl->getClassKind(key); }
const SchemaObjectClass* ConsoleEngine::getObjectClass(const SchemaClassKey* key) const { return impl->getObjectClass(key); }
const SchemaEventClass* ConsoleEngine::getEventClass(const SchemaClassKey* key) const { return impl->getEventClass(key); }
void ConsoleEngine::bindPackage(const char* packageName) { impl->bindPackage(packageName); }
void ConsoleEngine::bindClass(const SchemaClassKey* key) { impl->bindClass(key); }
void ConsoleEngine::bindClass(const char* packageName, const char* className) { impl->bindClass(packageName, className); }
//void ConsoleEngine::startSync(const Query& query, void* context, SyncQuery& sync) { impl->startSync(query, context, sync); }
//void ConsoleEngine::touchSync(SyncQuery& sync) { impl->touchSync(sync); }
//void ConsoleEngine::endSync(SyncQuery& sync) { impl->endSync(sync); }


