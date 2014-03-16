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
 * Subclass of QmfDescribed, which is itself a subclass of QmfData.
 * <p>
 * When representing managed data, a QmfData instance is assigned an object identifier 
 *
 * @author Fraser Adams
 */
public class QmfManaged extends QmfDescribed
{
    private ObjectId _object_id;

    /**
     * The default constructor, initialises the underlying QmfData base class with an empty Map
     */
    protected QmfManaged()
    {
    }

    /**
     * The main constructor, taking a java.util.Map as a parameter. In essence it "deserialises" its state from the Map.
     *
     * @param m the map used to construct the QmfManaged
     */
    public QmfManaged(final Map m)
    {
        super(m);
        _object_id = (m == null) ? null : new ObjectId((Map)m.get("_object_id"));
    }

    /**
     * Returns the ObjectId of this managed object.
     * @return the ObjectId of this managed object.
     */
    public final ObjectId getObjectId()
    {
        return _object_id;
    }

    /**
     * Sets the ObjectId of this managed object.
     * @param object_id the ObjectId of this managed object.
     */
    public final void setObjectId(final ObjectId object_id)
    {
        _object_id = object_id;
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        super.listValues();
        System.out.println("_object_id: " + getObjectId());
    }
}

