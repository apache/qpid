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
package org.apache.qpid.systest.rest.acl;

import java.io.IOException;

import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.security.acl.AbstractACLTestCase;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class LogViewerACLTest extends QpidRestTestCase
{
    private static final String ALLOWED_USER = "user1";
    private static final String DENIED_USER = "user2";

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().configureTemporaryPasswordFile(this, ALLOWED_USER, DENIED_USER);

        AbstractACLTestCase.writeACLFileUtil(this, "ACL ALLOW-LOG ALL ACCESS MANAGEMENT",
                "ACL ALLOW-LOG " + ALLOWED_USER + " ACCESS_LOGS BROKER",
                "ACL DENY-LOG " + DENIED_USER + " ACCESS_LOGS BROKER",
                "ACL DENY-LOG ALL ALL");

        getBrokerConfiguration().setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
    }

    public void testGetLogRecordsAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        int responseCode = getRestTestHelper().submitRequest("/service/logrecords", "GET");
        assertEquals("Access to log records should be allowed", 200, responseCode);
    }

    public void testGetLogRecordsDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        int responseCode = getRestTestHelper().submitRequest("/service/logrecords", "GET");
        assertEquals("Access to log records should be denied", 403, responseCode);
    }

    public void testGetLogFilesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        int responseCode = getRestTestHelper().submitRequest("/service/logfilenames", "GET");
        assertEquals("Access to log files should be allowed", 200, responseCode);
    }

    public void testGetLogFilesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        int responseCode = getRestTestHelper().submitRequest("/service/logfilenames", "GET");
        assertEquals("Access to log files should be denied", 403, responseCode);
    }

    public void testDownloadLogFileAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        int responseCode = getRestTestHelper().submitRequest("/service/logfile?l=appender%2fqpid.log", "GET");
        assertEquals("Access to log files should be allowed", 404, responseCode);
    }

    public void testDownloadLogFileDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        int responseCode = getRestTestHelper().submitRequest("/service/logfile?l=appender%2fqpid.log", "GET");
        assertEquals("Access to log files should be denied", 403, responseCode);
    }

}
