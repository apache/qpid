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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.WeakHashMap;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.Permission;
import org.apache.qpid.server.security.access.logging.AccessControlMessages;

/**
 * Models the rule configuration for the access control plugin.
 *
 * The access control rule definitions are loaded from an external configuration file, passed in as the
 * target to the {@link load(ConfigurationFile)} method. The file specified 
 */
public class RuleSet
{
    private static final Logger _logger = Logger.getLogger(RuleSet.class);
    
    private static final String AT = "@";
	private static final String SLASH = "/";

	public static final String DEFAULT_ALLOW = "defaultallow";
	public static final String DEFAULT_DENY = "defaultdeny";
	public static final String TRANSITIVE = "transitive";
	public static final String EXPAND = "expand";
    public static final String AUTONUMBER = "autonumber";
    public static final String CONTROLLED = "controlled";
    public static final String VALIDATE = "validate";
    
    public static final List<String> CONFIG_PROPERTIES = Arrays.asList(
            DEFAULT_ALLOW, DEFAULT_DENY, TRANSITIVE, EXPAND, AUTONUMBER, CONTROLLED
        );
    
    private static final Integer _increment = 10;
	
    private final Map<String, List<String>> _groups = new HashMap<String, List<String>>();
    private final SortedMap<Integer, Rule> _rules = new TreeMap<Integer, Rule>();
    private final Map<String, Map<Operation, Map<ObjectType, List<Rule>>>> _cache =
                        new WeakHashMap<String, Map<Operation, Map<ObjectType, List<Rule>>>>();
    private final Map<String, Boolean> _config = new HashMap<String, Boolean>();
    
    public RuleSet()
    {
        // set some default configuration properties
        configure(DEFAULT_DENY, Boolean.TRUE);
        configure(TRANSITIVE, Boolean.TRUE);
    }
    
    /**
     * Clear the contents, invluding groups, rules and configuration.
     */
    public void clear()
    {
        _rules.clear();
        _cache.clear();
        _config.clear();
        _groups.clear();
    }
    
    public int getRuleCount()
    {
        return _rules.size();
    }
	
	/**
	 * Filtered rules list based on an identity and operation.
	 * 
	 * Allows only enabled rules with identity equal to all, the same, or a group with identity as a member,
	 * and operation is either all or the same operation.
	 */		
	public List<Rule> getRules(String identity, Operation operation, ObjectType objectType)
	{
        // Lookup identity in cache and create empty operation map if required
		Map<Operation, Map<ObjectType, List<Rule>>> operations = _cache.get(identity);		
		if (operations == null)
		{	
			operations = new EnumMap<Operation, Map<ObjectType, List<Rule>>>(Operation.class);
			_cache.put(identity, operations);
		}
		
        // Lookup operation and create empty object type map if required        
        Map<ObjectType, List<Rule>> objects = operations.get(operation);
		if (objects == null)
		{
            objects = new EnumMap<ObjectType, List<Rule>>(ObjectType.class);
            operations.put(operation, objects);
        }

        // Lookup object type rules for the operation
        if (!objects.containsKey(objectType))
        {
            boolean controlled = false;
            List<Rule> filtered = new LinkedList<Rule>();
            for (Rule rule : _rules.values())
            {
                if (rule.isEnabled()
                    && (rule.getAction().getOperation() == Operation.ALL || rule.getAction().getOperation() == operation)
                    && (rule.getAction().getObjectType() == ObjectType.ALL || rule.getAction().getObjectType() == objectType))
                {
                    controlled = true;

                    if (rule.getIdentity().equalsIgnoreCase(Rule.ALL)
                        || rule.getIdentity().equalsIgnoreCase(identity)
                        || (_groups.containsKey(rule.getIdentity()) && _groups.get(rule.getIdentity()).contains(identity)))
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
        }
		
        // Return the cached rules
		return objects.get(objectType);
	}
    
    public boolean isValidNumber(Integer number)
    {
        return !_rules.containsKey(number);
    }
	
    public void grant(Integer number, String identity, Permission permission, Operation operation)
    {
        Action action = new Action(operation);
        addRule(number, identity, permission, action);
    }
    
    public void grant(Integer number, String identity, Permission permission, Operation operation, ObjectType object, ObjectProperties properties)
    {
        Action action = new Action(operation, object, properties);
        addRule(number, identity, permission, action);
    }
    
    public boolean ruleExists(String identity, Action action)
    {
		for (Rule rule : _rules.values())
		{
		    if (rule.getIdentity().equals(identity) && rule.getAction().equals(action))
		    {
		        return true;
		    }
		}
		return false;
    }
    
    private Permission noLog(Permission permission)
    {
        switch (permission)
        {
            case ALLOW:
            case ALLOW_LOG:
                return Permission.ALLOW;
            case DENY:
            case DENY_LOG:
            default:
                return Permission.DENY;
        }
    }

    // TODO make this work when group membership is not known at file parse time
    public void addRule(Integer number, String identity, Permission permission, Action action)
    {
		if (!action.isAllowed())
		{
			throw new IllegalArgumentException("Action is not allowd: " + action);
		}
        if (ruleExists(identity, action))
        {
            return;
        }
        
        // expand actions - possibly multiply number by
        if (isSet(EXPAND))
        {
            if (action.getOperation() == Operation.CREATE && action.getObjectType() == ObjectType.TOPIC)
            {
                addRule(null, identity, noLog(permission), new Action(Operation.BIND, ObjectType.EXCHANGE,
                        new ObjectProperties("amq.topic", action.getProperties().get(ObjectProperties.Property.NAME))));
                ObjectProperties topicProperties = new ObjectProperties();
                topicProperties.put(ObjectProperties.Property.DURABLE, true);
                addRule(null, identity, permission, new Action(Operation.CREATE, ObjectType.QUEUE, topicProperties));
                return;
            }
            if (action.getOperation() == Operation.DELETE && action.getObjectType() == ObjectType.TOPIC)
            {
                addRule(null, identity, noLog(permission), new Action(Operation.UNBIND, ObjectType.EXCHANGE,
                        new ObjectProperties("amq.topic", action.getProperties().get(ObjectProperties.Property.NAME))));
                ObjectProperties topicProperties = new ObjectProperties();
                topicProperties.put(ObjectProperties.Property.DURABLE, true);
                addRule(null, identity, permission, new Action(Operation.DELETE, ObjectType.QUEUE, topicProperties));
                return;
            }
        }
        
		// transitive action dependencies
        if (isSet(TRANSITIVE))
        {
            if (action.getOperation() == Operation.CREATE && action.getObjectType() == ObjectType.QUEUE)
            {
                ObjectProperties exchProperties = new ObjectProperties(action.getProperties());
                exchProperties.setName(ExchangeDefaults.DEFAULT_EXCHANGE_NAME);
                exchProperties.put(ObjectProperties.Property.ROUTING_KEY, action.getProperties().get(ObjectProperties.Property.NAME));
                addRule(null, identity, noLog(permission), new Action(Operation.BIND, ObjectType.EXCHANGE, exchProperties));
				if (action.getProperties().isSet(ObjectProperties.Property.AUTO_DELETE))
				{
					addRule(null, identity, noLog(permission), new Action(Operation.DELETE, ObjectType.QUEUE, action.getProperties()));
				}
            }
            else if (action.getOperation() == Operation.DELETE && action.getObjectType() == ObjectType.QUEUE)
            {
                ObjectProperties exchProperties = new ObjectProperties(action.getProperties());
                exchProperties.setName(ExchangeDefaults.DEFAULT_EXCHANGE_NAME);
                exchProperties.put(ObjectProperties.Property.ROUTING_KEY, action.getProperties().get(ObjectProperties.Property.NAME));
                addRule(null, identity, noLog(permission), new Action(Operation.UNBIND, ObjectType.EXCHANGE, exchProperties));
            }
            else if (action.getOperation() != Operation.ACCESS && action.getObjectType() != ObjectType.VIRTUALHOST)
            {
                addRule(null, identity, noLog(permission), new Action(Operation.ACCESS, ObjectType.VIRTUALHOST));
            }
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
        _cache.remove(identity);
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
    
    public boolean addGroup(String group, List<String> constituents)
    {
        if (_groups.containsKey(group))
        {
            // cannot redefine
            return false;
        }
        else
        {
            _groups.put(group, new ArrayList<String>());
        }
        
        for (String name : constituents)
        {
            if (name.equalsIgnoreCase(group))
            {
                // recursive definition
                return false;
            }
            
            if (!checkName(name))
            {
                // invalid name
                return false;
            }
            
            if (_groups.containsKey(name))
            {
                // is a group
                _groups.get(group).addAll(_groups.get(name));
            }
            else
            {
                // is a user
                if (!isvalidUserName(name))
                {
                    // invalid username
                    return false;
                }
                _groups.get(group).add(name);
            }
        }
        return true;
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
		// check for '@' and '/' in namne
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

	// C++ broker authorise function prototype
    // virtual bool authorise(const std::string& id, const Action& action, const ObjectType& objType,
	//		const std::string& name, std::map<Property, std::string>* params=0);
	
	// Possibly add a String name paramater?

    /**
     * Check the authorisation granted to a particular identity for an operation on an object type with
     * specific properties.
     *
     * Looks up the entire ruleset, whcih may be cached, for the user and operation and goes through the rules
     * in order to find the first one that matches. Either defers if there are no rules, returns the result of
     * the first match found, or denies access if there are no matching rules. Normally, it would be expected
     * to have a default deny or allow rule at the end of an access configuration however.
     */
    public Result check(String identity, Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        // Create the action to check
        Action action = new Action(operation, objectType, properties);

		// get the list of rules relevant for this request
		List<Rule> rules = getRules(identity, operation, objectType);
		if (rules == null)
		{
		    if (isSet(CONTROLLED))
		    {
    		    // Abstain if there are no rules for this operation
                return Result.ABSTAIN;
		    }
		    else
		    {
		        return getDefault();
		    }
		}
		
		// Iterate through a filtered set of rules dealing with this identity and operation
        for (Rule current : rules)
		{
			// Check if action matches
            if (action.matches(current.getAction()))
            {
                Permission permission = current.getPermission();
                
                switch (permission)
                {
                    case ALLOW_LOG:
                        CurrentActor.get().message(AccessControlMessages.ALLOWED(
                                action.getOperation().toString(), action.getObjectType().toString(), action.getProperties().toString()));
                    case ALLOW:
                        return Result.ALLOWED;
                    case DENY_LOG:
                        CurrentActor.get().message(AccessControlMessages.DENIED(
                                action.getOperation().toString(), action.getObjectType().toString(), action.getProperties().toString()));
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
}
