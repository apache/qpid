#ifndef _QmfEngineAgent_
#define _QmfEngineAgent_

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

#include <qmf/engine/Schema.h>
#include <qmf/engine/ObjectId.h>
#include <qmf/engine/Object.h>
#include <qmf/engine/Event.h>
#include <qmf/engine/Query.h>
#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Variant.h>

namespace qmf {
namespace engine {

    /**
     * AgentEvent
     *
     *  This structure represents a QMF event coming from the agent to
     *  the application.
     */
    struct AgentEvent {
        enum EventKind {
            GET_QUERY      = 1,
            START_SYNC     = 2,
            END_SYNC       = 3,
            METHOD_CALL    = 4
        };

        EventKind    kind;
        uint32_t     sequence;    // Protocol sequence (for all kinds)
        char*        authUserId;  // Authenticated user ID (for all kinds)
        char*        authToken;   // Authentication token if issued (for all kinds)
        char*        name;        // Name of the method/sync query
                                  //    (METHOD_CALL, START_SYNC, END_SYNC)
        Object*      object;      // Object involved in method call (METHOD_CALL)
        char*        objectKey;   // Object key for method call (METHOD_CALL)
        Query*       query;       // Query parameters (GET_QUERY, START_SYNC)
        qpid::messaging::Variant::Map*  arguments;   // Method parameters (METHOD_CALL)
        const SchemaClass* objectClass; // (METHOD_CALL)
    };

    class AgentImpl;

    /**
     * Agent - Protocol engine for the QMF agent
     */
    class Agent {
    public:
        Agent(const char* vendor, const char* product, const char* name, const char* domain=0, bool internalStore=true);
        ~Agent();

        /**
         * Set an agent attribute that can be used to describe this agent to consoles.
         *@param key Null-terminated string that is the name of the attribute.
         *@param value Variant value (or any API type) of the attribute.
         */
        void setAttr(const char* key, const qpid::messaging::Variant& value);

        /**
         * Configure the directory path for storing persistent data.
         *@param path Null-terminated string containing a directory path where files can be
         *            created, written, and read.  If NULL, no persistent storage will be
         *            attempted.
         */
        void setStoreDir(const char* path);

        /**
         * Configure the directory path for files transferred over QMF.
         *@param path Null-terminated string containing a directory path where files can be
         *            created, deleted, written, and read.  If NULL, file transfers shall not
         *            be permitted.
         */
        void setTransferDir(const char* path);

        /**
         * Get the next application event from the agent engine.
         *@param event The event iff the return value is true
         *@return true if event is valid, false if there are no events to process
         */
        bool getEvent(AgentEvent& event) const;

        /**
         * Remove and discard one event from the head of the event queue.
         */
        void popEvent();

        /**
         * Provide the AMQP connection to be used for this agent.
         */
        void setConnection(qpid::messaging::Connection& conn);

        /**
         * Respond to a method request.
         *@param sequence  The sequence number from the method request event.
         *@param status    The method's completion status.
         *@param text      Status text ("OK" or an error message)
         *@param arguments The list of output arguments from the method call.
         */
        void methodResponse(uint32_t sequence, uint32_t status, char* text, const qpid::messaging::Variant::Map& arguments);

        /**
         * Send a content indication to the QMF bus.  This is only needed for objects that are
         * managed by the application.  This is *NOT* needed for objects managed by the Agent
         * (inserted using addObject).
         *@param sequence The sequence number of the GET request or the SYNC_START request.
         *@param object   The object (annotated with "changed" flags) for publication.
         */
        void queryResponse(uint32_t sequence, Object& object);

        /**
         * Indicate the completion of a query.  This is not used for SYNC_START requests.
         *@param sequence The sequence number of the GET request.
         */
        void queryComplete(uint32_t sequence);

        /**
         * Register a schema class with the Agent.
         *@param cls A SchemaClass object that defines data managed by the agent.
         */
        void registerClass(SchemaClass* cls);

        /**
         * Give an object to the Agent for storage and management.  Once added, the agent takes
         * responsibility for the life cycle of the object.
         *@param obj The object to be managed by the Agent.
         *@param key A unique name (a primary key) to be used to address this object. If
         *           left null, the agent will create a unique name for the object.
         *@return The key for the managed object.
         */
        const char* addObject(Object& obj, const char* key=0);

        /**
         * Raise an event into the QMF network..
         *@param event The event object for the event to be raised.
         */
        void raiseEvent(Event& event);

    private:
        AgentImpl* impl;
    };
}
}

#endif

