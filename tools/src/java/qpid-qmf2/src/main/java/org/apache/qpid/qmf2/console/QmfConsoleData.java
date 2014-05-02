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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfManaged;
import org.apache.qpid.qmf2.common.SchemaClassId;

/**
 * Subclass of QmfManaged to provide a Console specific representation of management data.
 * <p>
 * The Console application represents a managed data object by the QmfConsoleData class. The Console has "read only"
 * access to the data values in the data object via this class. The Console can also invoke the methods defined by
 * the object via this class.
 * <p>
 * The actual data stored in this object is cached from the Agent. In order to update the cached values,
 * the Console invokes the instance's refresh() method.
 * <p>
 * Note that the refresh() and invokeMethod() methods require communication with the remote Agent. As such, they
 * may block. For these two methods, the Console has the option of blocking in the call until the call completes.
 * Optionally, the Console can receive a notification asynchronously when the operation is complete.
 *
 * @author Fraser Adams
 */
public class QmfConsoleData extends QmfManaged
{
    private final Agent _agent;
    private long _updateTimestamp;
    private long _createTimestamp;
    private long _deleteTimestamp;

    /**
     * The main constructor, taking a java.util.Map as a parameter. In essence it "deserialises" its state from the Map.
     *
     * @param m the map used to construct the SchemaClass.
     * @param a the Agent that manages this object.
     */
    public QmfConsoleData(final Map m, final Agent a)
    {
        super(m);
        long currentTime = System.currentTimeMillis()*1000000l;
        _updateTimestamp = m.containsKey("_update_ts") ? getLong(m.get("_update_ts")) : currentTime;
        _createTimestamp = m.containsKey("_create_ts") ? getLong(m.get("_create_ts")) : currentTime;
        _deleteTimestamp = m.containsKey("_delete_ts") ? getLong(m.get("_delete_ts")) : currentTime;
        _agent = a;
    }

    /**
     * Sets the state of the QmfConsoleData, used as an assignment operator.
     * 
     * @param m the Map used to initialise the QmfConsoleData
     */
    @SuppressWarnings("unchecked")
    public void initialise(final Map m)
    {
        Map<String, Object> values = (Map<String, Object>)m.get("_values");
        _values = (values == null) ? m : values;

        Map<String, String> subtypes = (Map<String, String>)m.get("_subtypes");
        _subtypes = subtypes;

        setSchemaClassId(new SchemaClassId((Map)m.get("_schema_id")));
        setObjectId(new ObjectId((Map)m.get("_object_id")));

        long currentTime = System.currentTimeMillis()*1000000l;
        _updateTimestamp = m.containsKey("_update_ts") ? getLong(m.get("_update_ts")) : currentTime;
        _createTimestamp = m.containsKey("_create_ts") ? getLong(m.get("_create_ts")) : currentTime;
        _deleteTimestamp = m.containsKey("_delete_ts") ? getLong(m.get("_delete_ts")) : currentTime;
    }

    /**
     * Sets the state of the QmfConsoleData, used as an assignment operator.
     * 
     * @param rhs the QmfConsoleData used to initialise the QmfConsoleData
     */
    public void initialise(final QmfConsoleData rhs)
    {
        _values = rhs._values;
        _subtypes = rhs._subtypes;
        setSchemaClassId(rhs.getSchemaClassId());
        setObjectId(rhs.getObjectId());
        _updateTimestamp = rhs._updateTimestamp;
        _createTimestamp = rhs._createTimestamp;
        _deleteTimestamp = rhs._deleteTimestamp;
    }

    /**
     * Return a list of timestamps describing the lifecycle of the object.
     * @return a list of timestamps describing the lifecycle of the object.
     * <p>
     * All timestamps are represented by the AMQP timestamp type recorded in nanoseconds since the epoch.
     * <pre>
     * [0] = time of last update from Agent,
     * [1] = creation timestamp
     * [2] = deletion timestamp, or zero if not deleted.
     * </pre>
     */
    public final long[] getTimestamps()
    {
        long[] timestamps = {_updateTimestamp, _createTimestamp, _deleteTimestamp};
        return timestamps;
    }

    /**
     * Return the creation timestamp.
     * @return the creation timestamp. Timestamps are recorded in nanoseconds since the epoch.
     */
    public final long getCreateTime()
    {
        return _createTimestamp;
    }

    /**
     * Return the update timestamp.
     * @return the update timestamp. Timestamps are recorded in nanoseconds since the epoch.
     */
    public final long getUpdateTime()
    {
        return _updateTimestamp;
    }

    /**
     * Return the deletion timestamp, or zero if not deleted.
     * @return the deletion timestamp, or zero if not deleted. Timestamps are recorded in nanoseconds since the epoch.
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
     * Request that the Agent updates the value of this object's contents.
     */    
    public final void refresh() throws QmfException
    {
        refresh(-1);
    }

    /**
     * Request that the Agent updates the value of this object's contents.
     *
     * @param timeout the maximum time in seconds to wait for a response, overrides default replyTimeout.
     */    
    public final void refresh(final int timeout) throws QmfException
    {
        if (_agent == null)
        {
            throw new QmfException("QmfConsoleData.refresh() called with null Agent");
        }
        QmfConsoleData newContents = _agent.refresh(getObjectId(), null, timeout);
        if (newContents == null)
        {
            _deleteTimestamp = System.currentTimeMillis()*1000000l;
        }
        else
        {
            // Save the original values of create and delete timestamps as the ManagementAgent doesn't return 
            // these on a call to getObjects(ObjectId);
            long createTimestamp = _createTimestamp;
            long deleteTimestamp = _deleteTimestamp;
            initialise(newContents);
            _createTimestamp = createTimestamp;
            _deleteTimestamp = deleteTimestamp;
        }
    }

    /**
     * Request that the Agent updates the value of this object's contents asynchronously.
     *
     * @param replyHandle the correlation handle used to tie asynchronous refresh requests with responses.
     */    
    public final void refresh(final String replyHandle) throws QmfException
    {
        if (_agent == null)
        {
            throw new QmfException("QmfConsoleData.refresh() called with null Agent");
        }
        _agent.refresh(getObjectId(), replyHandle, -1);
    }

    /**
     * Invoke the named method on this instance.
     *
     * @param name name of the method to invoke.
     * @param inArgs inArgs an unordered set of key/value pairs comprising the method arguments.
     * @return the MethodResult.
     */    
    public final MethodResult invokeMethod(final String name, final QmfData inArgs) throws QmfException
    {
        if (_agent == null)
        {
            throw new QmfException("QmfConsoleData.invokeMethod() called with null Agent");
        }
        return _agent.invokeMethod(getObjectId(), name, inArgs, -1);
    }

    /**
     * Invoke the named method on this instance.
     *
     * @param name name of the method to invoke.
     * @param inArgs inArgs an unordered set of key/value pairs comprising the method arguments.
     * @param timeout the maximum time in seconds to wait for a response, overrides default replyTimeout.
     * @return the MethodResult.
     */    
    public final MethodResult invokeMethod(final String name, final QmfData inArgs, final int timeout) throws QmfException
    {
        if (_agent == null)
        {
            throw new QmfException("QmfConsoleData.invokeMethod() called with null Agent");
        }
        return _agent.invokeMethod(getObjectId(), name, inArgs, timeout);
    }

    /**
     * Invoke the named method asynchronously on this instance.
     *
     * @param name name of the method to invoke.
     * @param inArgs inArgs an unordered set of key/value pairs comprising the method arguments.
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses.
     */    
    public final void invokeMethod(final String name, final QmfData inArgs, final String replyHandle) throws QmfException
    {
        if (_agent == null)
        {
            throw new QmfException("QmfConsoleData.invokeMethod() called with null Agent");
        }
        _agent.invokeMethod(getObjectId(), name, inArgs, replyHandle);
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        super.listValues();
        System.out.println("_create_ts: " + new Date(getCreateTime()/1000000l));
        System.out.println("_update_ts: " + new Date(getUpdateTime()/1000000l));
        System.out.println("_delete_ts: " + new Date(getDeleteTime()/1000000l));
    }
}

