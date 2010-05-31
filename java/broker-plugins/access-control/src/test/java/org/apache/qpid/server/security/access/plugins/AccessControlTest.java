/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.qpid.server.security.access.plugins;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;

import junit.framework.TestCase;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.security.access.config.ConfigurationFile;
import org.apache.qpid.server.security.access.config.PlainConfiguration;
import org.apache.qpid.server.security.access.config.RuleSet;

/**
 * These tests check that the ACL file parsing works correctly.
 * 
 * For each message that can be returned in a {@link ConfigurationException}, an ACL file is created that should trigger this
 * particular message.
 */
public class AccessControlTest extends TestCase
{
    public void writeACLConfig(String...aclData) throws Exception
    {
        File acl = File.createTempFile(getClass().getName() + getName(), "acl");
        acl.deleteOnExit();
        
        // Write ACL file
        PrintWriter aclWriter = new PrintWriter(new FileWriter(acl));
        for (String line : aclData)
        {
            aclWriter.println(line);
        }
        aclWriter.close();

        // Load ruleset
        ConfigurationFile configFile = new PlainConfiguration(acl);
        RuleSet ruleSet = configFile.load();
    }

    public void testMissingACLConfig() throws Exception
    {
        try
        {
            // Load ruleset
	        ConfigurationFile configFile = new PlainConfiguration(new File("doesnotexist"));
	        RuleSet ruleSet = configFile.load();
            
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.CONFIG_NOT_FOUND_MSG, "doesnotexist"), ce.getMessage());
            assertTrue(ce.getCause() instanceof FileNotFoundException);
            assertEquals("doesnotexist (No such file or directory)", ce.getCause().getMessage());
        }
    }

    public void testACLFileSyntaxContinuation() throws Exception
    {
        try
        {
            writeACLConfig("ACL ALLOW ALL \\ ALL");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.PREMATURE_CONTINUATION_MSG, 1), ce.getMessage());
        }
    }

    public void testACLFileSyntaxTokens() throws Exception
    {
        try
        {
            writeACLConfig("ACL unparsed ALL ALL");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.PARSE_TOKEN_FAILED_MSG, 1), ce.getMessage());
            assertTrue(ce.getCause() instanceof IllegalArgumentException);
            assertEquals("Not a valid permission: unparsed", ce.getCause().getMessage());
        }
    }

    public void testACLFileSyntaxNotEnoughGroup() throws Exception
    {
        try
        {
            writeACLConfig("GROUP blah");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.NOT_ENOUGH_GROUP_MSG, 1), ce.getMessage());
        }
    }

    public void testACLFileSyntaxNotEnoughACL() throws Exception
    {
        try
        {
            writeACLConfig("ACL ALLOW");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.NOT_ENOUGH_ACL_MSG, 1), ce.getMessage());
        }
    }

    public void testACLFileSyntaxNotEnoughConfig() throws Exception
    {
        try
        {
            writeACLConfig("CONFIG");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.NOT_ENOUGH_TOKENS_MSG, 1), ce.getMessage());
        }
    }

    public void testACLFileSyntaxNotEnough() throws Exception
    {
        try
        {
            writeACLConfig("INVALID");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.NOT_ENOUGH_TOKENS_MSG, 1), ce.getMessage());
        }
    }

    public void testACLFileSyntaxPropertyKeyOnly() throws Exception
    {
        try
        {
            writeACLConfig("ACL ALLOW adk CREATE QUEUE name");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.PROPERTY_KEY_ONLY_MSG, 1), ce.getMessage());
        }
    }

    public void testACLFileSyntaxPropertyNoEquals() throws Exception
    {
        try
        {
            writeACLConfig("ACL ALLOW adk CREATE QUEUE name test");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.PROPERTY_NO_EQUALS_MSG, 1), ce.getMessage());
        }
    }

    public void testACLFileSyntaxPropertyNoValue() throws Exception
    {
        try
        {
            writeACLConfig("ACL ALLOW adk CREATE QUEUE name =");
            fail("fail");
        }
        catch (ConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.PROPERTY_NO_VALUE_MSG, 1), ce.getMessage());
        }
    }
}
