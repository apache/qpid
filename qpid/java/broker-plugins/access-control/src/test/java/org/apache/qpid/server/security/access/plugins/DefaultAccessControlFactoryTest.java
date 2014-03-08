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
package org.apache.qpid.server.security.access.plugins;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.security.AccessControl;
import org.apache.qpid.server.security.access.FileAccessControlProviderConstants;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

import static org.mockito.Mockito.mock;

public class DefaultAccessControlFactoryTest extends QpidTestCase
{
    public void testCreateInstanceWhenAclFileIsNotPresent()
    {
        DefaultAccessControlFactory factory = new DefaultAccessControlFactory();
        Map<String, Object> attributes = new HashMap<String, Object>();
        AccessControl acl = factory.createInstance(attributes, mock(EventLoggerProvider.class));
        assertNull("ACL was created without a configuration file", acl);
    }

    public void testCreateInstanceWhenAclFileIsSpecified()
    {
        File aclFile = TestFileUtils.createTempFile(this, ".acl", "ACL ALLOW all all");
        DefaultAccessControlFactory factory = new DefaultAccessControlFactory();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.TYPE, FileAccessControlProviderConstants.ACL_FILE_PROVIDER_TYPE);
        attributes.put(FileAccessControlProviderConstants.PATH, aclFile.getAbsolutePath());
        AccessControl acl = factory.createInstance(attributes, mock(EventLoggerProvider.class));
        acl.open();

        assertNotNull("ACL was not created from acl file: " + aclFile.getAbsolutePath(), acl);
    }

    public void testCreateInstanceWhenAclFileIsSpecifiedButDoesNotExist()
    {
        File aclFile = new File(TMP_FOLDER, "my-non-existing-acl-" + System.currentTimeMillis());
        assertFalse("ACL file " + aclFile.getAbsolutePath() + " actually exists but should not", aclFile.exists());
        DefaultAccessControlFactory factory = new DefaultAccessControlFactory();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.TYPE, FileAccessControlProviderConstants.ACL_FILE_PROVIDER_TYPE);
        attributes.put(FileAccessControlProviderConstants.PATH, aclFile.getAbsolutePath());
        try
        {
            AccessControl control = factory.createInstance(attributes, mock(EventLoggerProvider.class));
            control.open();
            fail("It should not be possible to create and initialise ACL with non existing file");
        }
        catch (IllegalConfigurationException e)
        {
            assertTrue("Unexpected exception message: " + e.getMessage(), Pattern.matches("ACL file '.*' is not found", e.getMessage()));
        }
    }
}
