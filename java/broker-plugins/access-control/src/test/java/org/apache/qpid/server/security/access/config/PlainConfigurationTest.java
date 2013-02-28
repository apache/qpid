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
package org.apache.qpid.server.security.access.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectProperties.Property;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.config.ConfigurationFile;
import org.apache.qpid.server.security.access.config.PlainConfiguration;
import org.apache.qpid.server.security.access.config.Rule;
import org.apache.qpid.server.security.access.config.RuleSet;

/**
 * These tests check that the ACL file parsing works correctly.
 *
 * For each message that can be returned in a {@link ConfigurationException}, an ACL file is created that should trigger this
 * particular message.
 */
public class PlainConfigurationTest extends TestCase
{
    private PlainConfiguration writeACLConfig(String...aclData) throws Exception
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
        PlainConfiguration configFile = new PlainConfiguration(acl);
        configFile.load();
        return configFile;
    }

    public void testMissingACLConfig() throws Exception
    {
        try
        {
            // Load ruleset
            ConfigurationFile configFile = new PlainConfiguration(new File("doesnotexist"));
            configFile.load();

            fail("fail");
        }
        catch (IllegalConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.CONFIG_NOT_FOUND_MSG, "doesnotexist"), ce.getMessage());
            assertTrue(ce.getCause() instanceof FileNotFoundException);
        }
    }

    public void testACLFileSyntaxContinuation() throws Exception
    {
        try
        {
            writeACLConfig("ACL ALLOW ALL \\ ALL");
            fail("fail");
        }
        catch (IllegalConfigurationException ce)
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
        catch (IllegalConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.PARSE_TOKEN_FAILED_MSG, 1), ce.getMessage());
            assertTrue(ce.getCause() instanceof IllegalArgumentException);
            assertEquals("Not a valid permission: unparsed", ce.getCause().getMessage());
        }
    }

    public void testACLFileSyntaxNotEnoughACL() throws Exception
    {
        try
        {
            writeACLConfig("ACL ALLOW");
            fail("fail");
        }
        catch (IllegalConfigurationException ce)
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
        catch (IllegalConfigurationException ce)
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
        catch (IllegalConfigurationException ce)
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
        catch (IllegalConfigurationException ce)
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
        catch (IllegalConfigurationException ce)
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
        catch (IllegalConfigurationException ce)
        {
            assertEquals(String.format(PlainConfiguration.PROPERTY_NO_VALUE_MSG, 1), ce.getMessage());
        }
    }

    /**
     * Tests interpretation of an acl rule with no object properties.
     *
     */
    public void testValidRule() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("ACL DENY-LOG user1 ACCESS VIRTUALHOST");
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", "user1", rule.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.ACCESS, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.VIRTUALHOST, rule.getAction().getObjectType());
        assertEquals("Rule has unexpected object properties", ObjectProperties.EMPTY, rule.getAction().getProperties());
    }

    /**
     * Tests interpretation of an acl rule with object properties quoted in single quotes.
     */
    public void testValidRuleWithSingleQuotedProperty() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("ACL ALLOW all CREATE EXCHANGE name = \'value\'");
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", "all", rule.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.CREATE, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.EXCHANGE, rule.getAction().getObjectType());
        final ObjectProperties expectedProperties = new ObjectProperties();
        expectedProperties.setName("value");
        assertEquals("Rule has unexpected object properties", expectedProperties, rule.getAction().getProperties());
    }

    /**
     * Tests interpretation of an acl rule with object properties quoted in double quotes.
     */
    public void testValidRuleWithDoubleQuotedProperty() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("ACL ALLOW all CREATE EXCHANGE name = \"value\"");
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", "all", rule.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.CREATE, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.EXCHANGE, rule.getAction().getObjectType());
        final ObjectProperties expectedProperties = new ObjectProperties();
        expectedProperties.setName("value");
        assertEquals("Rule has unexpected object properties", expectedProperties, rule.getAction().getProperties());
    }

    /**
     * Tests interpretation of an acl rule with many object properties.
     */
    public void testValidRuleWithManyProperties() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("ACL ALLOW admin DELETE QUEUE name=name1 owner = owner1");
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", "admin", rule.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.DELETE, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.QUEUE, rule.getAction().getObjectType());
        final ObjectProperties expectedProperties = new ObjectProperties();
        expectedProperties.setName("name1");
        expectedProperties.put(Property.OWNER, "owner1");
        assertEquals("Rule has unexpected operation", expectedProperties, rule.getAction().getProperties());
    }

    /**
     * Tests interpretation of an acl rule with object properties containing wildcards.  Values containing
     * hashes must be quoted otherwise they are interpreted as comments.
     */
    public void testValidRuleWithWildcardProperties() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("ACL ALLOW all CREATE EXCHANGE routingKey = \'news.#\'",
                                                         "ACL ALLOW all CREATE EXCHANGE routingKey = \'news.co.#\'",
                                                         "ACL ALLOW all CREATE EXCHANGE routingKey = *.co.medellin");
        final RuleSet rs = config.getConfiguration();
        assertEquals(3, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(3, rules.size());
        final Rule rule1 = rules.get(0);
        assertEquals("Rule has unexpected identity", "all", rule1.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.CREATE, rule1.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.EXCHANGE, rule1.getAction().getObjectType());
        final ObjectProperties expectedProperties1 = new ObjectProperties();
        expectedProperties1.put(Property.ROUTING_KEY,"news.#");
        assertEquals("Rule has unexpected object properties", expectedProperties1, rule1.getAction().getProperties());

        final Rule rule2 = rules.get(10);
        final ObjectProperties expectedProperties2 = new ObjectProperties();
        expectedProperties2.put(Property.ROUTING_KEY,"news.co.#");
        assertEquals("Rule has unexpected object properties", expectedProperties2, rule2.getAction().getProperties());

        final Rule rule3 = rules.get(20);
        final ObjectProperties expectedProperties3 = new ObjectProperties();
        expectedProperties3.put(Property.ROUTING_KEY,"*.co.medellin");
        assertEquals("Rule has unexpected object properties", expectedProperties3, rule3.getAction().getProperties());
    }

    /**
     * Tests that rules are case insignificant.
     */
    public void testMixedCaseRuleInterpretation() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("AcL deny-LOG User1 BiND Exchange Name=AmQ.dIrect");
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", "User1", rule.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.BIND, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.EXCHANGE, rule.getAction().getObjectType());
        final ObjectProperties expectedProperties = new ObjectProperties("AmQ.dIrect");
        assertEquals("Rule has unexpected object properties", expectedProperties, rule.getAction().getProperties());
    }

    /**
     * Tests whitespace is supported. Note that currently the Java implementation permits comments to
     * be introduced anywhere in the ACL, whereas the C++ supports only whitespace at the beginning of
     * of line.
     */
    public void testCommentsSuppported() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("#Comment",
                                                         "ACL DENY-LOG user1 ACCESS VIRTUALHOST # another comment",
                                                         "  # final comment with leading whitespace");
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", "user1", rule.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.ACCESS, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.VIRTUALHOST, rule.getAction().getObjectType());
        assertEquals("Rule has unexpected object properties", ObjectProperties.EMPTY, rule.getAction().getProperties());
    }

    /**
     * Tests interpretation of an acl rule using mixtures of tabs/spaces as token separators.
     *
     */
    public void testWhitespace() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("ACL\tDENY-LOG\t\t user1\t \tACCESS VIRTUALHOST");
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", "user1", rule.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.ACCESS, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.VIRTUALHOST, rule.getAction().getObjectType());
        assertEquals("Rule has unexpected object properties", ObjectProperties.EMPTY, rule.getAction().getProperties());
    }

    /**
     * Tests interpretation of an acl utilising line continuation.
     */
    public void testLineContination() throws Exception
    {
        final PlainConfiguration config = writeACLConfig("ACL DENY-LOG user1 \\",
                                                         "ACCESS VIRTUALHOST");
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", "user1", rule.getIdentity());
        assertEquals("Rule has unexpected operation", Operation.ACCESS, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", ObjectType.VIRTUALHOST, rule.getAction().getObjectType());
        assertEquals("Rule has unexpected object properties", ObjectProperties.EMPTY, rule.getAction().getProperties());
    }

    public void testUserRuleParsing() throws Exception
    {
        validateRule(writeACLConfig("ACL ALLOW user1 CREATE USER"),
                "user1", Operation.CREATE, ObjectType.USER, ObjectProperties.EMPTY);
        validateRule(writeACLConfig("ACL ALLOW user1 CREATE USER name=\"otherUser\""),
                "user1", Operation.CREATE, ObjectType.USER, new ObjectProperties("otherUser"));

        validateRule(writeACLConfig("ACL ALLOW user1 DELETE USER"),
                "user1", Operation.DELETE, ObjectType.USER, ObjectProperties.EMPTY);
        validateRule(writeACLConfig("ACL ALLOW user1 DELETE USER name=\"otherUser\""),
                "user1", Operation.DELETE, ObjectType.USER, new ObjectProperties("otherUser"));

        validateRule(writeACLConfig("ACL ALLOW user1 UPDATE USER"),
                "user1", Operation.UPDATE, ObjectType.USER, ObjectProperties.EMPTY);
        validateRule(writeACLConfig("ACL ALLOW user1 UPDATE USER name=\"otherUser\""),
                "user1", Operation.UPDATE, ObjectType.USER, new ObjectProperties("otherUser"));

        validateRule(writeACLConfig("ACL ALLOW user1 ALL USER"),
                "user1", Operation.ALL, ObjectType.USER, ObjectProperties.EMPTY);
        validateRule(writeACLConfig("ACL ALLOW user1 ALL USER name=\"otherUser\""),
                "user1", Operation.ALL, ObjectType.USER, new ObjectProperties("otherUser"));
    }

    public void testGroupRuleParsing() throws Exception
    {
        validateRule(writeACLConfig("ACL ALLOW user1 CREATE GROUP"),
                "user1", Operation.CREATE, ObjectType.GROUP, ObjectProperties.EMPTY);
        validateRule(writeACLConfig("ACL ALLOW user1 CREATE GROUP name=\"groupName\""),
                "user1", Operation.CREATE, ObjectType.GROUP, new ObjectProperties("groupName"));

        validateRule(writeACLConfig("ACL ALLOW user1 DELETE GROUP"),
                "user1", Operation.DELETE, ObjectType.GROUP, ObjectProperties.EMPTY);
        validateRule(writeACLConfig("ACL ALLOW user1 DELETE GROUP name=\"groupName\""),
                "user1", Operation.DELETE, ObjectType.GROUP, new ObjectProperties("groupName"));

        validateRule(writeACLConfig("ACL ALLOW user1 UPDATE GROUP"),
                "user1", Operation.UPDATE, ObjectType.GROUP, ObjectProperties.EMPTY);
        validateRule(writeACLConfig("ACL ALLOW user1 UPDATE GROUP name=\"groupName\""),
                "user1", Operation.UPDATE, ObjectType.GROUP, new ObjectProperties("groupName"));

        validateRule(writeACLConfig("ACL ALLOW user1 ALL GROUP"),
                "user1", Operation.ALL, ObjectType.GROUP, ObjectProperties.EMPTY);
        validateRule(writeACLConfig("ACL ALLOW user1 ALL GROUP name=\"groupName\""),
                "user1", Operation.ALL, ObjectType.GROUP, new ObjectProperties("groupName"));
    }

    /** explicitly test for exception indicating that this functionality has been moved to Group Providers */
    public void testGroupDefinitionThrowsException() throws Exception
    {
        try
        {
            writeACLConfig("GROUP group1 bob alice");
            fail("Expected exception not thrown");
        }
        catch(IllegalConfigurationException e)
        {
            assertTrue(e.getMessage().contains("GROUP keyword not supported"));
        }
    }

    public void testManagementRuleParsing() throws Exception
    {
        validateRule(writeACLConfig("ACL ALLOW user1 ALL MANAGEMENT"),
                "user1", Operation.ALL, ObjectType.MANAGEMENT, ObjectProperties.EMPTY);

        validateRule(writeACLConfig("ACL ALLOW user1 ACCESS MANAGEMENT"),
                "user1", Operation.ACCESS, ObjectType.MANAGEMENT, ObjectProperties.EMPTY);
    }

    private void validateRule(final PlainConfiguration config, String username, Operation operation, ObjectType objectType, ObjectProperties objectProperties)
    {
        final RuleSet rs = config.getConfiguration();
        assertEquals(1, rs.getRuleCount());

        final Map<Integer, Rule> rules = rs.getAllRules();
        assertEquals(1, rules.size());
        final Rule rule = rules.get(0);
        assertEquals("Rule has unexpected identity", username, rule.getIdentity());
        assertEquals("Rule has unexpected operation", operation, rule.getAction().getOperation());
        assertEquals("Rule has unexpected operation", objectType, rule.getAction().getObjectType());
        assertEquals("Rule has unexpected object properties", objectProperties, rule.getAction().getProperties());
    }
}
