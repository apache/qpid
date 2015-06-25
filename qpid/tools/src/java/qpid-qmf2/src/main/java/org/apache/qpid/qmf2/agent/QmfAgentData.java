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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfManaged;
import org.apache.qpid.qmf2.common.SchemaObjectClass;

/**
 * The Agent manages the data it represents by the QmfAgentData class - a derivative of the QmfData class.
 * <p>
 * The Agent is responsible for managing the values of the properties within the object, as well as servicing
 * the object's method calls. Unlike the Console, the Agent has full control of the state of the object.
 * <p>
 * In most cases, for efficiency, it is expected that Agents would <i>actually</i> manage objects that are subclasses of 
 * QmfAgentData and maintain subclass specific properties as primitives, only actually explicitly setting the
 * underlying Map properties via setValue() etc. when the object needs to be "serialised". This would most
 * obviously be done by extending the mapEncode() method (noting that it's important to call QmfAgentData's mapEncode()
 * first via super.mapEncode(); as this will set the state of the underlying QmfData).
 * <p>
 * This class provides a number of methods aren't in the QMF2 API per se, but they are used to manage the association
 * between a managed object and any subscriptions that might be interested in it.
 * <p>
 * The diagram below shows the relationship between the Subscription and QmfAgentData.
 * <p>
 * <img alt="" src="doc-files/Subscriptions.png">
 * <p>
 * In particular the QmfAgentData maintains references to active subscriptions to allow agents to asynchronously
 * push data to subscribing Consoles immediately that data becomes available.
 * <p>
 * The update() method indicates that the object's state has changed and the publish() method <b>immediately</b> sends
 * the new state to any subscription.
 * <p>
 * The original intention was to "auto update" by calling these from the setValue() method. Upon reflection this
 * seems a bad idea, as in many cases there may be several properties that an Agent may wish to change which would
 * lead to unnecessary calls to currentTimeMillis(), but also as theSubscription update is run via a TimerTask it is
 * possible that an update indication could get sent part way through setting an object's overall state.
 * Similarly calling the publish() method directly from setValue() would force an update indication on partial changes
 * of state, which is generally not the desired behaviour.
 * @author Fraser Adams
 */
public class QmfAgentData extends QmfManaged implements Comparable<QmfAgentData>
{
    private long _updateTimestamp;
    private long _createTimestamp;
    private long _deleteTimestamp;
    private String _compareKey = null;

    /**
     * This Map is used to look up Subscriptions that are interested in this data by SubscriptionId
     */
    private Map<String, Subscription> _subscriptions = new ConcurrentHashMap<String, Subscription>();

    /**
     * Construct a QmfAgentData object of the type described by the given SchemaObjectClass.
     *
     * @param schema the schema describing the type of this QmfAgentData object.
     */
    public QmfAgentData(final SchemaObjectClass schema)
    {
        long currentTime = System.currentTimeMillis()*1000000l;
        _updateTimestamp = currentTime;
        _createTimestamp = currentTime;
        _deleteTimestamp = 0;
        setSchemaClassId(schema.getClassId());
    }

    /**
     * Return the creation timestamp.
     * @return the creation timestamp. Timestamps are recorded in nanoseconds since the epoch
     */
    public final long getCreateTime()
    {
        return _createTimestamp;
    }

    /**
     * Return the update timestamp.
     * @return the update timestamp. Timestamps are recorded in nanoseconds since the epoch
     */
    public final long getUpdateTime()
    {
        return _updateTimestamp;
    }

    /**
     * Return the deletion timestamp.
     * @return the deletion timestamp, or zero if not deleted. Timestamps are recorded in nanoseconds since the epoch
     */
    public final long getDeleteTime()
    {
        return _deleteTimestamp;
    }

    /**
     * Return true if deletion timestamp not zero.
     * @return true if deletion timestamp not zero.
     */
    public final boolean isDeleted()
    {
        return getDeleteTime() != 0;
    }

    /**
     * Mark the object as deleted by setting the deletion timestamp to the current time.
     * <p>
     * This method alse publishes the deleted object to any listening Subscription then removes references to the
     * Subscription.
     * <p>
     * When this method returns the object should be ready for reaping.
     */
    public final void destroy()
    {
        _deleteTimestamp = System.currentTimeMillis()*1000000l;
        _updateTimestamp = System.currentTimeMillis()*1000000l;
        publish();
        _subscriptions.clear();
    }

    /**
     * Add the delta to the property.
     *
     * @param name the name of the property being modified.
     * @param delta the value being added to the property.
     */
    public final synchronized void incValue(final String name, final long delta)
    {
        long value = getLongValue(name);
        value += delta;
        setValue(name, value);
    }

    /**
     * Add the delta to the property.
     *
     * @param name the name of the property being modified.
     * @param delta the value being added to the property.
     */
    public final synchronized void incValue(final String name, final double delta)
    {
        double value = getDoubleValue(name);
        value += delta;
        setValue(name, value);
    }

    /**
     * Subtract the delta from the property.
     *
     * @param name the name of the property being modified.
     * @param delta the value being subtracted from the property.
     */
    public final synchronized void decValue(final String name, final long delta)
    {
        long value = getLongValue(name);
        value -= delta;
        setValue(name, value);
    }

    /**
     * Subtract the delta from the property.
     *
     * @param name the name of the property being modified.
     * @param delta the value being subtracted from the property.
     */
    public final synchronized void decValue(final String name, final double delta)
    {
        double value = getDoubleValue(name);
        value -= delta;
        setValue(name, value);
    }

    // The following methods aren't in the QMF2 API per se, but they are used to manage the association between
    // a managed object and any subscriptions that might be interested in it.

    /**
     * Return the Subscription with the specified ID.
     * @return the Subscription with the specified ID.
     */
    public final Subscription getSubscription(final String subscriptionId)
    {
        return _subscriptions.get(subscriptionId);
    }

    /**
     * Add a new Subscription reference.
     * @param subscriptionId the ID of the Subscription being added.
     * @param subscription the Subscription being added.
     */
    public final void addSubscription(final String subscriptionId, final Subscription subscription)
    {
        _subscriptions.put(subscriptionId, subscription);
    }

    /**
     * Remove a Subscription reference.
     * @param subscriptionId the ID of the Subscription being removed.
     */
    public final void removeSubscription(final String subscriptionId)
    {
        _subscriptions.remove(subscriptionId);
    }


    /**
     * Set the _updateTimestamp to indicate (particularly to subscriptions) that the managed object has changed.
     * <p>
     * The update() method indicates that the object's state has changed and the publish() method <b>immediately</b> sends
     * the new state to any subscription.
     * <p>
     * The original intention was to "auto update" by calling these from the setValue() method. Upon reflection this
     * seems a bad idea, as in many cases there may be several properties that an Agent may wish to change which would
     * lead to unnecessary calls to currentTimeMillis(), but also as the Subscription update is run via a TimerTask it
     * is possible that an update indication could get sent part way through setting an object's overall state.
     * Similarly calling the publish() method directly from setValue() would force an update indication on partial
     * changes of state, which is generally not the desired behaviour.
     */
    public final void update()
    {
        _updateTimestamp = System.currentTimeMillis()*1000000l;
    }

    /**
     * Iterate through any Subscriptions associated with this Object and force them to republish the Object's new state.
     * <p>
     * The update() method indicates that the object's state has changed and the publish() method <b>immediately</b> sends
     * the new state to any subscription.
     * <p>
     * The original intention was to "auto update" by calling these from the setValue() method. Upon reflection this
     * seems a bad idea, as in many cases there may be several properties that an Agent may wish to change which would
     * lead to unnecessary calls to currentTimeMillis(), but also as the Subscription update is run via a TimerTask it
     * is possible that an update indication could get sent part way through setting an object's overall state.
     * Similarly calling the publish() method directly from setValue() would force an update indication on partial
     * changes of state, which is generally not the desired behaviour.
     */
    public final void publish()
    {
        update();
        if (getObjectId() == null)
        { // If ObjectId is null the Object isn't yet Managed to we can't publish
            return;
        }

        List<Map> results = new ArrayList<Map>();
        results.add(mapEncode());
        for (Map.Entry<String, Subscription> entry : _subscriptions.entrySet())
        {
            Subscription subscription = entry.getValue();
            subscription.publish(results);
        }
    }

    /**
     * Return the underlying map.
     * <p>
     * In most cases, for efficiency, it is expected that Agents would <i>actually</i> manage objects that are
     * subclasses of QmfAgentData and maintain subclass specific properties as primitives, only actually explicitly
     * setting the underlying Map properties via setValue() etc. when the object needs to be "serialised". This would
     * most obviously be done by extending the mapEncode() method (noting that it's important to call QmfAgentData's
     * mapEncode() first via super.mapEncode(); as this will set the state of the underlying QmfData).
     *
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("_values", super.mapEncode());
        if (_subtypes != null)
        {
            map.put("_subtypes", _subtypes);
        }
        map.put("_schema_id", getSchemaClassId().mapEncode());
        map.put("_object_id", getObjectId().mapEncode());
        map.put("_update_ts", _updateTimestamp);
        map.put("_create_ts", _createTimestamp);
        map.put("_delete_ts", _deleteTimestamp);
        return map;
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        super.listValues();
        System.out.println("QmfAgentData:");
        System.out.println("create timestamp: " + new Date(getCreateTime()/1000000l));
        System.out.println("update timestamp: " + new Date(getUpdateTime()/1000000l));
        System.out.println("delete timestamp: " + new Date(getDeleteTime()/1000000l));
    }

    // The following methods allow instances of QmfAgentData to be compared with each other and sorted.
    // N.B. This behaviour is not part of the specified QmfAgentData, but it's quite useful for some Agents.

    /**
     * Set the key String to be used for comparing two QmfAgentData instances. This is primarily used by the Agent
     * to allow it to order Query results (e.g. for getObjects()).
     * @param compareKey the String that we wish to use as a compare key.
     */
    public void setCompareKey(String compareKey)
    {
        _compareKey = compareKey;
    }

    /**
     * If a compare key has been set then the QmfAgentData is sortable.
     * @return true if a compare key has been set and the QmfAgentData is sortable otherwise return false.
     */
    public boolean isSortable()
    {
        return _compareKey != null;
    }

    /**
     * Compare the compare key of this QmfAgentData with the specified other QmfAgentData.
     * Compares the compare keys (which are Strings) lexicographically. The comparison is based on the Unicode
     * value of each character in the strings.
     * @param rhs the String to be compared.
     * @return the value 0 if the argument string is equal to this string; a value less than 0 if this string is 
     * lexicographically less than the string argument; and a value greater than 0 if this string is lexicographically 
     * greater than the string argument.
     */
    public int compareTo(QmfAgentData rhs)
    { 
        if (_compareKey == null)
        {
            return 0;
        }
        else
        {
            return this._compareKey.compareTo(rhs._compareKey);
        }
    }
}

