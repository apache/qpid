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
package org.apache.qpid.qmf2.common;

import java.util.HashMap;
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.console.Agent;

/**
 * Descriptions below are taken from <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Proposal</a> 
 * <p>
 * A WorkItem describes an event that has arrived for the application to process.
 * <p>
 * The Notifier is invoked when one or more WorkItems become available for processing.
 * <p>
 * The type of the Object returned by getParams is determined by the WorkItemType and described below.
 * <p>
 *
 * <table width="100%" border="1"><thead><tr><th>WorkItem Type</th><th>Description</th></tr></thead><tbody>
 *
 *
 * <tr>
 * <td>AGENT_ADDED:</td>
 * <td>
 * When the QMF Console receives the first heartbeat from an Agent, an AGENT_ADDED WorkItem is pushed onto the
 * work-queue.
 * <p>
 * The getParams() method returns a map which contains a reference to the new Console Agent instance. The reference is
 * indexed from the map using the key string "agent". There is no handle associated with this WorkItem.
 * <p>
 * Note: If a new Agent is discovered as a result of the Console findAgent() method, then no AGENT_ADDED WorkItem is 
 * generated for that Agent.
 * <p>
 * Use AgentAddedWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>AGENT_DELETED:</td>
 * <td>
 * When a known Agent stops sending heartbeat messages the Console will time out that Agent. On Agent timeout, an  
 * AGENT_DELETED WorkItem is pushed onto the work-queue.
 * <p>
 * The getParams() method returns a map which contains a reference to the Agent instance that has been deleted.
 * The reference is indexed from the map using the key string "agent". There is no handle associated with this WorkItem.
 * <p>
 * The Console application must release all saved references to the Agent before returning the WorkItem.
 * <p>
 * Use AgentDeletedWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>AGENT_RESTARTED:</td>
 * <td>
 * Sent when the QMF Console detects an Agent was restarted, an AGENT_RESTARTED WorkItem is pushed onto the work-queue.
 * <p>
 * The getParams() method returns a map which contains a reference to the Console Agent instance. The reference
 * is indexed from the map using the key string "agent". There is no handle associated with this WorkItem.
 * <p>
 * Use AgentRestartedWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>AGENT_HEARTBEAT:</td>
 * <td>
 * When the QMF Console receives heartbeats from an Agent, an AGENT_HEARTBEAT WorkItem is pushed onto the work-queue.
 * <p>
 * The getParams() method returns a map which contains a reference to the Console Agent instance. The reference
 * is indexed from the map using the key string "agent". There is no handle associated with this WorkItem.
 * <p>
 * Note: the first heartbeat results in an AGENT_ADDED WorkItem for Agent not an AGENT_HEARTBEAT.
 * <p>
 * Use AgentHeartbeatWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>NEW_PACKAGE:</td><td>TBD</td>
 * </tr>
 *
 *
 * <tr>
 * <td>NEW_CLASS:</td><td>TBD</td>
 * </tr>
 *
 *
 * <tr>
 * <td>OBJECT_UPDATE:</td>
 * <td>
 * The OBJECT_UPDATE WorkItem is generated in response to an asynchronous refresh made by a QmfConsoleData object.
 * <p>
 * The getParams() method will return a QmfConsoleData.
 * <p>
 * The getHandle() method returns the reply handle provided to the refresh() method call.
 * This handle is merely the handle used for the asynchronous response, it is not associated with the QmfConsoleData
 * in any other way.
 * <p>
 * Use ObjectUpdateWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>METHOD_RESPONSE:</td>
 * <td>
 * The METHOD_RESPONSE WorkItem is generated in response to an asynchronous invokeMethod made by a QmfConsoleData object.
 * <p>
 * The getParams() method will return a MethodResult object.
 * <p>
 * The getHandle() method returns the reply handle provided to the refresh() method call.
 * This handle is merely the handle used for the asynchronous response, it is not associated with the QmfConsoleData
 * in any other way.
 * <p>
 * Use MethodResponseWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>EVENT_RECEIVED:</td>
 * <td>
 * When an Agent generates a QmfEvent an EVENT_RECEIVED WorkItem is pushed onto the work-queue.
 * <p>
 * The getParams() method returns a map which contains a reference to the Console Agent instance that generated
 * the Event and a reference to the QmfEvent itself. The Agent reference is indexed from the map using the key string
 * "agent, The QmfEvent reference is indexed from the map using the key string "event". There is no handle associated
 * with this WorkItem.
 * <p>
 * Use EventReceivedWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>SUBSCRIBE_RESPONSE:</td>
 * <td>
 * The SUBSCRIBE_RESPONSE WorkItem returns the result of a subscription request made by this Console. This WorkItem is 
 * generated when the Console's createSubscription() is called in an asychronous manner, rather than pending for the result. 
 * <p>
 * The getParams() method will return an instance of the SubscribeParams class.
 * <p>
 * The SubscriptionId object must be used when the subscription is refreshed or cancelled. It must be passed to the
 * Console's refresh_subscription() and cancelSubscription() methods. The value of the SubscriptionId does not change
 * over the lifetime of the subscription.
 * <p>
 * The console handle will be provided by the Agent on each data indication event that corresponds to this subscription.
 * It should not change for the lifetime of the subscription.
 * <p>
 * The getHandle() method returns the reply handle provided to the createSubscription() method call. This handle is
 * merely the handle used for the asynchronous response, it is not associated with the subscription in any other way.
 * <p>
 * Once a subscription is created, the Agent that maintains the subscription will periodically issue updates for the 
 * subscribed data. This update will contain the current values of the subscribed data, and will appear as the first 
 * SUBSCRIPTION_INDICATION WorkItem for this subscription.
 * <p>
 * Use SubscribeResponseWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>SUBSCRIPTION_INDICATION:</td>
 * <td>
 * The SUBSCRIPTION_INDICATION WorkItem signals the arrival of an update to subscribed data from the Agent. 
 * <p>
 * The getParams() method will return an instance of the SubscribeIndication class. 
 * The getHandle() method returns null.
 * <p>
 * Use SubscriptionIndicationWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>RESUBSCRIBE_RESPONSE:</td>
 * <td>
 * The RESUBSCRIBE_RESPONSE WorkItem is generated in response to a subscription refresh request made by this Console.
 * This WorkItem is generated when the Console's refreshSubscription() is called in an asychronous manner, rather than 
 * pending for the result. 
 * <p>
 * The getParams() method will return an instance of the SubscribeParams class.
 * <p>
 * The getHandle() method returns the reply handle provided to the refreshSubscription() method call. This handle is
 * merely the handle used for the asynchronous response, it is not associated with the subscription in any other way.
 * <p>
 * Use ResubscribeResponseWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>METHOD_CALL:</td>
 * <td>
 * The METHOD_CALL WorkItem describes a method call that must be serviced by the application on behalf of this Agent.
 * <p>
 * The getParams() method will return an instance of the MethodCallParams class.
 * <p>
 * Use MethodCallWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>QUERY:</td>
 * <td>
 * The QUERY WorkItem describes a query that the application must service. The application should call the 
 * queryResponse() method for each object that satisfies the query. When complete, the application must call the 
 * queryComplete() method. If a failure occurs, the application should indicate the error to the agent by calling
 * the query_complete() method with a description of the error.
 * <p>
 * The getParams() method will return an instance of the QmfQuery class.
 * <p>
 * The getHandle() WorkItem method returns the reply handle which should be passed to the Agent's queryResponse()
 * and queryComplete() methods.
 * <p>
 * Use QueryWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>SUBSCRIBE_REQUEST:</td>
 * <td>
 * The SUBSCRIBE_REQUEST WorkItem provides a query that the agent application must periodically publish until the 
 * subscription is cancelled or expires. On receipt of this WorkItem, the application should call the Agent 
 * subscriptionResponse() method to acknowledge the request. On each publish interval, the application should call Agent 
 * subscriptionIndicate(), passing a list of the objects that satisfy the query. The subscription remains in effect until
 * an UNSUBSCRIBE_REQUEST WorkItem for the subscription is received, or the subscription expires.
 * <p>
 * The getParams() method will return an instance of the SubscriptionParams class.
 * <p>
 * The getHandle() WorkItem method returns the reply handle which should be passed to the Agent's 
 * subscriptionResponse() method.
 * <p>
 * Use SubscribeRequestWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>RESUBSCRIBE_REQUEST:</td>
 * <td>
 * The RESUBSCRIBE_REQUEST is sent by a Console to renew an existing subscription. The Console may request a new
 * duration for the subscription, otherwise the previous lifetime interval is repeated.
 * <p>
 * The getParams() method will return an instance of the ResubscribeParams class.
 * <p>
 * The getHandle() WorkItem method returns the reply handle which should be passed to the Agent's 
 * subscriptionResponse() method.
 * <p>
 * Use ResubscribeRequestWorkItem to enable neater access.
 * </td>
 * </tr>
 *
 *
 * <tr>
 * <td>UNSUBSCRIBE_REQUEST:</td>
 * <td>
 * The UNSUBSCRIBE_REQUEST is sent by a Console to terminate an existing subscription. The Agent application should
 * terminate the given subscription if it exists, and cancel sending any further updates against it.
 * <p>
 * The getParams() method will return a String holding the subscriptionId.
 * <p>
 * The getHandle() method returns null.
 * <p>
 * Use UnsubscribeRequestWorkItem to enable neater access.
 * </td>
 * </tr>
 * </table>
 * <p>
 * The following diagram illustrates the QMF2 WorkItem class hierarchy.
 * <p>
 * <img src="doc-files/WorkItem.png"/>
 * @author Fraser Adams
 */

public class WorkItem
{
    /**
     * An Enumeration of the types of WorkItems produced on the Console or Agent.
     */
    public enum WorkItemType
    {
        // Enumeration of the types of WorkItems produced on the Console
        AGENT_ADDED,
        AGENT_DELETED,
        AGENT_RESTARTED,
        AGENT_HEARTBEAT,
        NEW_PACKAGE,
        NEW_CLASS,
        OBJECT_UPDATE,
        EVENT_RECEIVED,
        METHOD_RESPONSE,
        SUBSCRIBE_RESPONSE,
        SUBSCRIPTION_INDICATION,
        RESUBSCRIBE_RESPONSE,
        // Enumeration of the types of WorkItems produced on the Agent
        METHOD_CALL,
        QUERY,
        SUBSCRIBE_REQUEST,
        RESUBSCRIBE_REQUEST,
        UNSUBSCRIBE_REQUEST;
    }

    private final WorkItemType _type;
    private final Handle       _handle;
    private final Object       _params;

    /**
     * Construct a WorkItem
     *
     * @param type the type of WorkItem specified by the WorkItemType enum
     * @param handle the handle passed by async calls - the correlation ID
     * @param params the payload of the WorkItem
     */
    public WorkItem(WorkItemType type, Handle handle, Object params)
    {
        _type = type;
        _handle = handle;
        _params = params;
    }

    /**
     * Return the type of work item.
     * @return the type of work item.
     */
    public final WorkItemType getType()
    {
        return _type;
    }

    /**
     * Return the reply handle for an asynchronous operation, if present.
     * @return the reply handle for an asynchronous operation, if present.
     */
    public final Handle getHandle()
    {
        return _handle;
    }

    /**              
     * Return the payload of the work item.
     * @return the payload of the work item.
     * <p>
     * The type of this object is determined by the type of the workitem as follows:
     * <pre>
     * <b>Console</b>
     * AGENT_ADDED: Map{"agent":Agent}                      - Use AgentAddedWorkItem to enable neater access
     * AGENT_DELETED: Map{"agent":Agent}                    - Use AgentDeletedWorkItem to enable neater access
     * AGENT_RESTARTED: Map{"agent":Agent}                  - Use AgentRestartedWorkItem to enable neater access
     * AGENT_HEARTBEAT: Map{"agent":Agent}                  - Use AgentHeartbeatWorkItem to enable neater access
     * OBJECT_UPDATE: QmfConsoleData                        - Use ObjectUpdateWorkItem to enable neater access
     * METHOD_RESPONSE: MethodResult                        - Use MethodResponseWorkItem to enable neater access
     * EVENT_RECEIVED: Map{"agent":Agent, "event":QmfEvent} - Use EventReceivedWorkItem to enable neater access
     * SUBSCRIBE_RESPONSE: SubscribeParams                  - Use SubscribeResponseWorkItem to enable neater access
     * SUBSCRIPTION_INDICATION: SubscribeIndication         - Use SubscriptionIndicationWorkItem to enable neater access
     * RESUBSCRIBE_RESPONSE: SubscribeParams                - Use ResubscribeResponseWorkItem to enable neater access
     *
     * <b>Agent</b>
     * METHOD_CALL: MethodCallParams                        - Use MethodCallWorkItem to enable neater access
     * QUERY: QmfQuery                                      - Use QueryWorkItem to enable neater access
     * SUBSCRIBE_REQUEST: SubscriptionParams                - Use SubscribeRequestWorkItem to enable neater access
     * RESUBSCRIBE_REQUEST: ResubscribeParams               - Use ResubscribeRequestWorkItem to enable neater access
     * UNSUBSCRIBE_REQUEST: String (subscriptionId)         - Use UnsubscribeRequestWorkItem to enable neater access
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public final <T> T getParams()
    {
        return (T)_params;
    }
}

