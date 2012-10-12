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
package org.apache.qpid.server.security.group;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;

import junit.framework.TestCase;

public class FileGroupManagerFactoryTest extends TestCase
{

    private FileGroupManagerFactory _factory = new FileGroupManagerFactory();
    private Configuration _configuration = new XMLConfiguration();
    private String _emptyButValidGroupFile = TestFileUtils.createTempFile(this).getAbsolutePath();

    public void testInstanceCreated() throws Exception
    {
        _configuration.setProperty("file-group-manager.attributes.attribute.name", "groupFile");
        _configuration.setProperty("file-group-manager.attributes.attribute.value", _emptyButValidGroupFile);

        GroupManager manager = _factory.createInstance(_configuration);
        assertNotNull(manager);
        assertTrue(manager instanceof FileGroupManager);
    }

    public void testReturnsNullWhenNoConfig() throws Exception
    {
        GroupManager manager = _factory.createInstance(_configuration);
        assertNull(manager);
    }

    public void testReturnsNullWhenConfigNotForThisPlugin() throws Exception
    {
        _configuration.setProperty("other-group-manager", "config");

        GroupManager manager = _factory.createInstance(_configuration);
        assertNull(manager);
    }

    public void testRejectsConfigThatHasUnexpectedAttributeName() throws Exception
    {
        _configuration.setProperty("file-group-manager.attributes.attribute.name", "unexpected");
        _configuration.setProperty("file-group-manager.attributes.attribute.value", _emptyButValidGroupFile);

        try
        {
            _factory.createInstance(_configuration);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }
    }

    public void testRejectsConfigThatIsMissingAttributeValue() throws Exception
    {
        _configuration.setProperty("file-group-manager.attributes.attribute.name", "groupFile");
        _configuration.setProperty("file-group-manager.attributes.attribute.value", null);

        try
        {
            _factory.createInstance(_configuration);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }
    }

}
