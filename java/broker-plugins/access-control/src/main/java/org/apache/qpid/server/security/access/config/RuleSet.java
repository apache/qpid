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

import java.net.InetAddress;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.WeakHashMap;

import javax.security.auth.Subject;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.logging.messages.AccessControlMessages;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.Permission;

/**
 * Models the rule configuration for the access control plugin.
 */
public class RuleSet implements EventLoggerProvider
{
    private static final Logger _logger = LoggerFactory.getLogger(RuleSet.class);

    private static final String AT = "@";
    private static final String SLASH = "/";

    public static final String DEFAULT_ALLOW = "defaultallow";
    public static final String DEFAULT_DENY = "defaultdeny";

    public static final List<String> CONFIG_PROPERTIES = Arrays.asList(DEFAULT_ALLOW, DEFAULT_DENY);

    private static final Integer _increment = 10;

    private final SortedMap<Integer, Rule> _rules = new TreeMap<Integer, Rule>();
    private final Map<Subject, Map<Operation, Map<ObjectType, List<Rule>>>> _cache =
                        new WeakHashMap<Subject, Map<Operation, Map<ObjectType, List<Rule>>>>();
    private final Map<String, Boolean> _config = new HashMap<String, Boolean>();
    private final EventLoggerProvider _eventLogger;

    public RuleSet(EventLoggerProvider eventLogger)
    {
        _eventLogger = eventLogger;
        // set some default configuration properties
        configure(DEFAULT_DENY, Boolean.TRUE);
    }

    /**
     * Clear the contents, including acl rules and configuration.
     */
    public void clear()
    {
        _rules.clear();
        _cache.clear();
        _config.clear();
    }

    public int getRuleCount()
    {
        return _rules.size();
    }

    /**
     * Filtered rules list based on a subject and operation.
     *
     * Allows only enabled rules with identity equal to all, the same, or a group with identity as a member,
     * and operation is either all or the same operation.
     */
    public List<Rule> getRules(final Subject subject, final Operation operation, final ObjectType objectType)
    {
        final Map<ObjectType, List<Rule>> objects = getObjectToRuleCache(subject, operation);

        // Lookup object type rules for the operation
        if (!objects.containsKey(objectType))
        {
            final Set<Principal> principals = subject.getPrincipals();
            boolean controlled = false;
            List<Rule> filtered = new LinkedList<Rule>();
            for (Rule rule : _rules.values())
            {
                final Action ruleAction = rule.getAction();
                if (rule.isEnabled()
                    && (ruleAction.getOperation() == Operation.ALL || ruleAction.getOperation() == operation)
                    && (ruleAction.getObjectType() == ObjectType.ALL || ruleAction.getObjectType() == objectType))
                {
                    controlled = true;

                    if (isRelevant(principals,rule))
                    {
                        filtered.add(rule);
                    }
                }
            }

            // Return null if there are no rules at all for this operation and object type
            if (filtered.isEmpty() && controlled == false)
            {
                filtered = null;
            }

            // Save the rules we selected
            objects.put(objectType, filtered);
            if(_logger.isDebugEnabled())
            {
                _logger.debug("Cached " + objectType + " RulesList: " + filtered);
            }
        }

        // Return the cached rules
        List<Rule> rules = objects.get(objectType);
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Returning RuleList: " + rules);
        }

        return rules;
    }

    public boolean isValidNumber(Integer number)
    {
        return !_rules.containsKey(number);
    }

    public void grant(Integer number, String identity, Permission permission, Operation operation)
    {
        AclAction action = new AclAction(operation);
        addRule(number, identity, permission, action);
    }

    public void grant(Integer number, String identity, Permission permission, Operation operation, ObjectType object, ObjectProperties properties)
    {
        AclAction action = new AclAction(operation, object, properties);
        addRule(number, identity, permission, action);
    }

    public void grant(Integer number, String identity, Permission permission, Operation operation, ObjectType object, AclRulePredicates predicates)
    {
        AclAction aclAction = new AclAction(operation, object, predicates);
        addRule(number, identity, permission, aclAction);
    }

    public boolean ruleExists(String identity, AclAction action)
    {
        for (Rule rule : _rules.values())
        {
            if (rule.getIdentity().equals(identity) && rule.getAclAction().equals(action))
            {
                return true;
            }
        }
        return false;
    }

    public void addRule(Integer number, String identity, Permission permission, AclAction action)
    {

        if (!action.isAllowed())
        {
            throw new IllegalArgumentException("Action is not allowed: " + action);
        }
        if (ruleExists(identity, action))
        {
            return;
        }

        // set rule number if needed
        Rule rule = new Rule(number, identity, action, permission);
        if (rule.getNumber() == null)
        {
            if (_rules.isEmpty())
            {
                rule.setNumber(0);
            }
            else
            {
                rule.setNumber(_rules.lastKey() + _increment);
            }
        }

        // save rule
        _cache.clear();
        _rules.put(rule.getNumber(), rule);
    }

    public void enableRule(int ruleNumber)
    {
        _rules.get(Integer.valueOf(ruleNumber)).enable();
    }

    public void disableRule(int ruleNumber)
    {
        _rules.get(Integer.valueOf(ruleNumber)).disable();
    }

    /** Return true if the name is well-formed (contains legal characters). */
    protected boolean checkName(String name)
    {
        for (int i = 0; i < name.length(); i++)
        {
            Character c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '@' && c != '.' && c != '/')
            {
                return false;
            }
        }
        return true;
    }

    /** Returns true if a username has the name[@domain][/realm] format  */
    protected boolean isvalidUserName(String name)
    {
        // check for '@' and '/' in name
        int atPos = name.indexOf(AT);
        int slashPos = name.indexOf(SLASH);
        boolean atFound = atPos != StringUtils.INDEX_NOT_FOUND && atPos == name.lastIndexOf(AT);
        boolean slashFound = slashPos != StringUtils.INDEX_NOT_FOUND && slashPos == name.lastIndexOf(SLASH);

        // must be at least one character after '@' or '/'
        if (atFound && atPos > name.length() - 2)
        {
            return false;
        }
        if (slashFound && slashPos > name.length() - 2)
        {
            return false;
        }

        // must be at least one character between '@' and '/'
        if (atFound && slashFound)
        {
            return (atPos < (slashPos - 1));
        }

        // otherwise all good
        return true;
    }

    /**
     * Checks for the case when the client's address is not known.
     *
     * @see #check(Subject, Operation, ObjectType, ObjectProperties, InetAddress)
     */
    public Result check(Subject subject, Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        return check(subject, operation, objectType, properties, null);
    }

    /**
     * Check the authorisation granted to a particular identity for an operation on an object type with
     * specific properties.
     *
     * Looks up the entire ruleset, which may be cached, for the user and operation and goes through the rules
     * in order to find the first one that matches. Either defers if there are no rules, returns the result of
     * the first match found, or denies access if there are no matching rules. Normally, it would be expected
     * to have a default deny or allow rule at the end of an access configuration however.
     */
    public Result check(Subject subject, Operation operation, ObjectType objectType, ObjectProperties properties, InetAddress addressOfClient)
    {
        ClientAction action = new ClientAction(operation, objectType, properties);

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Checking action: " + action);
        }

        // get the list of rules relevant for this request
        List<Rule> rules = getRules(subject, operation, objectType);
        if (rules == null)
        {
            if(_logger.isDebugEnabled())
            {
                _logger.debug("No rules found, returning default result");
            }
            return getDefault();
        }

        // Iterate through a filtered set of rules dealing with this identity and operation
        for (Rule rule : rules)
        {
            if(_logger.isDebugEnabled())
            {
                _logger.debug("Checking against rule: " + rule);
            }

            if (action.matches(rule.getAclAction(), addressOfClient))
            {
                Permission permission = rule.getPermission();

                switch (permission)
                {
                    case ALLOW_LOG:
                        getEventLogger().message(AccessControlMessages.ALLOWED(
                                action.getOperation().toString(),
                                action.getObjectType().toString(),
                                action.getProperties().toString()));
                    case ALLOW:
                        return Result.ALLOWED;
                    case DENY_LOG:
                        getEventLogger().message(AccessControlMessages.DENIED(
                                action.getOperation().toString(),
                                action.getObjectType().toString(),
                                action.getProperties().toString()));
                    case DENY:
                        return Result.DENIED;
                }

                return Result.DENIED;
            }
        }

        // Defer to the next plugin of this type, if it exists
        return Result.DEFER;
    }

    /** Default deny. */
    public Result getDefault()
    {
        if (isSet(DEFAULT_ALLOW))
        {
            return Result.ALLOWED;
        }
        if (isSet(DEFAULT_DENY))
        {
            return Result.DENIED;
        }
        return Result.ABSTAIN;
    }

    /**
     * Check if a configuration property is set.
     */
    protected boolean isSet(String key)
    {
        return BooleanUtils.isTrue(_config.get(key));
    }

    /**
     * Configure properties for the plugin instance.
     *
     * @param properties
     */
    public void configure(Map<String, Boolean> properties)
    {
        _config.putAll(properties);
    }

    /**
     * Configure a single property for the plugin instance.
     *
     * @param key
     * @param value
     */
    public void configure(String key, Boolean value)
    {
        _config.put(key, value);
    }

     /**
      * Returns all rules in the {@link RuleSet}.   Primarily intended to support unit-testing.
      * @return map of rules
      */
     public Map<Integer, Rule> getAllRules()
     {
         return Collections.unmodifiableMap(_rules);
     }

    private boolean isRelevant(final Set<Principal> principals, final Rule rule)
    {
        if (rule.getIdentity().equalsIgnoreCase(Rule.ALL))
        {
            return true;
        }
        else
        {
            for (Iterator<Principal> iterator = principals.iterator(); iterator.hasNext();)
            {
                final Principal principal = iterator.next();

                if (rule.getIdentity().equalsIgnoreCase(principal.getName()))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private Map<ObjectType, List<Rule>> getObjectToRuleCache(final Subject subject, final Operation operation)
    {
        // Lookup identity in cache and create empty operation map if required
        Map<Operation, Map<ObjectType, List<Rule>>> operations = _cache.get(subject);
        if (operations == null)
        {
            operations = new EnumMap<Operation, Map<ObjectType, List<Rule>>>(Operation.class);
            _cache.put(subject, operations);
        }

        // Lookup operation and create empty object type map if required
        Map<ObjectType, List<Rule>> objects = operations.get(operation);
        if (objects == null)
        {
            objects = new EnumMap<ObjectType, List<Rule>>(ObjectType.class);
            operations.put(operation, objects);
        }
        return objects;
    }

    public EventLogger getEventLogger()
    {
        return _eventLogger.getEventLogger();
    }
}
