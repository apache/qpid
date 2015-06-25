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

// Misc Imports
import java.util.Map;

/**
 * This class provides a wrapper for QMF Object IDs to enable easier comparisons.
 * <p>
 * QMF Object IDs are Maps and <i>in theory</i> equals should work, but strings in QMF are actually often returned
 * as byte[] due to inconsistent binary and UTF-8 encodings being used and byte[].equals() compares the address not a
 * bytewise comparison.
 * <p>
 * This class creates a String from the internal ObjectId state information to enable easier comparison and rendering.
 *
 * @author Fraser Adams
 */
public final class ObjectId extends QmfData
{
    private final String _agentName;
    private final String _objectName;
    private final long   _agentEpoch;

    /**
     * Create an ObjectId given the ID created via ObjectId.toString().
     * @param oid a string created via ObjectId.toString().
     */
    public ObjectId(String oid)
    {
        String[] split = oid.split("@");

        _agentName  = split.length == 3 ? split[0] : "";
        _agentEpoch = split.length == 3 ? Long.parseLong(split[1]) : 0;
        _objectName = split.length == 3 ? split[2] : "";

        setValue("_agent_name", _agentName);
        setValue("_agent_epoch", _agentEpoch);
        setValue("_object_name", _objectName);
    }

    /**
     * Create an ObjectId given an agentName, objectName and agentEpoch.
     * @param agentName the name of the Agent managing the object.
     * @param objectName the name of the managed object.
     * @param agentEpoch a count used to identify if an Agent has been restarted.
     */
    public ObjectId(String agentName, String objectName, long agentEpoch)
    {
        _agentName = agentName;
        _objectName = objectName;
        _agentEpoch = agentEpoch;
        setValue("_agent_name", _agentName);
        setValue("_object_name", _objectName);
        setValue("_agent_epoch", _agentEpoch);
    }

    /**
     * Create an ObjectId from a Map. In essence it "deserialises" its state from the Map.
     * @param m the Map the Object is retrieving its state from.
     */
    public ObjectId(Map m)
    {
        super(m);
        _agentName = getStringValue("_agent_name");
        _objectName = getStringValue("_object_name");
        _agentEpoch = getLongValue("_agent_epoch");
    }

    /**
     * Create an ObjectId from a QmfData object. In essence it "deserialises" its state from the QmfData object.
     * @param qmfd the QmfData the Object is retrieving its state from.
     */
    public ObjectId(QmfData qmfd)
    {
        this(qmfd.mapEncode());
    }

    /**
     * Returns the name of the Agent managing the object.
     * @return the name of the Agent managing the object.
     */
    public String getAgentName()
    {
        return _agentName;
    }

    /**
     * Returns the name of the managed object.
     * @return the name of the managed object.
     */
    public String getObjectName()
    {
        return _objectName;
    }

    /**
     * Returns the Epoch count.
     * @return the Epoch count.
     */
    public long getAgentEpoch()
    {
        return _agentEpoch;
    }

    /**
     * Compares two ObjectId objects for equality.
     * @param rhs the right hands side ObjectId in the comparison.
     * @return true if the two ObjectId objects are equal otherwise returns false.
     */
    @Override
    public boolean equals(Object rhs)
    {
        if (rhs instanceof ObjectId)
        {
            return toString().equals(rhs.toString());
        }
        return false;
    }

    /**
     * Returns the ObjectId hashCode.
     * @return the ObjectId hashCode.
     */
    @Override
    public int hashCode()
    {
        return toString().hashCode();
    }

    /**
     * Returns a String representation of the ObjectId.
     * @return a String representation of the ObjectId.
     */
    @Override
    public String toString()
    {
        return _agentName + "@" +  _agentEpoch + "@" + _objectName;
    }
}

