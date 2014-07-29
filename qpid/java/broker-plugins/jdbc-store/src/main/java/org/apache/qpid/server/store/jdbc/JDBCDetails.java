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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.model.ConfiguredObject;

public abstract class JDBCDetails
{
    public static final String CONTEXT_JDBCSTORE_BIGINTTYPE = "qpid.jdbcstore.bigIntType";
    public static final String CONTEXT_JDBCSTORE_VARBINARYTYPE = "qpid.jdbcstore.varBinaryType";
    public static final String CONTEXT_JDBCSTORE_BLOBTYPE = "qpid.jdbcstore.blobType";
    public static final String CONTEXT_JDBCSTORE_USEBYTESFORBLOB = "qpid.jdbcstore.useBytesForBlob";

    public abstract String getVendor();

    public abstract String getBlobType();

    public abstract String getVarBinaryType();

    public abstract String getBigintType();

    public abstract boolean isUseBytesMethodsForBlob();

    public abstract boolean isKnownVendor();

    public abstract boolean isOverridden();

    static class KnownJDBCDetails extends JDBCDetails
    {
        private static final JDBCDetails FALLBACK = new KnownJDBCDetails("fallback", "blob", "varchar(%d) for bit data", "bigint", false,
                                                                         false);
        private static final JDBCDetails ORACLE = new KnownJDBCDetails("oracle", "blob", "raw(%d)", "number", false,
                                                                       true);
        private static final JDBCDetails SYBASE = new KnownJDBCDetails("sybase", "image", "varbinary(%d)", "bigint", false,
                                                                       true);
        private static final JDBCDetails POSTGRES = new KnownJDBCDetails("postgresql", "bytea", "bytea", "bigint", true,
                                                                         true);
        private static final JDBCDetails DERBY = new KnownJDBCDetails("derby", "blob", "varchar(%d) for bit data", "bigint", false,
                                                                      true);

        static
        {
            Map<String, JDBCDetails> map = new HashMap<>();

            try
            {
                map.put(ORACLE.getVendor(), ORACLE);
                map.put(SYBASE.getVendor(), SYBASE);
                map.put(POSTGRES.getVendor(), POSTGRES);
                map.put(DERBY.getVendor(), DERBY);
                map.put(FALLBACK.getVendor(), FALLBACK);
            }
            finally
            {
                VENDOR_DETAILS = Collections.unmodifiableMap(map);
            }
        }

        private final static Map<String, JDBCDetails> VENDOR_DETAILS;

        private final String _vendor;
        private final String _blobType;
        private final String _varBinaryType;
        private final String _bigintType;
        private final boolean _useBytesMethodsForBlob;
        private final boolean _isKnownVendor;

        KnownJDBCDetails(String vendor,
                         String blobType,
                         String varBinaryType,
                         String bigIntType,
                         boolean useBytesMethodsForBlob,
                         boolean knownVendor)
        {
            _vendor = vendor;
            _blobType = blobType;
            _varBinaryType = varBinaryType;
            _bigintType = bigIntType;
            _useBytesMethodsForBlob = useBytesMethodsForBlob;
            _isKnownVendor = knownVendor;
        }

        @Override
        public String getVendor()
        {
            return _vendor;
        }

        @Override
        public String getBlobType()
        {
            return _blobType;
        }

        @Override
        public String getVarBinaryType()
        {
            return _varBinaryType;
        }

        @Override
        public boolean isUseBytesMethodsForBlob()
        {
            return _useBytesMethodsForBlob;
        }

        @Override
        public String getBigintType()
        {
            return _bigintType;
        }

        @Override
        public boolean isKnownVendor()
        {
            return _isKnownVendor;
        }

        @Override
        public boolean isOverridden()
        {
            return false;
        }

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
               ", knownVendor=" + isKnownVendor() +
               ", overridden=" + isOverridden() +
               '}';
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final JDBCDetails that = (JDBCDetails) o;

        if (isKnownVendor() != that.isKnownVendor())
        {
            return false;
        }
        if (isOverridden() != that.isOverridden())
        {
            return false;
        }
        if (isUseBytesMethodsForBlob() != that.isUseBytesMethodsForBlob())
        {
            return false;
        }
        if (getBigintType() != null ? !getBigintType().equals(that.getBigintType()) : that.getBigintType() != null)
        {
            return false;
        }
        if (getBlobType() != null ? !getBlobType().equals(that.getBlobType()) : that.getBlobType() != null)
        {
            return false;
        }
        if (getVarBinaryType() != null ? !getVarBinaryType().equals(that.getVarBinaryType()) : that.getVarBinaryType() != null)
        {
            return false;
        }
        if (getVendor() != null ? !getVendor().equals(that.getVendor()) : that.getVendor() != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = getVendor() != null ? getVendor().hashCode() : 0;
        result = 31 * result + (getBlobType() != null ? getBlobType().hashCode() : 0);
        result = 31 * result + (getVarBinaryType() != null ? getVarBinaryType().hashCode() : 0);
        result = 31 * result + (getBigintType() != null ? getBigintType().hashCode() : 0);
        result = 31 * result + (isUseBytesMethodsForBlob() ? 1 : 0);
        result = 31 * result + (isKnownVendor() ? 1 : 0);
        result = 31 * result + (isOverridden() ? 1 : 0);
        return result;
    }

    public static JDBCDetails getDetailsForJdbcUrl(String jdbcUrl, final ConfiguredObject<?> object)
    {
        final Set<String> contextKeys = object.getContextKeys(false);
        Map<String,String> mapConversion = new AbstractMap<String, String>()
        {
            @Override
            public Set<Entry<String, String>> entrySet()
            {
                return new AbstractSet<Entry<String, String>>()
                {
                    @Override
                    public Iterator<Entry<String, String>> iterator()
                    {
                        final Iterator<String> underlying = contextKeys.iterator();
                        return new Iterator<Entry<String, String>>()
                        {
                            @Override
                            public boolean hasNext()
                            {
                                return underlying.hasNext();
                            }

                            @Override
                            public Entry<String, String> next()
                            {
                                final String key = underlying.next();
                                final String value = object.getContextValue(String.class, key);
                                return new Entry<String,String>()
                                {

                                    @Override
                                    public String getKey()
                                    {
                                        return key;
                                    }

                                    @Override
                                    public String getValue()
                                    {
                                        return value;
                                    }

                                    @Override
                                    public String setValue(final String value)
                                    {
                                        throw new UnsupportedOperationException();
                                    }
                                };

                            }

                            @Override
                            public void remove()
                            {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }

                    @Override
                    public int size()
                    {
                        return contextKeys.size();
                    }
                };
            }
        };
        return getDetailsForJdbcUrl(jdbcUrl, mapConversion);
    }
    public static JDBCDetails getDetailsForJdbcUrl(String jdbcUrl, final Map<String, String> contextMap)
    {
        String[] components = jdbcUrl.split(":", 3);
        final JDBCDetails details;
        if(components.length >= 2)
        {
            String vendor = components[1];
            if (KnownJDBCDetails.VENDOR_DETAILS.containsKey(vendor))
            {
                details = KnownJDBCDetails.VENDOR_DETAILS.get(vendor);
            }
            else
            {
                details = KnownJDBCDetails.FALLBACK;
            }
        }
        else
        {
            details = KnownJDBCDetails.FALLBACK;
        }


        return new JDBCDetails()
        {
            @Override
            public String getVendor()
            {
                return details.getVendor();
            }

            @Override
            public String getBlobType()
            {
                return contextMap.containsKey(CONTEXT_JDBCSTORE_BLOBTYPE)
                        ? String.valueOf(contextMap.get(CONTEXT_JDBCSTORE_BLOBTYPE)) : details.getBlobType();
            }

            @Override
            public String getVarBinaryType()
            {
                return contextMap.containsKey(CONTEXT_JDBCSTORE_VARBINARYTYPE)
                        ? String.valueOf(contextMap.get(CONTEXT_JDBCSTORE_VARBINARYTYPE)) : details.getVarBinaryType();
            }

            @Override
            public String getBigintType()
            {
                return contextMap.containsKey(CONTEXT_JDBCSTORE_BIGINTTYPE)
                        ? String.valueOf(contextMap.get(CONTEXT_JDBCSTORE_BIGINTTYPE)) : details.getBigintType();
            }

            @Override
            public boolean isUseBytesMethodsForBlob()
            {
                return contextMap.containsKey(CONTEXT_JDBCSTORE_USEBYTESFORBLOB)
                        ? Boolean.parseBoolean(contextMap.get(CONTEXT_JDBCSTORE_USEBYTESFORBLOB)) : details.isUseBytesMethodsForBlob();
            }

            @Override
            public boolean isKnownVendor()
            {
                return details.isKnownVendor();
            }

            @Override
            public boolean isOverridden()
            {
                return contextMap.containsKey(CONTEXT_JDBCSTORE_USEBYTESFORBLOB)
                        || contextMap.containsKey(CONTEXT_JDBCSTORE_BIGINTTYPE)
                        || contextMap.containsKey(CONTEXT_JDBCSTORE_VARBINARYTYPE)
                        || contextMap.containsKey(CONTEXT_JDBCSTORE_BLOBTYPE);
            }
        };

    }

}
