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
using namespace qpid::broker;
using namespace qpid::sys;

ManagementAgent::ManagementAgent (uint16_t _interval) : interval (_interval)
{
    timer.add (TimerTask::shared_ptr (new Periodic(*this, interval)));
}

void ManagementAgent::setExchange (Exchange::shared_ptr _exchange)
{
    exchange = _exchange;
}

void ManagementAgent::addObject (ManagementObject::shared_ptr object)
{
    managementObjects.push_back (object);
    QPID_LOG(info, "Management Object Added");
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
    for (ManagementObjectVector::iterator iter = managementObjects.begin ();
         iter != managementObjects.end ();
         iter++)
    {
        ManagementObject::shared_ptr object = *iter;
        object->setAllChanged   ();
        object->setSchemaNeeded ();
    }
}

void ManagementAgent::PeriodicProcessing (void)
{
#define BUFSIZE   65536
#define THRESHOLD 16384
    char      msgChars[BUFSIZE];
    Buffer    msgBuffer (msgChars, BUFSIZE);
    uint32_t  contentSize;
    std::list<uint32_t> deleteList;

    if (managementObjects.empty ())
        return;
        
    Message::shared_ptr msg (new Message ());

    // Build the magic number for the management message.
    msgBuffer.putOctet ('A');
    msgBuffer.putOctet ('M');
    msgBuffer.putOctet ('0');
    msgBuffer.putOctet ('1');

    for (uint32_t idx = 0; idx < managementObjects.size (); idx++)
    {
        ManagementObject::shared_ptr object = managementObjects[idx];

        if (object->getSchemaNeeded ())
        {
            uint32_t startAvail = msgBuffer.available ();
            uint32_t recordLength;
            
            msgBuffer.putOctet ('S');  // opcode = Schema Record
            msgBuffer.putOctet (0);    // content-class = N/A
            msgBuffer.putShort (object->getObjectType ());
            msgBuffer.record   (); // Record the position of the length field
            msgBuffer.putLong  (0xFFFFFFFF); // Placeholder for length

            object->writeSchema (msgBuffer);
            recordLength = startAvail - msgBuffer.available ();
            msgBuffer.restore (true);         // Restore pointer to length field
            msgBuffer.putLong (recordLength);
            msgBuffer.restore ();             // Re-restore to get to the end of the buffer
        }

        if (object->getConfigChanged ())
        {
            uint32_t startAvail = msgBuffer.available ();
            uint32_t recordLength;
            
            msgBuffer.putOctet ('C');  // opcode = Content Record
            msgBuffer.putOctet ('C');  // content-class = Configuration
            msgBuffer.putShort (object->getObjectType ());
            msgBuffer.record   (); // Record the position of the length field
            msgBuffer.putLong  (0xFFFFFFFF); // Placeholder for length

            object->writeConfig (msgBuffer);
            recordLength = startAvail - msgBuffer.available ();
            msgBuffer.restore (true);         // Restore pointer to length field
            msgBuffer.putLong (recordLength);
            msgBuffer.restore ();             // Re-restore to get to the end of the buffer
        }
        
        if (object->getInstChanged ())
        {
            uint32_t startAvail = msgBuffer.available ();
            uint32_t recordLength;
            
            msgBuffer.putOctet ('C');  // opcode = Content Record
            msgBuffer.putOctet ('I');  // content-class = Instrumentation
            msgBuffer.putShort (object->getObjectType ());
            msgBuffer.record   (); // Record the position of the length field
            msgBuffer.putLong  (0xFFFFFFFF); // Placeholder for length

            object->writeInstrumentation (msgBuffer);
            recordLength = startAvail - msgBuffer.available ();
            msgBuffer.restore (true);         // Restore pointer to length field
            msgBuffer.putLong (recordLength);
            msgBuffer.restore ();             // Re-restore to get to the end of the buffer
        }

        if (object->isDeleted ())
            deleteList.push_back (idx);

        // Temporary protection against buffer overrun.
        // This needs to be replaced with frame fragmentation.
        if (msgBuffer.available () < THRESHOLD)
            break;
    }
    
    msgBuffer.putOctet ('X');  // End-of-message
    msgBuffer.putOctet (0);
    msgBuffer.putShort (0);
    msgBuffer.putLong  (8);

    contentSize = BUFSIZE - msgBuffer.available ();
    msgBuffer.reset ();

    AMQFrame method  (0, MessageTransferBody(ProtocolVersion(),
                                             0, "qpid.management", 0, 0));
    AMQFrame header  (0, AMQHeaderBody());
    AMQFrame content;

    content.setBody(AMQContentBody());
    content.castBody<AMQContentBody>()->decode(msgBuffer, contentSize);

    method.setEof  (false);
    header.setBof  (false);
    header.setEof  (false);
    content.setBof (false);

    msg->getFrames().append(method);
    msg->getFrames().append(header);

    MessageProperties* props = msg->getFrames().getHeaders()->get<MessageProperties>(true);
    props->setContentLength(contentSize);
    msg->getFrames().append(content);

    DeliverableMessage deliverable (msg);
    exchange->route (deliverable, "mgmt", 0);

    // Delete flagged objects
    for (std::list<uint32_t>::reverse_iterator iter = deleteList.rbegin ();
         iter != deleteList.rend ();
         iter++)
    {
        managementObjects.erase (managementObjects.begin () + *iter);
    }
    deleteList.clear ();
}

void ManagementAgent::dispatchCommand (Deliverable&      /*msg*/,
                                       const string&     /*routingKey*/,
                                       const FieldTable* /*args*/)
{
}

