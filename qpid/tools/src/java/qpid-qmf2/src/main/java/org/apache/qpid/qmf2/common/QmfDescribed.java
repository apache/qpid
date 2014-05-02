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
import java.util.UUID;

/**
 * Subclass of QmfData.
 * <p>
 * When representing formally defined data, a QmfData instance is assigned a Schema.
 *
 * @author Fraser Adams
 */
public class QmfDescribed extends QmfData
{
    private SchemaClassId _schema_id;

    /**
     * The default constructor, initialises the underlying QmfData base class with an empty Map
     */
    protected QmfDescribed()
    {
    }

    /**
     * The main constructor, taking a java.util.Map as a parameter. In essence it "deserialises" its state from the Map.
     *
     * @param m the map used to construct the QmfDescribed
     */
    public QmfDescribed(final Map m)
    {
        super(m);
        _schema_id = (m == null) ? null : new SchemaClassId((Map)m.get("_schema_id"));
    }

    /**
     * Returns the SchemaClassId describing this object.
     * @return the SchemaClassId describing this object.
     */
    public final SchemaClassId getSchemaClassId()
    {
        return _schema_id;
    }

    /**
     * Sets the SchemaClassId describing this object.
     * @param schema_id the SchemaClassId describing this object.
     */
    public final void setSchemaClassId(final SchemaClassId schema_id)
    {
        _schema_id = schema_id;
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        super.listValues();
        _schema_id.listValues();
    }
}

