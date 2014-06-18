/*
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
 */

package org.apache.qpid.server.store.jdbc;

import java.util.HashMap;
import java.util.Map;

public class JDBCDetails
{

    private static Map<String, JDBCDetails> VENDOR_DETAILS = new HashMap<>();

    private static JDBCDetails DERBY_DETAILS =
            new JDBCDetails("derby",
                            "blob",
                            "varchar(%d) for bit data",
                            "bigint",
                            false);

    private static JDBCDetails POSTGRESQL_DETAILS =
            new JDBCDetails("postgresql",
                            "bytea",
                            "bytea",
                            "bigint",
                            true);

    private static JDBCDetails MYSQL_DETAILS =
            new JDBCDetails("mysql",
                            "blob",
                            "varbinary(%d)",
                            "bigint",
                            false);


    private static JDBCDetails SYBASE_DETAILS =
            new JDBCDetails("sybase",
                            "image",
                            "varbinary(%d)",
                            "bigint",
                            false);


    private static JDBCDetails ORACLE_DETAILS =
            new JDBCDetails("oracle",
                            "blob",
                            "raw(%d)",
                            "number",
                            false);


    static
    {

        addDetails(DERBY_DETAILS);
        addDetails(POSTGRESQL_DETAILS);
        addDetails(MYSQL_DETAILS);
        addDetails(SYBASE_DETAILS);
        addDetails(ORACLE_DETAILS);
    }

    public static JDBCDetails getDetails(String vendor)
    {
        return VENDOR_DETAILS.get(vendor);
    }

    public static JDBCDetails getDefaultDetails()
    {
        return DERBY_DETAILS;
    }

    private static void addDetails(JDBCDetails details)
    {
        VENDOR_DETAILS.put(details.getVendor(), details);
    }

    private final String _vendor;
    private String _blobType;
    private String _varBinaryType;
    private String _bigintType;
    private boolean _useBytesMethodsForBlob;

    JDBCDetails(String vendor,
                String blobType,
                String varBinaryType,
                String bigIntType,
                boolean useBytesMethodsForBlob)
    {
        _vendor = vendor;
        setBlobType(blobType);
        setVarBinaryType(varBinaryType);
        setBigintType(bigIntType);
        setUseBytesMethodsForBlob(useBytesMethodsForBlob);
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        JDBCDetails that = (JDBCDetails) o;

        if (!getVendor().equals(that.getVendor()))
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return getVendor().hashCode();
    }

    @Override
    public String toString()
    {
        return "JDBCDetails{" +
               "vendor='" + getVendor() + '\'' +
               ", blobType='" + getBlobType() + '\'' +
               ", varBinaryType='" + getVarBinaryType() + '\'' +
               ", bigIntType='" + getBigintType() + '\'' +
               ", useBytesMethodsForBlob=" + isUseBytesMethodsForBlob() +
               '}';
    }

    public String getVendor()
    {
        return _vendor;
    }

    public String getBlobType()
    {
        return _blobType;
    }

    public void setBlobType(String blobType)
    {
        _blobType = blobType;
    }

    public String getVarBinaryType()
    {
        return _varBinaryType;
    }

    public void setVarBinaryType(String varBinaryType)
    {
        _varBinaryType = varBinaryType;
    }

    public boolean isUseBytesMethodsForBlob()
    {
        return _useBytesMethodsForBlob;
    }

    public void setUseBytesMethodsForBlob(boolean useBytesMethodsForBlob)
    {
        _useBytesMethodsForBlob = useBytesMethodsForBlob;
    }

    public String getBigintType()
    {
        return _bigintType;
    }

    public void setBigintType(String bigintType)
    {
        _bigintType = bigintType;
    }
}
