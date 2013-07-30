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
package org.apache.qpid.transport.network.security.ssl;

import org.apache.qpid.test.utils.QpidTestCase;

public class SSLUtilTest extends QpidTestCase
{
    public void testGetIdFromSubjectDN()
    {
        // "normal" dn
        assertEquals("user@somewhere.example.org",SSLUtil.getIdFromSubjectDN("cn=user,dc=somewhere,dc=example,dc=org"));
        // quoting of values, case of types, spacing all ignored
        assertEquals("user2@somewhere.example.org",SSLUtil.getIdFromSubjectDN("DC=somewhere, dc=example,cn=\"user2\",dc=org"));
        // only first cn is used
        assertEquals("user@somewhere.example.org",SSLUtil.getIdFromSubjectDN("DC=somewhere, dc=example,cn=\"user\",dc=org, cn=user2"));
        // no cn, no Id
        assertEquals("",SSLUtil.getIdFromSubjectDN("DC=somewhere, dc=example,dc=org"));
        // cn in value is ignored
        assertEquals("",SSLUtil.getIdFromSubjectDN("C=CZ,O=Scholz,OU=\"JAKUB CN=USER1\""));
        // cn with no dc gives just user
        assertEquals("someone",SSLUtil.getIdFromSubjectDN("ou=someou, CN=\"someone\""));
        // null results in empty string
        assertEquals("",SSLUtil.getIdFromSubjectDN(null));
        // invalid name results in empty string
        assertEquals("",SSLUtil.getIdFromSubjectDN("ou=someou, ="));
        // component containing whitespace
        assertEquals("me@example.com",SSLUtil.getIdFromSubjectDN("CN=me,DC=example, DC=com, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB"));
        // empty CN
        assertEquals("",SSLUtil.getIdFromSubjectDN("CN=,DC=somewhere, dc=example,dc=org"));


    }
}
