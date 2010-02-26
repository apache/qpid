#ifndef _QmfAgent_
#define _QmfAgent_

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

#include "qmf/QmfImportExport.h"
#include "qmf/Notifiable.h"
#include "qpid/sys/Time.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Variant.h"
#include <string>

namespace qmf {

    class AgentImpl;
    class ObjectId;
    class AgentObject;
    class Event;
    class SchemaClass;
    class Query;

    /**
     * AgentHandler is used by agents that select the internalStore=false option (see Agent
     * constructor) or by agents that wish to provide access control for queries and methods.
     *
     * \ingroup qmfapi
     */
    class AgentHandler {
        QMF_EXTERN virtual ~AgentHandler();

        /**
         * allowQuery is called before a query operation is executed.  If true is returned
         * by this function, the query will proceed.  If false is returned, the query will
         * be forbidden.
         *
         * @param q The query being requested.
         * @param userId The authenticated identity of the user requesting the query.
         */
        virtual bool allowQuery(const Query& q, const std::string& userId);

        /**
         * allowMethod is called before a method call is executed.  If true is returned
         * by this function, the method call will proceed.  If false is returned, the method
         * call will be forbidden.
         *
         * @param name The name of the method being called.
         * @param args A value object (of type "map") that contains both input and output arguments.
         * @param oid The objectId that identifies the instance of the object being called.
         * @param cls The Schema describing the object being called.
         * @param userId The authenticated identity of the requesting user.
         */
        virtual bool allowMethod(const std::string& name,
                                 const qpid::messaging::Variant::Map& args,
                                 const ObjectId& oid,
                                 const SchemaClass& cls,
                                 const std::string& userId);

        /**
         * query is called when the agent receives a query request.  The handler must invoke
         * Agent::queryResponse zero or more times (using the supplied context) followed by
         * a single invocation of Agent::queryComplete.  These calls do not need to be made
         * synchronously in the context of this function.  They may occur before or after this
         * function returns.
         *
         * This function will only be invoked if internalStore=false in the Agent's constructor.
         *
         * @param context A context value to use in resulting calls to queryResponse and quertComplete.
         * @param q The query requested by the console.
         * @param userId the authenticated identity of the user requesting the query.
         */
        virtual void query(uint32_t context, const Query& q, const std::string& userId);

        /**
         * syncStart is called when a console requests a standing query.  This function must
         * behave exactly like AgentHandler::query (i.e. send zero or more responses followed
         * by a queryComplete) except it then remembers the context and the query and makes
         * subsequent queryResponse calls whenever appropriate according the the query.
         *
         * The standing query shall stay in effect until syncStop is called with the same context
         * value or until a specified period of time elapses without receiving a syncTouch for the
         * context.
         *
         * This function will only be invoked if internalStore=false in the Agent's constructor.
         *
         * @param context A context value to use in resulting calls to queryResponse and queryComplete.
         * @param q The query requested by the console.
         * @param userId the authenticated identity of the user requesting the query.
         */
        virtual void syncStart(uint32_t context, const Query& q, const std::string& userId);

        /**
         * syncTouch is called when the console that requested a standing query refreshes its
         * interest in the query.  The console must periodically "touch" a standing query to keep
         * it alive.  This prevents standing queries from accumulating when the console disconnects
         * before it can stop the query.
         *
         * This function will only be invoked if internalStore=false in the Agent's constructor.
         *
         * @param context The context supplied in a previous call to syncStart.
         * @param userId The authenticated identity of the requesting user.
         */
        virtual void syncTouch(uint32_t context, const std::string& userId);

        /**
         * syncStop is called when the console that requested a standing query no longer wishes to
         * receive data associated with that query.  The application shall stop processing this
         * query and shall remove its record of the context value.
         *
         * This function will only be invoked if internalStore=false in the Agent's constructor.
         *
         * @param context The context supplied in a previous call to syncStart.
         * @param userId The authenticated identity of the requesting user.
         */
        virtual void syncStop(uint32_t context, const std::string& userId);

        /**
         * methodCall is called when a console invokes a method on a QMF object.  The application
         * must call Agent::methodResponse once in response to this function.  The response does
         * not need to be called synchronously in the context of this function.  It may be called
         * before or after this function returns.
         *
         * This function will only be invoked if internalStore=false in the Agent's constructor.
         *
         * @param context A context value to use in resulting call to methodResponse.
         * @param name The name of the method being called.
         * @param args A value object (of type "map") that contains both input and output arguments.
         * @param oid The objectId that identifies the instance of the object being called.
         * @param cls The Schema describing the object being called.
         * @param userId The authenticated identity of the requesting user.
         */
        virtual void methodCall(uint32_t context, const std::string& name, qpid::messaging::Variant::Map& args,
                                const ObjectId& oid, const SchemaClass& cls, const std::string& userId);
    };

    /**
     * The Agent class is the QMF Agent portal.  It should be instantiated once and associated with a
     * Connection (setConnection) to connect an agent to the QMF infrastructure.
     *
     * \ingroup qmfapi
     */
    class Agent {
    public:
        /**
         * Create an instance of the Agent class.
         *
         * @param vendor A string identifying the vendor of the agent application.
         *               This should follow the reverse-domain-name form (i.e. org.apache).
         *
         * @param product A string identifying the product provided by the vendor.
         *
         * @param instance A string that uniquely identifies this instance of the agent.
         *                 If zero, the agent will generate a guid for the instance string.
         *
         * @param domain A string that defines the QMF domain that this agent should join.
         *               If zero, the agent will join the default QMF domain.
         *
         * @param internalStore If true, objects shall be tracked internally by the agent.
         *                      If false, the user of the agent must track the objects.
         *        If the agent is tracking the objects, queries and syncs are handled by
         *        the agent.  The only involvement the user has is to optionally authorize
         *        individual operations.  If the user is tracking the objects, the user code
         *        must implement queries and syncs (standing queries).
         *
         * @param handler A pointer to a class that implements the AgentHandler interface.
         *        This must be supplied if any of the following conditions are true:
         *          - The agent model contains methods
         *          - The user wishes to individually authorize query and sync operations.
         *          - internalStore = false
         *
         * @param notifiable A pointer to a class that implements the Notifiable interface.
         *        This argument is optional (may be supplied as 0).  If it is not supplied,
         *        notification callbacks will not be invoked.
         */
        QMF_EXTERN Agent(const std::string& vendor, const std::string& product, const std::string& instance="",
                         const std::string& domain="", bool internalStore=true,
                         AgentHandler* handler=0, Notifiable* notifiable=0);

        /**
         * Destroy an instance of the Agent class.
         */
        QMF_EXTERN ~Agent();

        /**
         * Set an attribute for the agent.  Attributes are visible to consoles and can be used to find
         * agents.
         *
         * @param name Name of the attribute to be set (or overwritten)
         *
         * @param value Value (of any variant type) of the attribute
         */
        QMF_EXTERN void setAttribute(const std::string& name, const qpid::messaging::Variant& value);

        /**
         * Set the persistent store file.  This file, if specified, is used to store state information
         * about the Agent.  For example, if object-ids must be persistent across restarts of the Agent
         * program, this file path must be supplied.
         *
         * @param path Full path to a file that is both writable and readable by the Agent program.
         */
        QMF_EXTERN void setStoreDir(const std::string& path);

        /**
         * Provide a connection (to a Qpid broker) over which the agent can communicate.
         *
         * @param conn Pointer to a Connection object.
         */
        QMF_EXTERN void setConnection(qpid::messaging::Connection& conn);

        /**
         * Register a class schema (object or event) with the agent.  The agent must have a registered
         * schema for an object class or an event class before it can handle objects or events of that
         * class.
         *
         * @param cls Pointer to the schema structure describing the class.
         */
        QMF_EXTERN void registerClass(SchemaClass* cls);

        /**
         * Invoke the handler (if supplied in the constructor) with events stored in the Agent's work
         * queue.  This function call is a way of supplying the Agent with a thread on which to run the
         * application's handler (the Agent will never invoke the handler on one of its internal threads).
         *
         * @param limit The maximum number of handler callbacks to invoke during this call.  Zero means
         *              there will be no limit on the number of invocations.
         *
         * @param timeout The time this call will block if there are no handler events to process.
         *
         * @return The number of handler events processed.  If the timeout expired, the return value will
         *         be zero.
         */
        QMF_EXTERN uint32_t invokeHandler(uint32_t limit=0, qpid::sys::Duration timeout=qpid::sys::TIME_INFINITE);

        /**
         * Add an object to the agent (for internal storage mode only).
         *
         * @param obj Reference to the object to be managed by the agent.
         *
         * @param persistent Iff true, the object ID assigned to the object shall indicate persistence
         *                   (i.e. the object ID shall be the same across restarts of the agent program).
         *
         * @param oid 64-bit value for the oid (if zero, the agent will assign the value).
         *
         * @param oidLo 32-bit value for the lower 32-bits of the oid.
         *
         * @param oidHi 32-bit value for the upper 32-bits of the oid.
         */
        QMF_EXTERN const ObjectId* addObject(AgentObject& obj, bool persistent=false, uint64_t oid=0);
        QMF_EXTERN const ObjectId* addObject(AgentObject& obj, bool persistent, uint32_t oidLo, uint32_t oidHi);

        /**
         * Raise a QMF event.
         *
         * @param event Reference to an event object to be raised to the QMF infrastructure.
         */
        QMF_EXTERN void raiseEvent(Event& event);

        /**
         * Provide a response to a query (for external storage mode only).
         *
         * @param context The context value supplied in the query (via the AgentHandler interface).
         *
         * @param object A reference to the agent that matched the query criteria.
         */
        QMF_EXTERN void queryResponse(uint32_t context, AgentObject& object);

        /**
         * Indicate that a query (or the initial dump of a sync) is complete (for external storage mode only).
         *
         * @param context The context value supplied in the query/sync (via the AgentHandler interface).
         */
        QMF_EXTERN void queryComplete(uint32_t context);

        /**
         * Provide the response to a method call.
         *
         * @param context The context value supplied in the method request (via the AgentHandler interface).
         *
         * @param args The argument list from the method call.  Must include the output arguments (may include
         *             the input arguments).
         *
         * @param exception Pointer to an exception value.  If status is non-null, the exception value is
         *                  sent to the caller.  It is optional (i.e. leave the pointer as 0), or may be
         *                  set to any legal value.  A string may be supplied, but an unmanaged object of
         *                  any schema may also be passed.
         */
        QMF_EXTERN void methodResponse(uint32_t context,
                                       const qpid::messaging::Variant::Map& args,
                                       const qpid::messaging::Variant& exception=qpid::messaging::Variant());

    private:
        /**
         * Private copy constructor and assignment operator ensure that objects of this class cannot
         * be copied.
         */
        Agent(const Agent&);
        const Agent& operator=(const Agent&);

        AgentImpl* impl;
    };

}

#endif
