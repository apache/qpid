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
 * Schema are identified by a combination of their package name and class name. A hash value over the body of
 * the schema provides a revision identifier. The class SchemaClassId represents this Schema identifier.
 * <p>
 * If the hash value is not supplied, then the value of the hash string will be set to None. This will be the
 * case when a SchemaClass is being dynamically constructed, and a proper hash is not yet available.
 *
 * @author Fraser Adams
 */
public final class SchemaClassId extends QmfData
{
    private String _packageName = "";
    private String _className = "";
    private String _type = "";
    private UUID _hash = null;

    /**
     * The main constructor, taking a java.util.Map as a parameter.
     *
     * @param m the map used to construct the SchemaClassId.
     */
    public SchemaClassId(final Map m)
    {
        super(m);
        _packageName =  getStringValue("_package_name");
        _className = getStringValue("_class_name");
        _type = getStringValue("_type");
        _hash = hasValue("_hash") ? (UUID)getValue("_hash") : null;
    }

    /**
     * Construct a SchemaClassId from a given class name.
     *
     * @param className the class name.
     */
    public SchemaClassId(final String className)
    {
        this(null, className, null, null);
    }

    /**
     * Construct a SchemaClassId from a given package name and class name.
     *
     * @param packageName the package name.
     * @param className the class name.
     */
    public SchemaClassId(final String packageName, final String className)
    {
        this(packageName, className, null, null);
    }

    /**
     * Construct a SchemaClassId from a given package name and class name and type (_data or _event).
     *
     * @param packageName the package name.
     * @param className the class name.
     * @param type the schema type (_data or _event).
     */
    public SchemaClassId(final String packageName, final String className, final String type)
    {
        this(packageName, className, type, null);
    }

    /**
     * Construct a SchemaClassId from a given package name and class name, type (_data or _event) and hash.
     *
     * @param packageName the package name.
     * @param className the class name.
     * @param type the schema type (_data or _event).
     * @param hash a UUID representation of the md5 hash of the Schema,
     */
    public SchemaClassId(final String packageName, final String className, final String type, final UUID hash)
    {
        if (packageName != null)
        {
            setValue("_package_name", packageName);
            _packageName = packageName;
        }

        if (className != null)
        {
            setValue("_class_name", className);
            _className = className;
        }

        if (type != null)
        {
            setValue("_type", type);
            _type = type;
        }

        if (hash != null)
        {
            setValue("_hash", hash);
            _hash = hash;
        }
    }

    /**
     * Return The name of the associated package.
     * @return The name of the associated package. Returns empty String if there's no package name.
     */
    public String getPackageName()
    {
        return _packageName;
    }

    /**
     * Return The name of the class within the package.
     * @return The name of the class within the package. Returns empty String if there's no class name.
     */
    public String getClassName()
    {
        return _className;
    }

    /**
     * Return The type of schema, either "_data" or "_event".
     * @return The type of schema, either "_data" or "_event". Returns empty String if type is unknown.
     */
    public String getType()
    {
        return _type;
    }

    /**
     * Return The MD5 hash of the schema.
     * @return The MD5 hash of the schema, in the format "%08x-%08x-%08x-%08x"
     */
    public UUID getHashString()
    {
        return _hash;
    }

    /**
     * Compares two SchemaClassId objects for equality.
     * @param rhs the right hands side SchemaClassId in the comparison.
     * @return true if the two SchemaClassId objects are equal otherwise returns false.
     */
    @Override
    public boolean equals(final Object rhs)
    {
        if (rhs instanceof SchemaClassId)
        {
            SchemaClassId that = (SchemaClassId)rhs;
            String lvalue = _packageName + _className + _hash;
            String rvalue = that._packageName + that._className + that._hash;
            return lvalue.equals(rvalue);
        }
        return false;
    }

    /**
     * Returns the SchemaClassId hashCode.
     * @return the SchemaClassId hashCode.
     */
    @Override
    public int hashCode()
    {
        String lvalue = _packageName + _className + _hash;
        return lvalue.hashCode();
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        System.out.println("_package_name: " + getPackageName());
        System.out.println("_class_name: " + getClassName());
        System.out.println("_type: " + getType());
        System.out.println("_hash: " + getHashString());
    }
}

