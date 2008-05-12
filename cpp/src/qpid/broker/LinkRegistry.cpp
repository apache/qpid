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
#include <iostream>

using namespace qpid::broker;
using namespace qpid::sys;
using std::pair;
using std::stringstream;
using boost::intrusive_ptr;

#define LINK_MAINT_INTERVAL 5

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
    for (LinkMap::iterator i = links.begin(); i != links.end(); i++)
        i->second->maintenanceVisit();
}

pair<Link::shared_ptr, bool> LinkRegistry::declare(std::string& host,
                                                   uint16_t     port,
                                                   bool         useSsl,
                                                   bool         durable)
{
    Mutex::ScopedLock   locker(lock);
    stringstream        keystream;
    keystream << host << ":" << port;
    string key = string(keystream.str());

    LinkMap::iterator i = links.find(key);
    if (i == links.end())
    {
        Link::shared_ptr link;

        link = Link::shared_ptr (new Link (this, host, port, useSsl, durable, broker, parent));
        links[key] = link;
        return std::pair<Link::shared_ptr, bool>(link, true);
    }
    return std::pair<Link::shared_ptr, bool>(i->second, false);
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

void LinkRegistry::setStore (MessageStore* _store)
{
    assert (store == 0 && _store != 0);
    store = _store;
}

MessageStore* LinkRegistry::getStore() const {
    return store;
}

