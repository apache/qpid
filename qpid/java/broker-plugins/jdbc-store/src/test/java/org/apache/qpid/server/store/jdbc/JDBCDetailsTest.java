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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class JDBCDetailsTest extends TestCase
{
    public void testDerby()
    {
        JDBCDetails derbyDetails = JDBCDetails.getDetailsForJdbcUrl("jdbc:derby:sample",
                                                               Collections.<String, String>emptyMap());
        assertEquals("derby", derbyDetails.getVendor());
        assertEquals("varchar(%d) for bit data", derbyDetails.getVarBinaryType());
        assertEquals("bigint", derbyDetails.getBigintType());
        assertEquals("blob", derbyDetails.getBlobType());
        assertFalse(derbyDetails.isUseBytesMethodsForBlob());

        assertTrue(derbyDetails.isKnownVendor());
        assertFalse(derbyDetails.isOverridden());
    }

    public void testUnknownVendor_UsesFallbackDetails()
    {
        JDBCDetails details = JDBCDetails.getDetailsForJdbcUrl("jdbc:homedb:", Collections.<String, String>emptyMap());
        assertEquals("fallback", details.getVendor());
        assertEquals("varchar(%d) for bit data", details.getVarBinaryType());
        assertEquals("bigint", details.getBigintType());
        assertEquals("blob", details.getBlobType());
        assertEquals(false, details.isUseBytesMethodsForBlob());
        assertFalse(details.isOverridden());
        assertFalse(details.isKnownVendor());
    }

    public void testDerbyWithOverride()
    {

        Map<String, String> contextMap = new HashMap<>();
        contextMap.put(JDBCDetails.CONTEXT_JDBCSTORE_VARBINARYTYPE, "myvarbin");

        JDBCDetails derbyDetails = JDBCDetails.getDetailsForJdbcUrl("jdbc:derby:sample", contextMap);
        assertEquals("derby", derbyDetails.getVendor());
        assertEquals("myvarbin", derbyDetails.getVarBinaryType());
        assertEquals("bigint", derbyDetails.getBigintType());
        assertEquals("blob", derbyDetails.getBlobType());
        assertFalse(derbyDetails.isUseBytesMethodsForBlob());

        assertTrue(derbyDetails.isKnownVendor());
        assertTrue(derbyDetails.isOverridden());
    }




    public void testRecognisedDriver_AllDetailsProvidedByContext()
    {
        Map<String, String> contextMap = new HashMap<>();
        contextMap.put(JDBCDetails.CONTEXT_JDBCSTORE_VARBINARYTYPE, "myvarbin");
        contextMap.put(JDBCDetails.CONTEXT_JDBCSTORE_BIGINTTYPE, "mybigint");
        contextMap.put(JDBCDetails.CONTEXT_JDBCSTORE_BLOBTYPE, "myblob");
        contextMap.put(JDBCDetails.CONTEXT_JDBCSTORE_USEBYTESFORBLOB, "true");

        JDBCDetails details = JDBCDetails.getDetailsForJdbcUrl("jdbc:sybase:", contextMap);
        assertEquals("sybase", details.getVendor());
        assertEquals("myvarbin", details.getVarBinaryType());
        assertEquals("mybigint", details.getBigintType());
        assertEquals("myblob", details.getBlobType());
        assertEquals(true, details.isUseBytesMethodsForBlob());
        assertTrue(details.isKnownVendor());
        assertTrue(details.isOverridden());
    }

}