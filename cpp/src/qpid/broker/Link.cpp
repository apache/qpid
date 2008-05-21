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

#include "Link.h"
#include "LinkRegistry.h"
#include "Broker.h"
#include "Connection.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/management/Link.h"
#include "boost/bind.hpp"
#include "qpid/log/Statement.h"

using namespace qpid::broker;
using qpid::framing::Buffer;
using qpid::framing::FieldTable;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
using qpid::sys::Mutex;

Link::Link(LinkRegistry*  _links,
           MessageStore*  _store,
           string&        _host,
           uint16_t       _port,
           bool           _useSsl,
           bool           _durable,
           string&        _authMechanism,
           string&        _username,
           string&        _password,
           Broker*        _broker,
           management::Manageable* parent)
    : links(_links), store(_store), host(_host), port(_port), useSsl(_useSsl), durable(_durable),
      authMechanism(_authMechanism), username(_username), password(_password),
      persistenceId(0), broker(_broker), state(0),
      visitCount(0),
      currentInterval(1),
      closing(false),
      channelCounter(1),
      connection(0)
{
    if (parent != 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent();
        if (agent.get() != 0)
        {
            mgmtObject = management::Link::shared_ptr
                (new management::Link(this, parent, _host, _port, _useSsl, _durable));
            if (!durable)
                agent->addObject(mgmtObject);
        }
    }
    setStateLH(STATE_WAITING);
}

Link::~Link ()
{
    if (state == STATE_OPERATIONAL && connection != 0)
        connection->close();

    if (mgmtObject.get () != 0)
        mgmtObject->resourceDestroy ();
}

void Link::setStateLH (int newState)
{
    if (newState == state)
        return;

    state = newState;
    if (mgmtObject.get() == 0)
        return;

    switch (state)
    {
    case STATE_WAITING     : mgmtObject->set_state("Waiting");     break;
    case STATE_CONNECTING  : mgmtObject->set_state("Connecting");  break;
    case STATE_OPERATIONAL : mgmtObject->set_state("Operational"); break;
    case STATE_FAILED      : mgmtObject->set_state("Failed");      break;
    case STATE_CLOSED      : mgmtObject->set_state("Closed");      break;
    }
}

void Link::startConnectionLH ()
{
    try {
        broker->connect (host, port, useSsl,
                         boost::bind (&Link::closed, this, _1, _2));
        setStateLH(STATE_CONNECTING);
    } catch(std::exception& e) {
        setStateLH(STATE_WAITING);
        mgmtObject->set_lastError (e.what());
    }
}

void Link::established ()
{
    Mutex::ScopedLock mutex(lock);

    QPID_LOG (info, "Inter-broker link established to " << host << ":" << port);
    setStateLH(STATE_OPERATIONAL);
    currentInterval = 1;
    visitCount      = 0;
    if (closing)
        destroy();
}

void Link::closed (int, std::string text)
{
    Mutex::ScopedLock mutex(lock);

    connection = 0;

    if (state == STATE_OPERATIONAL)
        QPID_LOG (warning, "Inter-broker link disconnected from " << host << ":" << port);

    for (Bridges::iterator i = active.begin(); i != active.end(); i++)
        created.push_back(*i);
    active.clear();

    if (state != STATE_FAILED)
    {
        setStateLH(STATE_WAITING);
        mgmtObject->set_lastError (text);
    }

    if (closing)
        destroy();
}

void Link::destroy ()
{
    Mutex::ScopedLock mutex(lock);
    Bridges toDelete;

    QPID_LOG (info, "Inter-broker link to " << host << ":" << port << " removed by management");
    if (connection)
        connection->close(403, "closed by management");

    setStateLH(STATE_CLOSED);

    // Move the bridges to be deleted into a local vector so there is no
    // corruption of the iterator caused by bridge deletion.
    for (Bridges::iterator i = active.begin(); i != active.end(); i++)
        toDelete.push_back(*i);
    active.clear();

    for (Bridges::iterator i = created.begin(); i != created.end(); i++)
        toDelete.push_back(*i);
    created.clear();

    // Now delete all bridges on this link.
    for (Bridges::iterator i = toDelete.begin(); i != toDelete.end(); i++)
        (*i)->destroy();
    toDelete.clear();

    links->destroy (host, port);
}

void Link::add(Bridge::shared_ptr bridge)
{
    Mutex::ScopedLock mutex(lock);
    created.push_back (bridge);
}

void Link::cancel(Bridge::shared_ptr bridge)
{
    Mutex::ScopedLock mutex(lock);

    for (Bridges::iterator i = created.begin(); i != created.end(); i++) {
        if ((*i).get() == bridge.get()) {
            created.erase(i);
            break;
        }
    }
    for (Bridges::iterator i = active.begin(); i != active.end(); i++) {
        if ((*i).get() == bridge.get()) {
            bridge->cancel();
            active.erase(i);
            break;
        }
    }
}

void Link::ioThreadProcessing()
{
    Mutex::ScopedLock mutex(lock);

    if (state != STATE_OPERATIONAL)
        return;

    //process any pending creates
    if (!created.empty()) {
        for (Bridges::iterator i = created.begin(); i != created.end(); ++i) {
            active.push_back(*i);
            (*i)->create(*connection);
        }
        created.clear();
    }
}

void Link::setConnection(Connection* c)
{
    Mutex::ScopedLock mutex(lock);
    connection = c;
}

void Link::maintenanceVisit ()
{
    Mutex::ScopedLock mutex(lock);

    if (state == STATE_WAITING)
    {
        visitCount++;
        if (visitCount >= currentInterval)
        {
            visitCount = 0;
            currentInterval *= 2;
            if (currentInterval > MAX_INTERVAL)
                currentInterval = MAX_INTERVAL;
            startConnectionLH();
        }
    }
    else if (state == STATE_OPERATIONAL && !created.empty() && connection != 0)
        connection->requestIOProcessing (boost::bind(&Link::ioThreadProcessing, this));
}

uint Link::nextChannel()
{
    Mutex::ScopedLock mutex(lock);

    return channelCounter++;
}

void Link::notifyConnectionForced(const string text)
{
    Mutex::ScopedLock mutex(lock);

    setStateLH(STATE_FAILED);
    mgmtObject->set_lastError(text);
}

void Link::setPersistenceId(uint64_t id) const
{
    if (mgmtObject != 0 && persistenceId == 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();
        agent->addObject (mgmtObject, id);
    }
    persistenceId = id;
}

const string& Link::getName() const
{
    return host;
}

Link::shared_ptr Link::decode(LinkRegistry& links, Buffer& buffer)
{
    string   host;
    uint16_t port;
    string   authMechanism;
    string   username;
    string   password;
    
    buffer.getShortString(host);
    port = buffer.getShort();
    bool useSsl(buffer.getOctet());
    bool durable(buffer.getOctet());
    buffer.getShortString(authMechanism);
    buffer.getShortString(username);
    buffer.getShortString(password);

    return links.declare(host, port, useSsl, durable, authMechanism, username, password).first;
}

void Link::encode(Buffer& buffer) const 
{
    buffer.putShortString(string("link"));
    buffer.putShortString(host);
    buffer.putShort(port);
    buffer.putOctet(useSsl  ? 1 : 0);
    buffer.putOctet(durable ? 1 : 0);
    buffer.putShortString(authMechanism);
    buffer.putShortString(username);
    buffer.putShortString(password);
}

uint32_t Link::encodedSize() const 
{ 
    return host.size() + 1 // short-string (host)
        + 5                // short-string ("link")
        + 2                // port
        + 1                // useSsl
        + 1                // durable
        + authMechanism.size() + 1
        + username.size() + 1
        + password.size() + 1;
}

ManagementObject::shared_ptr Link::GetManagementObject (void) const
{
    return boost::dynamic_pointer_cast<ManagementObject> (mgmtObject);
}

Manageable::status_t Link::ManagementMethod (uint32_t op, management::Args& args)
{
    switch (op)
    {
    case management::Link::METHOD_CLOSE :
        closing = true;
        if (state != STATE_CONNECTING)
            destroy();
        return Manageable::STATUS_OK;

    case management::Link::METHOD_BRIDGE :
        management::ArgsLinkBridge iargs =
            dynamic_cast<const management::ArgsLinkBridge&>(args);

        // Durable bridges are only valid on durable links
        if (iargs.i_durable && !durable)
            return Manageable::STATUS_INVALID_PARAMETER;

        std::pair<Bridge::shared_ptr, bool> result =
            links->declare (host, port, iargs.i_durable, iargs.i_src,
                            iargs.i_dest, iargs.i_key, iargs.i_src_is_queue,
                            iargs.i_src_is_local, iargs.i_tag, iargs.i_excludes);

        if (result.second && iargs.i_durable)
            store->create(*result.first);

        return Manageable::STATUS_OK;
    }

    return Manageable::STATUS_UNKNOWN_METHOD;
}
