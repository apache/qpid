#ifndef _IncomingMessage_
#define _IncomingMessage_

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
#include "../sys/Mutex.h"
#include <map>
#include <vector>


namespace qpid {
namespace client {

class Message;

/**
 * Manage incoming messages.
 *
 * Uses reference and destination concepts from 0-9 Messsage class.
 *
 * Basic messages use special destination and reference names to indicate
 * get-ok, return etc. messages.
 *
 */
class IncomingMessage {
  public:
    /** Accumulate data associated with a set of messages. */
    struct Reference {
        std::string data;
        std::vector<Message> messages;
    };

    /** Interface to a destination for messages. */
    class Destination {
      public:
        virtual ~Destination();

        /** Pass a message to the destination */
        virtual void message(const Message&) = 0;

        /** Notify destination of queue-empty contition */
        virtual void empty() = 0;
    };


    /** Add a reference. Throws if already open. */
    void openReference(const std::string& name);

    /** Get a reference. Throws if not already open. */
    void appendReference(const std::string& name,
                         const std::string& data);

    /** Create a message to destination associated with reference
     *@exception if destination or reference non-existent.
     */
    Message&  createMessage(const std::string& destination,
                            const std::string& reference);

    /** Get a reference.
     *@exception if non-existent.
     */
    Reference& getReference(const std::string& name);
    
    /** Close a reference and deliver all its messages.
     * Throws if not open or a message has an invalid destination.
     */
    void closeReference(const std::string& name);

    /** Add a destination.
     *@exception if a different Destination is already registered
     * under name.
     */
    void addDestination(std::string name, Destination&);

    /** Remove a destination. Throws if does not exist */
    void removeDestination(std::string name);

    /** Get a destination. Throws if does not exist */
    Destination& getDestination(const std::string& name);
  private:

    typedef std::map<std::string, Reference> ReferenceMap;
    typedef std::map<std::string, Destination*> DestinationMap;
    
    Reference& getRefUnlocked(const std::string& name);
    Destination& getDestUnlocked(const std::string& name);

    mutable sys::Mutex lock;
    ReferenceMap references;
    DestinationMap destinations;
};

}}


#endif
