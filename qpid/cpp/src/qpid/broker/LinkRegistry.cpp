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
#include "LinkRegistry.h"
#include "qpid/log/Statement.h"
#include <iostream>

using namespace qpid::broker;
using namespace qpid::sys;
using std::pair;
using std::stringstream;
using boost::intrusive_ptr;
namespace _qmf = qmf::org::apache::qpid::broker;

#define LINK_MAINT_INTERVAL 2

LinkRegistry::LinkRegistry (Broker* _broker) : broker(_broker), parent(0), store(0)
{
    timer.add (intrusive_ptr<TimerTask> (new Periodic(*this)));
}

LinkRegistry::Periodic::Periodic (LinkRegistry& _links) :
    TimerTask (Duration (LINK_MAINT_INTERVAL * TIME_SEC)), links(_links) {}

void LinkRegistry::Periodic::fire ()
{
    links.periodicMaintenance ();
    links.timer.add (intrusive_ptr<TimerTask> (new Periodic(links)));
}

void LinkRegistry::periodicMaintenance ()
{
    Mutex::ScopedLock locker(lock);

    linksToDestroy.clear();
    bridgesToDestroy.clear();
    for (LinkMap::iterator i = links.begin(); i != links.end(); i++)
        i->second->maintenanceVisit();
    //now process any requests for re-addressing
    for (AddressMap::iterator i = reMappings.begin(); i != reMappings.end(); i++)
        updateAddress(i->first, i->second);
    reMappings.clear();
}

void LinkRegistry::changeAddress(const TcpAddress& oldAddress, const TcpAddress& newAddress)
{
    //done on periodic maintenance thread; hold changes in separate
    //map to avoid modifying the link map that is iterated over
    reMappings[createKey(oldAddress)] = newAddress;
}

bool LinkRegistry::updateAddress(const std::string& oldKey, const TcpAddress& newAddress)
{
    std::string newKey = createKey(newAddress);
    if (links.find(newKey) != links.end()) {
        QPID_LOG(error, "Attempted to update key from " << oldKey << " to " << newKey << " which is already in use");
        return false;
    } else {
        LinkMap::iterator i = links.find(oldKey);
        if (i == links.end()) {
            QPID_LOG(error, "Attempted to update key from " << oldKey << " which does not exist, to " << newKey);
            return false;
        } else {
            links[newKey] = i->second;
            i->second->reconnect(newAddress);
            links.erase(oldKey);
            QPID_LOG(info, "Updated link key from " << oldKey << " to " << newKey);
            return true;
        }
    }
}

pair<Link::shared_ptr, bool> LinkRegistry::declare(string&  host,
                                                   uint16_t port,
                                                   string&  transport,
                                                   bool     durable,
                                                   string&  authMechanism,
                                                   string&  username,
                                                   string&  password)

{
    Mutex::ScopedLock   locker(lock);
    stringstream        keystream;
    keystream << host << ":" << port;
    string key = string(keystream.str());

    LinkMap::iterator i = links.find(key);
    if (i == links.end())
    {
        Link::shared_ptr link;

        link = Link::shared_ptr (new Link (this, store, host, port, transport, durable,
                                           authMechanism, username, password,
                                           broker, parent));
        links[key] = link;
        return std::pair<Link::shared_ptr, bool>(link, true);
    }
    return std::pair<Link::shared_ptr, bool>(i->second, false);
}

pair<Bridge::shared_ptr, bool> LinkRegistry::declare(std::string& host,
                                                     uint16_t     port,
                                                     bool         durable,
                                                     std::string& src,
                                                     std::string& dest,
                                                     std::string& key,
                                                     bool         isQueue,
                                                     bool         isLocal,
                                                     std::string& tag,
                                                     std::string& excludes,
                                                     bool         dynamic,
                                                     uint16_t     sync)
{
    Mutex::ScopedLock locker(lock);
    stringstream      keystream;
    keystream << host << ":" << port;
    string linkKey = string(keystream.str());

    keystream << "!" << src << "!" << dest << "!" << key;
    string bridgeKey = string(keystream.str());

    LinkMap::iterator l = links.find(linkKey);
    if (l == links.end())
        return pair<Bridge::shared_ptr, bool>(Bridge::shared_ptr(), false);

    BridgeMap::iterator b = bridges.find(bridgeKey);
    if (b == bridges.end())
    {
        _qmf::ArgsLinkBridge args;
        Bridge::shared_ptr bridge;

        args.i_durable    = durable;
        args.i_src        = src;
        args.i_dest       = dest;
        args.i_key        = key;
        args.i_srcIsQueue = isQueue;
        args.i_srcIsLocal = isLocal;
        args.i_tag        = tag;
        args.i_excludes   = excludes;
        args.i_dynamic    = dynamic;
        args.i_sync       = sync;

        bridge = Bridge::shared_ptr
            (new Bridge (l->second.get(), l->second->nextChannel(),
                         boost::bind(&LinkRegistry::destroy, this,
                                     host, port, src, dest, key), args));
        bridges[bridgeKey] = bridge;
        l->second->add(bridge);
        return std::pair<Bridge::shared_ptr, bool>(bridge, true);
    }
    return std::pair<Bridge::shared_ptr, bool>(b->second, false);
}

void LinkRegistry::destroy(const string& host, const uint16_t port)
{
    Mutex::ScopedLock   locker(lock);
    stringstream        keystream;
    keystream << host << ":" << port;
    string key = string(keystream.str());

    LinkMap::iterator i = links.find(key);
    if (i != links.end())
    {
        if (i->second->isDurable() && store)
            store->destroy(*(i->second));
        linksToDestroy[key] = i->second;
        links.erase(i);
    }
}

void LinkRegistry::destroy(const std::string& host,
                           const uint16_t     port,
                           const std::string& src,
                           const std::string& dest,
                           const std::string& key)
{
    Mutex::ScopedLock locker(lock);
    stringstream      keystream;
    keystream << host << ":" << port;
    string linkKey = string(keystream.str());

    LinkMap::iterator l = links.find(linkKey);
    if (l == links.end())
        return;

    keystream << "!" << src << "!" << dest << "!" << key;
    string bridgeKey = string(keystream.str());
    BridgeMap::iterator b = bridges.find(bridgeKey);
    if (b == bridges.end())
        return;

    l->second->cancel(b->second);
    if (b->second->isDurable())
        store->destroy(*(b->second));
    bridgesToDestroy[bridgeKey] = b->second;
    bridges.erase(b);
}

void LinkRegistry::setStore (MessageStore* _store)
{
    store = _store;
}

MessageStore* LinkRegistry::getStore() const {
    return store;
}

void LinkRegistry::notifyConnection(const std::string& key, Connection* c)
{
    Mutex::ScopedLock locker(lock);
    LinkMap::iterator l = links.find(key);
    if (l != links.end())
    {
        l->second->established();
        l->second->setConnection(c);
    }
}

void LinkRegistry::notifyClosed(const std::string& key)
{
    Mutex::ScopedLock locker(lock);
    LinkMap::iterator l = links.find(key);
    if (l != links.end())
        l->second->closed(0, "Closed by peer");
}

void LinkRegistry::notifyConnectionForced(const std::string& key, const std::string& text)
{
    Mutex::ScopedLock locker(lock);
    LinkMap::iterator l = links.find(key);
    if (l != links.end())
        l->second->notifyConnectionForced(text);
}

std::string LinkRegistry::getAuthMechanism(const std::string& key)
{
    Mutex::ScopedLock locker(lock);
    LinkMap::iterator l = links.find(key);
    if (l != links.end())
        return l->second->getAuthMechanism();
    return string("ANONYMOUS");
}

std::string LinkRegistry::getAuthCredentials(const std::string& key)
{
    Mutex::ScopedLock locker(lock);
    LinkMap::iterator l = links.find(key);
    if (l == links.end())
        return string();

    string result;
    result += '\0';
    result += l->second->getUsername();
    result += '\0';
    result += l->second->getPassword();

    return result;
}

std::string LinkRegistry::getAuthIdentity(const std::string& key)
{
    Mutex::ScopedLock locker(lock);
    LinkMap::iterator l = links.find(key);
    if (l == links.end())
        return string();

    return l->second->getUsername();
}


std::string LinkRegistry::createKey(const TcpAddress& a)
{
    stringstream        keystream;
    keystream << a.host << ":" << a.port;
    return string(keystream.str());
}
