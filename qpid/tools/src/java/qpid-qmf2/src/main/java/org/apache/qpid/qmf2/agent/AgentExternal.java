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
package org.apache.qpid.qmf2.agent;

// Misc Imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfCallback;
import org.apache.qpid.qmf2.common.QmfException;

/**
 * The AgentExternal class must be used by those applications that implement the external store model described in 
 * <a href=https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal>QMF2 API Proposal</a>. 
 * <p>
 * The AgentExternal class extends the Agent class by adding interfaces that notify the application when it needs to
 * service a request for management operations from the agent.
 * <p>
 * N.B. The author is not convinced that there is any particular advantage of the AgentExternal model over the
 * basic Agent model and indeed the API forces some constructs that are actually likely to be less efficient, as an
 * example sending a separate queryResponse() for each object forces a look up of a List of QmfAgentData objects
 * keyed by the consoleHandle for each call. There is also the need to separately iterate through the List of
 * QmfAgentData objects thus created to create the mapEncoded list needed for sending via the QMF2 protocol.
 * There are similar inefficiencies imposed in the subscriptionIndicate() method that are not present in the
 * Subscription code implemented in the Agent class for the "Internal Store" Agent model.
 * <p>
 * To be honest the author only bothered to implement AgentExternal for completeness and is unlikely to use it himself.
 *
 * @author Fraser Adams
 */
public final class AgentExternal extends Agent
{
    /**
     * This Map is used to hold query results. This is necessary as the API has each queryResponse() call send
     * back an individual QmfAgentData, so we need to maintain these in a list keyed by the consoleHandle until
     * the queryComplete() gets sent.
     */
    private Map<String, List<QmfAgentData>> _queryResults = new ConcurrentHashMap<String, List<QmfAgentData>>();

    //                                          QMF API Methods
    // ********************************************************************************************************

    /**
     * Constructor that provides defaults for name, domain  and heartbeat interval and takes a Notifier/Listener.
     *
     * @param notifier this may be either a QMF2 API Notifier object OR a QMFEventListener.
     * <p>
     * The latter is an alternative API that avoids the need for an explicit Notifier thread to be created the
     * EventListener is called from the JMS MessageListener thread.
     * <p>
     * This API may be simpler and more convenient than the QMF2 Notifier API for many applications.
     */
    public AgentExternal(final QmfCallback notifier) throws QmfException
    {
        super(null, null, notifier, 30);
    }

    /**
     * Constructor that provides defaults for name and domain and takes a Notifier/Listener
     *
     * @param notifier this may be either a QMF2 API Notifier object OR a QMFEventListener.
     * <p>
     * The latter is an alternative API that avoids the need for an explicit Notifier thread to be created the
     * EventListener is called from the JMS MessageListener thread.
     * <p>
     * This API may be simpler and more convenient than the QMF2 Notifier API for many applications.
     * @param interval is the heartbeat interval in seconds.
     */
    public AgentExternal(final QmfCallback notifier, final int interval) throws QmfException
    {
        super(null, null, notifier, interval);
    }

    /**
     * Main constructor, creates a Agent, but does NOT start it, that requires us to do setConnection()
     *
     * @param name If a name is supplied, it must be unique across all attached to the AMQP bus under the given domain.
     * <p>
     * The name must comprise three parts separated by colons: <pre>&lt;vendor&gt;:&lt;product&gt;[:&lt;instance&gt;]</pre>
     * where the vendor is the Agent vendor name, the product is the Agent product itself and the instance is a UUID
     * representing the running instance.
     * <p>
     * If the instance is not supplied then a random UUID will be generated.
     * @param domain the QMF "domain".
     * <p>
     * A QMF address is composed of two parts - an optional domain string, and a mandatory name string
     * <pre>"qmf.&lt;domain-string&gt;.direct/&lt;name-string&gt;"</pre>
     * The domain string is used to construct the name of the AMQP exchange to which the component's  name string will
     * be bound. If not supplied, the value of the domain defaults to "default".
     * <p>
     * Both Agents and Components must belong to the same domain in order to communicate.
     * @param notifier this may be either a QMF2 API Notifier object OR a QMFEventListener.
     * <p>
     * The latter is an alternative API that avoids the need for an explicit Notifier thread to be created the
     * EventListener is called from the JMS MessageListener thread.
     * <p>
     * This API may be simpler and more convenient than the QMF2 Notifier API for many applications.
     * @param interval is the heartbeat interval in seconds.
     */
    public AgentExternal(final String name, final String domain,
                         final QmfCallback notifier, final int interval) throws QmfException
    {
        super(name, domain, notifier, interval);
    }

    /**
     * We override the base Class addObject() to throw an Exception as addObject() is used to populate the
     * <b>internal</b> store.
     */
    @Override
    public void addObject(final QmfAgentData object) throws QmfException
    {
        throw new QmfException("Cannot call addObject() on AgentExternal as this method is used to populate the internal object store");
    }

    /**
     * Indicate to QMF that the named object is available to be managed. Once this method returns, the agent will
     * service requests from consoles referencing this data.
     *
     * @param objectName the name of the QmfAgentData being managed.
     * @return a new ObjectId based on the objectName passed as a parameter.
     */
    public ObjectId allocObjectId(final String objectName)
    {
        return new ObjectId(getName(), objectName, getEpoch());
    }

    /**
     * Indicate to QMF that the named object is no longer available to be managed.
     *
     * @param objectName the name of the QmfAgentData being managed.
     */
    public void freeObjectId(final String objectName)
    {
        // Null implementation. It's not really clear that there's anything useful that the Agent needs to do here
    }

    /**
     * Send a managed object in reply to a received query. Note that ownership of the object instance is returned to
     * the caller on return from this call.
     *
     * @param handle the handle from the WorkItem.
     * @param object a managed QmfAgentData object.
     */
    public void queryResponse(final Handle handle, final QmfAgentData object)
    {
        String index = handle.getCorrelationId();
        List<QmfAgentData> objects = _queryResults.get(index);
        if (objects == null)
        {
            objects = new ArrayList<QmfAgentData>();
            _queryResults.put(index, objects);
        }
        objects.add(object);
    }

    /**
     * Indicate to the agent that the application has completed processing a query request.
     * Zero or more calls to the queryResponse() method should be invoked before calling query_complete().
     * If the query should fail - for example, due to authentication error - the result should be set to a
     * non-zero error code ?TBD?.
     *
     * @param handle the handle from the WorkItem.
     * @param statusCode if this is non zero it indicates that the query failed.
     */
    public void queryComplete(final Handle handle, final int statusCode)
    {
        String index = handle.getCorrelationId();
        List<QmfAgentData> objects = _queryResults.get(index);
        if (objects != null)
        {
            List<Map> results = new ArrayList<Map>(objects.size());
            for (QmfAgentData object : objects)
            {
                results.add(object.mapEncode());
            }

            // Send the response back to the Console
            queryResponse(handle, results, "_data");
            _queryResults.remove(index);
        }
    }

    /**
     * This has actually been implemented in the base Agent class as it's useful there too.
     *
     * If the subscription request is successful, the Agent application must provide a unique subscriptionId.
     * If replying to a sucessful subscription refresh, the original subscriptionId must be supplied.
     * If the subscription or refresh fails, the subscriptionId should be set to null and error may be set to
     * an application-specific QmfData instance that describes the error.
     * Should a refresh request fail, the consoleHandle may be set to null if unknown.
     *
     * @param handle the handle from the WorkItem
     * @param consoleHandle the console reply handle
     * @param subscriptionId a unique handle for the subscription supplied by the Agent
     * @param lifetime should be set to the duration of the subscription in seconds.
     * @param publishInterval should be set to the time interval in seconds between successive publications
     *          on this subscription.
     * @param error an application-specific QmfData instance that describes the error.
     */
    //public void subscriptionResponse(Handle handle, Handle consoleHandle, String subscriptionId, long lifetime, long     
    //                                 publishInterval, QmfData error)

    /**
     * Send a list of updated subscribed data to the Console.
     *
     * Note that the base Agent class contains a sendSubscriptionIndicate() for data that is already mapEncoded()
     * To be honest AgentExternal Agents that happen to implement the SubscriptionProxy interface which reuses the
     * Internal Agent Subscription code will almost certainly call the base class sendSubscriptionIndicate() as the
     * SubscriptionProxy sendSubscriptionIndicate() has the same signature.
     *
     * @param handle the console reply handle.
     * @param objects a list of subscribed data in QmfAgentData encoded form.
     */
    public void subscriptionIndicate(final Handle handle, final List<QmfAgentData> objects)
    {
        List<Map> results = new ArrayList<Map>(objects.size());
        for (QmfAgentData object : objects)
        {
            results.add(object.mapEncode());
        }
        sendSubscriptionIndicate(handle, results);
    }

    /**
     * Acknowledge a Subscription Cancel WorkItem.
     *
     * @param handle the handle from the WorkItem.
     * @param consoleHandle the console reply handle.
     */
    public void subscriptionCancel(final Handle handle, final String consoleHandle)
    {
        // Null implementation, there isn't really much that needs to be done after cancelling a Subscription
    }
}
