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
package org.apache.qpid.qmf2.console;

// Misc Imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.SchemaClass;
import org.apache.qpid.qmf2.common.SchemaClassId;

/**
 * Local representation (proxy) of a remote Agent.
 * <p>
 * This class holds some state that relates to the Agent and in addition some methods may be called on the agent.
 * destroy(), invokeMethod() and refresh() are actually called by a proxy class AgentProxy. AgentProxy is actually
 * an interface that is implemented by Console (as that's where all the JMS stuff is), we use this approach to 
 * avoid introducing a circular dependency between Agent and Console.
 * <p>
 * The Console application maintains a list of all known remote Agents.
 * Each Agent is represented by an instance of the Agent class:
 * <p>
 * The following diagram illustrates the interactions between the Console, AgentProxy and the client side Agent
 * representation.
 * <p>
 * <img src="doc-files/Subscriptions.png"/>
 * @author Fraser Adams
 */
public final class Agent extends QmfData
{
    private AgentProxy                      _proxy;
    private List<String>                    _packages = new ArrayList<String>();
    private Map<SchemaClassId, SchemaClass> _schemaCache = new ConcurrentHashMap<SchemaClassId, SchemaClass>();
    private long                            _epoch;
    private long                            _heartbeatInterval;
    private long                            _timestamp;
    private boolean                         _eventsEnabled = true;
    private boolean                         _isActive = true;

    /**
     * The main constructor, taking a java.util.Map as a parameter. In essence it "deserialises" its state from the Map.
     *
     * @param m the map used to construct the SchemaClass
     * @param p the AgentProxy instance that implements some of the concrete behaviour of the local Agent representation.
     */
    public Agent(final Map m, final AgentProxy p)
    {
        super(m);
        // Populate attributes translating any old style keys if necessary.
        _epoch = hasValue("_epoch") ? getLongValue("_epoch") : getLongValue("epoch");
        _heartbeatInterval = hasValue("_heartbeat_interval") ? getLongValue("_heartbeat_interval") : 
                                                               getLongValue("heartbeat_interval");
        _timestamp = hasValue("_timestamp") ? getLongValue("_timestamp") : getLongValue("timestamp");
        _proxy = p;
    }

    /**
     * Sets the state of the Agent, used as an assignment operator.
     * 
     * @param m the Map used to initialise the Agent.
     */
    @SuppressWarnings("unchecked")
    public void initialise(final Map m)
    {        
        Map<String, Object> values = (Map<String, Object>)m.get("_values");
        _values = (values == null) ? m : values;

        // Populate attributes translating any old style keys if necessary.
        _epoch = hasValue("_epoch") ? getLongValue("_epoch") : getLongValue("epoch");
        _heartbeatInterval = hasValue("_heartbeat_interval") ? getLongValue("_heartbeat_interval") : 
                                                               getLongValue("heartbeat_interval");
        _timestamp = hasValue("_timestamp") ? getLongValue("_timestamp") : getLongValue("timestamp");
    }

    /**
     * Return whether or not events are enabled for this Agent.
     * @return a boolean indication of whether or not events are enabled for this Agent.
     */
    public boolean eventsEnabled()
    {
        return _eventsEnabled;
    }

    /**
     * Deactivated this Agent. Called by the Console when the Agent times out.
     */
    public void deactivate()
    {
        _isActive = false;
    }

    /**
     * Return the Agent instance name.
     * @return the Agent instance name.
     */
    public String getInstance()
    {
        return getStringValue("_instance");
    }

    /**
     * Return the identifying name string of the Agent. 
     * @return the identifying name string of the Agent. This name is used to send AMQP messages directly to this agent.
     */
    public String getName()
    {
        return getStringValue("_name");
    }

    /**
     * Return the product name string of the Agent.
     * @return the product name string of the Agent.
     */
    public String getProduct()
    {
        return getStringValue("_product");
    }

    /**
     * Return the Agent vendor name.
     * @return the Agent vendor name.
     */
    public String getVendor()
    {
        return getStringValue("_vendor");
    }

    /**
     * Return the Epoch stamp.
     * @return the Epoch stamp, used to determine if an Agent has been restarted.
     */
    public long getEpoch()
    {
        return _epoch;
    }

    /**
     * Set the Epoch stamp.
     * @param epoch the new Epoch stamp, used to indicate that an Agent has been restarted.
     */
    public void setEpoch(long epoch)
    {
        _epoch = epoch;
    }

    /**
     * Return the time that the Agent waits between sending hearbeat messages.
     * @return the time that the Agent waits between sending hearbeat messages.
     */
    public long getHeartbeatInterval()
    {
        return _heartbeatInterval;
    }

    /**
     * Return the timestamp of the Agent's last update.
     * @return the timestamp of the Agent's last update.
     */
    public long getTimestamp()
    {
        return _timestamp;
    }

    /**
     * Return true if the agent is alive.
     * @return true if the agent is alive (heartbeats have not timed out).
     */
    public boolean isActive()
    {
        return _isActive;
    }

    /**
     * Request that the Agent updates the value of this object's contents.
     *
     * @param objectId the ObjectId being queried for..
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses.
     * @param timeout the maximum time to wait for a response, overrides default replyTimeout.
     * @return the refreshed object.
     */    
    public QmfConsoleData refresh(final ObjectId objectId, final String replyHandle, final int timeout) throws QmfException
    {
        if (isActive())
        {
            return _proxy.refresh(this, objectId, replyHandle, timeout);
        }
        else
        {
            throw new QmfException("Agent.refresh() called from deactivated Agent");
        }
    }

    /**
     * Helper method to create a Map containing a QMF method request.
     *
     * @param objectId the objectId of the remote object.
     * @param name the remote method name.
     * @param inArgs the formal parameters of the remote method name.
     * @return a Map containing a QMF method request.
     */
    private Map<String, Object> createRequest(final ObjectId objectId, final String name, final QmfData inArgs)
    {
        // Default sizes for HashMap should be fine for request
        Map<String, Object> request = new HashMap<String, Object>();
        if (objectId != null)
        {
            request.put("_object_id", objectId.mapEncode());
        }
        request.put("_method_name", name);
        if (inArgs != null)
        {
            request.put("_arguments", inArgs.mapEncode());
            if (inArgs.getSubtypes() != null)
            {
                request.put("_subtypes", inArgs.getSubtypes());
            }
        }
        return request;
    }

    /**
     * Sends a method request to the Agent. Delegates to the AgentProxy to actually send the method as it's the
     * AgentProxy that knows about connections, sessions and messages.
     *
     * @param objectId the objectId of the remote object.
     * @param name the remote method name.
     * @param inArgs the formal parameters of the remote method name.
     * @param timeout the maximum time to wait for a response, overrides default replyTimeout.
     * @return the MethodResult.
     */
    protected MethodResult invokeMethod(final ObjectId objectId, final String name,
                                        final QmfData inArgs, final int timeout) throws QmfException
    {
        if (isActive())
        {
            return _proxy.invokeMethod(this, createRequest(objectId, name, inArgs), null, timeout);
        }
        else
        {
            throw new QmfException("Agent.invokeMethod() called from deactivated Agent");
        }
    }

    /**
     * Sends an asynchronous method request to the Agent. Delegates to the AgentProxy to actually send the method as
     * it's the AgentProxy that knows about connections, sessions and messages.
     *
     * @param objectId the objectId of the remote object.
     * @param name the remote method name.
     * @param inArgs the formal parameters of the remote method name.
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses.
     */
    protected void invokeMethod(final ObjectId objectId, final String name,
                                final QmfData inArgs, final String replyHandle) throws QmfException
    {
        if (isActive())
        {
            _proxy.invokeMethod(this, createRequest(objectId, name, inArgs), replyHandle, -1);
        }
        else
        {
            throw new QmfException("Agent.invokeMethod() called from deactivated Agent");
        }
    }

    /**
     * Sends a method request to the Agent. Delegates to the AgentProxy to actually send the method as it's the
     * AgentProxy that knows about connections, sessions and messages.
     *
     * @param name the remote method name.
     * @param inArgs the formal parameters of the remote method name.
     * @return the MethodResult.
     */
    public MethodResult invokeMethod(final String name, final QmfData inArgs) throws QmfException
    {
        return invokeMethod(null, name, inArgs, -1);
    }

    /**
     * Sends a method request to the Agent. Delegates to the AgentProxy to actually send the method as it's the
     * AgentProxy that knows about connections, sessions and messages.
     *
     * @param name the remote method name.
     * @param inArgs the formal parameters of the remote method name.
     * @param timeout the maximum time to wait for a response, overrides default replyTimeout.
     * @return the MethodResult.
     */
    public MethodResult invokeMethod(final String name, final QmfData inArgs, final int timeout) throws QmfException
    {
        return invokeMethod(null, name, inArgs, timeout);
    }

    /**
     * Sends a method request to the Agent. Delegates to the AgentProxy to actually send the method as it's the
     * AgentProxy that knows about connections, sessions and messages.
     *
     * @param name the remote method name.
     * @param inArgs the formal parameters of the remote method name.
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses.
     */
    public void invokeMethod(final String name, final QmfData inArgs, final String replyHandle) throws QmfException
    {
        invokeMethod(null, name, inArgs, replyHandle);
    }

    /**
     * Remove a Subscription. Delegates to the AgentProxy to actually remove the Subscription as it's the AgentProxy
     * that really knows about subscriptions.
     *
     * @param subscription the SubscriptionManager that we wish to remove.
     */
    public void removeSubscription(final SubscriptionManager subscription)
    {
        _proxy.removeSubscription(subscription);
    }

    /**
     * Allows reception of events from this agent.
     */
    public void enableEvents()
    {
        _eventsEnabled = true;
    }

    /**
     * Prevents reception of events from this agent.
     */
    public void disableEvents()
    {
        _eventsEnabled = false;
    }

    /**
     * Releases this Agent instance. Once called, the Console application should not reference this instance again.
     */
    public void destroy()
    {
        _timestamp = 0;
        _proxy.destroy(this);
    }

    /**
     * Clears the internally cached schema. Generally done when we wich to refresh the schema information from the
     * remote Agent.
     */
    public void clearSchemaCache()
    {
        _schemaCache.clear();
        _packages.clear();
    }

    /**
     * Stores the schema and package information obtained by querying the remote Agent.
     *
     * @param classes the list of SchemaClassIds obtained by querying the remote Agent.
     */
    public void setClasses(final List<SchemaClassId> classes)
    {
        if (classes == null)
        {
            clearSchemaCache();
            return;
        }

        for (SchemaClassId classId : classes)
        {
            _schemaCache.put(classId, SchemaClass.EMPTY_SCHEMA);
            if (!_packages.contains(classId.getPackageName()))
            {
                _packages.add(classId.getPackageName());
            }
        }
    }

    /**
     * Return the list of SchemaClassIds associated with this Agent.
     * @return the list of SchemaClassIds associated with this Agent.
     */
    public List<SchemaClassId> getClasses()
    {
        if (_schemaCache.size() == 0)
        {
            return Collections.emptyList();
        }
        return new ArrayList<SchemaClassId>(_schemaCache.keySet());
    }

    /**
     * Return the list of packages associated with this Agent.
     * @return the list of packages associated with this Agent.
     */
    public List<String> getPackages()
    {
        return _packages;
    }

    /**
     * Return the SchemaClass associated with this Agent.
     * @return the list of SchemaClass associated with this Agent.
     * <p>
     * I <i>believe</i> that there should only be one entry in the list returned when looking up a specific chema by classId.
     */
    public List<SchemaClass> getSchema(final SchemaClassId classId)
    {
        SchemaClass schema = _schemaCache.get(classId);
        if (schema == SchemaClass.EMPTY_SCHEMA)
        {
            return Collections.emptyList();
        }
        
        List<SchemaClass> results = new ArrayList<SchemaClass>();
        results.add(schema);
        return results;
    }

    /**
     * Set a schema keyed by SchemaClassId.
     *
     * @param classId the SchemaClassId indexing the particular schema.
     * @param schemaList the schema being indexed.
     * <p>
     * I <i>believe</i> that there should only be one entry in the list returned when looking up a specific chema by classId.
     */
    public void setSchema(final SchemaClassId classId, final List<SchemaClass> schemaList)
    {
        if (schemaList == null || schemaList.size() == 0)
        {
            _schemaCache.put(classId, SchemaClass.EMPTY_SCHEMA);
        }
        else
        {
            // I believe that there should only be one entry in the list returned when looking up
            // a specific chema by classId
            _schemaCache.put(classId, schemaList.get(0));
        }
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        super.listValues();
        System.out.println("Agent:");
        System.out.println("instance: " + getInstance());
        System.out.println("name: " + getName());
        System.out.println("product: " + getProduct());
        System.out.println("vendor: " + getVendor());
        System.out.println("epoch: " + getEpoch());
        System.out.println("heartbeatInterval: " + getHeartbeatInterval());
        System.out.println("timestamp: " + new Date(getTimestamp()/1000000l));
    }
}

