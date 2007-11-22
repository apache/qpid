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
 
#include "ManagementAgent.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/log/Statement.h"
#include <qpid/broker/Message.h>
#include <qpid/broker/MessageDelivery.h>
#include <qpid/framing/AMQFrame.h>
#include <list>

using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::broker;
using namespace qpid::sys;

ManagementAgent::shared_ptr ManagementAgent::agent;

ManagementAgent::ManagementAgent (uint16_t _interval) : interval (_interval)
{
    timer.add (TimerTask::shared_ptr (new Periodic(*this, interval)));
    nextObjectId = uint64_t (qpid::sys::Duration (qpid::sys::now ()));
}

ManagementAgent::shared_ptr ManagementAgent::getAgent (void)
{
    if (agent.get () == 0)
        agent = shared_ptr (new ManagementAgent (10));

    return agent;
}

void ManagementAgent::setExchange (Exchange::shared_ptr _mexchange,
                                   Exchange::shared_ptr _dexchange)
{
    mExchange = _mexchange;
    dExchange = _dexchange;
}

void ManagementAgent::addObject (ManagementObject::shared_ptr object)
{
    uint64_t objectId = nextObjectId++;

    object->setObjectId (objectId);
    managementObjects[objectId] = object;
}

ManagementAgent::Periodic::Periodic (ManagementAgent& _agent, uint32_t _seconds)
    : TimerTask (qpid::sys::Duration (_seconds * qpid::sys::TIME_SEC)), agent(_agent) {}

void ManagementAgent::Periodic::fire ()
{
    agent.timer.add (TimerTask::shared_ptr (new Periodic (agent, agent.interval)));
    agent.PeriodicProcessing ();
}

void ManagementAgent::clientAdded (void)
{
    for (ManagementObjectMap::iterator iter = managementObjects.begin ();
         iter != managementObjects.end ();
         iter++)
    {
        ManagementObject::shared_ptr object = iter->second;
        object->setAllChanged   ();
        object->setSchemaNeeded ();
    }
}

void ManagementAgent::EncodeHeader (Buffer& buf)
{
    buf.putOctet ('A');
    buf.putOctet ('M');
    buf.putOctet ('0');
    buf.putOctet ('1');
}

void ManagementAgent::SendBuffer (Buffer&  buf,
                                  uint32_t length,
                                  Exchange::shared_ptr exchange,
                                  string   routingKey)
{
    intrusive_ptr<Message> msg (new Message ());
    AMQFrame method (in_place<MessageTransferBody>(
                         ProtocolVersion(), 0, exchange->getName (), 0, 0));
    AMQFrame header (in_place<AMQHeaderBody>());
    AMQFrame content(in_place<AMQContentBody>());

    QPID_LOG (debug, "ManagementAgent::SendBuffer - key="
              << routingKey << " len=" << length);

    content.castBody<AMQContentBody>()->decode(buf, length);

    method.setEof  (false);
    header.setBof  (false);
    header.setEof  (false);
    content.setBof (false);

    msg->getFrames().append(method);
    msg->getFrames().append(header);

    MessageProperties* props =
        msg->getFrames().getHeaders()->get<MessageProperties>(true);
    props->setContentLength(length);
    msg->getFrames().append(content);

    DeliverableMessage deliverable (msg);
    exchange->route (deliverable, routingKey, 0);
}

void ManagementAgent::PeriodicProcessing (void)
{
#define BUFSIZE   65536
#define THRESHOLD 16384
    char      msgChars[BUFSIZE];
    uint32_t  contentSize;
    string    routingKey;
    std::list<uint64_t> deleteList;

    if (managementObjects.empty ())
        return;
        
    for (ManagementObjectMap::iterator iter = managementObjects.begin ();
         iter != managementObjects.end ();
         iter++)
    {
        ManagementObject::shared_ptr object = iter->second;

        if (object->getSchemaNeeded ())
        {
            Buffer msgBuffer (msgChars, BUFSIZE);
            EncodeHeader (msgBuffer);
            msgBuffer.putOctet ('S');  // opcode = Schema Record
            msgBuffer.putOctet (0);    // content-class = N/A
            object->writeSchema (msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "mgmt.schema." + object->getClassName ();
            SendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        }

        if (object->getConfigChanged ())
        {
            Buffer msgBuffer (msgChars, BUFSIZE);
            EncodeHeader (msgBuffer);
            msgBuffer.putOctet ('C');  // opcode = Content Record
            msgBuffer.putOctet ('C');  // content-class = Configuration
            object->writeConfig (msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "mgmt.config." + object->getClassName ();
            SendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        }
        
        if (object->getInstChanged ())
        {
            Buffer msgBuffer (msgChars, BUFSIZE);
            EncodeHeader (msgBuffer);
            msgBuffer.putOctet ('C');  // opcode = Content Record
            msgBuffer.putOctet ('I');  // content-class = Instrumentation
            object->writeInstrumentation (msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "mgmt.inst." + object->getClassName ();
            SendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        }

        if (object->isDeleted ())
            deleteList.push_back (iter->first);
    }

    // Delete flagged objects
    for (std::list<uint64_t>::reverse_iterator iter = deleteList.rbegin ();
         iter != deleteList.rend ();
         iter++)
        managementObjects.erase (*iter);

    deleteList.clear ();
}

void ManagementAgent::dispatchCommand (Deliverable&      deliverable,
                                       const string&     routingKey,
                                       const FieldTable* /*args*/)
{
    size_t    pos, start;
    Message&  msg = ((DeliverableMessage&) deliverable).getMessage ();
    uint32_t  contentSize;

    if (routingKey.compare (0, 7, "method.") != 0)
    {
        QPID_LOG (debug, "Illegal routing key for dispatch: " << routingKey);
        return;
    }

    start = 7;
    if (routingKey.length () == start)
    {
        QPID_LOG (debug, "Missing package-name in routing key: " << routingKey);
        return;
    }

    pos = routingKey.find ('.', start);
    if (pos == string::npos || routingKey.length () == pos + 1)
    {
        QPID_LOG (debug, "Missing class-name in routing key: " << routingKey);
        return;
    }

    string packageName = routingKey.substr (start, pos - start);

    start = pos + 1;
    pos = routingKey.find ('.', start);
    if (pos == string::npos || routingKey.length () == pos + 1)
    {
        QPID_LOG (debug, "Missing method-name in routing key: " << routingKey);
        return;
    }

    string className = routingKey.substr (start, pos - start);

    start = pos + 1;
    string methodName = routingKey.substr (start, routingKey.length () - start);

    QPID_LOG (debug, "Dispatch package: " << packageName << ", class: "
              << className << ", method: " << methodName);

    contentSize = msg.encodedContentSize ();
    if (contentSize < 8 || contentSize > 65536)
        return;

    char     *inMem  = new char[contentSize];
    char     outMem[4096]; // TODO Fix This
    Buffer   inBuffer  (inMem,  contentSize);
    Buffer   outBuffer (outMem, 4096);
    uint32_t outLen;

    msg.encodeContent (inBuffer);
    inBuffer.reset ();

    uint32_t methodId = inBuffer.getLong     ();
    uint64_t objId    = inBuffer.getLongLong ();
    string   replyTo;

    inBuffer.getShortString (replyTo);

    QPID_LOG (debug, "    len = " << contentSize << ", methodId = " <<
              methodId << ", objId = " << objId);

    outBuffer.putLong (methodId);

    ManagementObjectMap::iterator iter = managementObjects.find (objId);
    if (iter == managementObjects.end ())
    {
        outBuffer.putLong        (2);
        outBuffer.putShortString ("Invalid Object Id");
    }
    else
    {
        iter->second->doMethod (methodName, inBuffer, outBuffer);
    }

    outLen = 4096 - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, dExchange, replyTo);
    free (inMem);
}

