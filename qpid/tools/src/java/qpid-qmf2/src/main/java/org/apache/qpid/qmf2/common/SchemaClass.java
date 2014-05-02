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
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

/**
 * Subclass of QmfData
 * <p>
 * When representing formally defined data, a QmfData instance is assigned a Schema.
 * <p>
 * The following diagram illustrates the QMF2 Schema class hierarchy.
 * <p>
 * <img src="doc-files/Schema.png"/>
 *
 * @author Fraser Adams
 */
public class SchemaClass extends QmfData
{
    public static final SchemaClass EMPTY_SCHEMA = new SchemaClass();

    private SchemaClassId _classId;

    /**
     * The default constructor, initialises the underlying QmfData base class with an empty Map
     */
    protected SchemaClass()
    {
    }

    /**
     * The main constructor, taking a java.util.Map as a parameter.
     *
     * @param m the map used to construct the SchemaClass
     */
    public SchemaClass(final Map m)
    {
        super(m);
        _classId = new SchemaClassId((Map)getValue("_schema_id"));
    }

    /**
     * Return the SchemaClassId that identifies this Schema instance.
     * @return the SchemaClassId that identifies this Schema instance.
     */
    public final SchemaClassId getClassId()
    {
        if (_classId.getHashString() == null)
        {
            _classId = new SchemaClassId(_classId.getPackageName(),
                                         _classId.getClassName(),
                                         _classId.getType(),
                                         generateHash());
        }
        return _classId;
    }

    /**
     * Set the SchemaClassId that identifies this Schema instance.
     * @param cid the SchemaClassId that identifies this Schema instance.
     */
    public final void setClassId(final SchemaClassId cid)
    {
        _classId = cid;
    }

    /**
     * Return a hash generated over the body of the schema, and return a  representation of the hash
     * @return a hash generated over the body of the schema, and return a  representation of the hash
     */
    public final UUID generateHash()
    {
        try
        {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            updateHash(md5);
            return UUID.nameUUIDFromBytes(md5.digest());
        }
        catch (NoSuchAlgorithmException nsae)
        {
        }
        return null;
    }

    /**
     * Generate the partial hash for the schema fields in this base class
     * @param md5 the MessageDigest to be updated
     */
    protected void updateHash(MessageDigest md5)
    {
        try
        {
            md5.update(_classId.getPackageName().getBytes("UTF-8"));
            md5.update(_classId.getClassName().getBytes("UTF-8"));
            md5.update(_classId.getType().getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException uee)
        {
        }
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        super.listValues();
        _classId.listValues();
    }
}

