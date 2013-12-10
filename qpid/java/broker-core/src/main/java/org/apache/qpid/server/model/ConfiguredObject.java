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
package org.apache.qpid.server.model;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * An object that can be "managed" (eg via the web interface) and usually read from configuration.
 */
public interface ConfiguredObject
{

    /**
     * Get the universally unique identifier for the object
     *
     * @return the objects id
     */
    UUID getId();

    /**
     * Get the name of the object
     *
     * @return the name of the object
     */
    String getName();


    /**
     * Attempt to change the name of the object
     *
     * Request a change to the name of the object.  The caller must pass in the name it believes the object currently
     * has. If the current name differs from this expected value, then no name change will occur
     *
     * @param currentName the name the caller believes the object to have
     * @param desiredName the name the caller would like the object to have
     * @return the new name for the object
     * @throws IllegalStateException if the name of the object may not be changed in in the current state
     * @throws AccessControlException if the current context does not have permission to change the name
     * @throws IllegalArgumentException if the provided name is not legal
     * @throws NullPointerException if the desired name is null
     */
    String setName(String currentName, String desiredName) throws IllegalStateException,
                                                                  AccessControlException;


    /**
     * Get the desired state of the object.
     *
     * This is the state set at the object itself, however the object
     * may not be able attain this state if one of its ancestors is in a different state (in particular a descendant
     * object may not be ACTIVE if all of its ancestors are not also ACTIVE).
     *
     * @return the desired state of the object
     */
    State getDesiredState();

    /**
     * Change the desired state of the object
     *
     * Request a change to the current state. The caller must pass in the state it believe the object to be in, if
     * this differs from the current desired state when the object evalues the request, then no state change will occur.
     *
     * @param currentState the state the caller believes the object to be in
     * @param desiredState the state the caller wishes the object to attain
     * @return the new current state
     * @throws IllegalStateTransitionException  the requested state tranisition is invalid
     * @throws AccessControlException the current context does not have sufficient permissions to change the state
     */
    State setDesiredState(State currentState, State desiredState) throws IllegalStateTransitionException,
                                                                         AccessControlException;

    /**
     * Get the actual state of the object.
     *
     * This state is derived from the desired state of the object itself and
     * the actual state of its parents. If an object "desires" to be ACTIVE, but one of its parents is STOPPED, then
     * the actual state of the object will be STOPPED
     *
     * @return the actual state of the object
     */
    State getActualState();


    /**
     * Add a listener which will be informed of all changes to this configuration object
     *
     * @param listener the listener to add
     */
    void addChangeListener(ConfigurationChangeListener listener);

    /**
     * Remove a change listener
     *
     *
     * @param listener the listener to remove
     * @return true iff a listener was removed
     */
    boolean removeChangeListener(ConfigurationChangeListener listener);

    /**
     * Get the parent of the given type for this object
     *
     * @param clazz the class of parent being asked for
     * @return the objects parent
     */
    <T extends ConfiguredObject> T getParent(Class<T> clazz);


    /**
     * Returns whether the the object configuration is durably stored
     *
     * @return the durability
     */
    boolean isDurable();

    /**
     * Sets the durability of the object
     *
     * @param durable true iff the caller wishes the object to store its configuration durably
     *
     * @throws IllegalStateException if the durability cannot be changed in the current state
     * @throws AccessControlException if the current context does not have sufficient permission to change the durability
     * @throws IllegalArgumentException if the object does not support the requested durability
     */
    void setDurable(boolean durable) throws IllegalStateException,
                                            AccessControlException,
                                            IllegalArgumentException;

    /**
     * Return the lifetime policy for the object
     *
     * @return the lifetime policy
     */
    LifetimePolicy getLifetimePolicy();

    /**
     * Set the lifetime policy of the object
     *
     * @param expected The lifetime policy the caller believes the object currently has
     * @param desired The lifetime policy the caller desires the object to have
     * @return the new lifetime policy
     * @throws IllegalStateException if the lifetime policy cannot be changed in the current state
     * @throws AccessControlException if the caller does not have permission to change the lifetime policy
     * @throws IllegalArgumentException if the object does not support the requested lifetime policy
     */
    LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired) throws IllegalStateException,
                                                                                             AccessControlException,
                                                                                             IllegalArgumentException;

    /**
     * Get the time the object will live once the lifetime policy conditions are no longer fulfilled
     *
     * @return the time to live
     */
    long getTimeToLive();

    /**
     * Set the ttl value
     *
     * @param expected the ttl the caller believes the object currently has
     * @param desired the ttl value the caller
     * @return the new ttl value
     * @throws IllegalStateException if the ttl cannot be set in the current state
     * @throws AccessControlException if the caller does not have permission to change the ttl
     * @throws IllegalArgumentException if the object does not support the requested ttl value
     */
    long setTimeToLive(long expected, long desired) throws IllegalStateException,
                                                           AccessControlException,
                                                           IllegalArgumentException;

    /**
     * Get the names of attributes that are set on this object
     *
     * Note that the returned collection is correct at the time the method is called, but will not reflect future
     * additions or removals when they occur
     *
     * @return the collection of attribute names
     */
    Collection<String> getAttributeNames();


    /**
     * Return the value for the given attribute name. The actual attribute value
     * is returned if the configured object has such attribute set. If not, the
     * value is looked default attributes.
     *
     * @param name
     *            the name of the attribute
     * @return the value of the attribute at the object (or null if the
     *         attribute value is set neither on object itself no in defaults)
     */
    Object getAttribute(String name);

    /**
     * Return the map containing only explicitly set attributes
     *
     * @return the map with the attributes
     */
    Map<String, Object> getActualAttributes();

    /**
     * Set the value of an attribute
     *
     * @param name the name of the attribute to be set
     * @param expected the value the caller believes the attribute currently has (or null if it is expected to be unset)
     * @param desired the desired value for the attribute (or null to unset the attribute)
     * @return the new value for the given attribute
     * @throws IllegalStateException if the attribute cannot be set while the object is in its current state
     * @throws AccessControlException if the caller does not have permission to alter the value of the attribute
     * @throws IllegalArgumentException if the provided value is not valid for the given argument
     */
    Object setAttribute(String name, Object expected, Object desired) throws IllegalStateException,
                                                                             AccessControlException,
                                                                             IllegalArgumentException;


    /**
     * Return the Statistics holder for the ConfiguredObject
     *
     * @return the Statistics holder for the ConfiguredObject (or null if none exists)
     */
    Statistics getStatistics();

    /**
     * Return children of the ConfiguredObject of the given class
     *
     * @param clazz the class of the children to return
     * @return the children
     *
     * @throws NullPointerException if the supplied class null
     *
     */
    <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz);


    <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                               Map<String, Object> attributes,
                                               ConfiguredObject... otherParents);

    void setAttributes(Map<String, Object> attributes) throws IllegalStateException, AccessControlException, IllegalArgumentException;
}
