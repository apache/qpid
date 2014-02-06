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

package org.apache.qpid.server.jmx;

import javax.management.JMException;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.Pluggable;
import org.apache.qpid.server.plugin.QpidServiceLoader;

/**
 * A provider of an mbean implementation.
 *
 * Provider implementations are advertised as services and loaded by a {@link QpidServiceLoader}.
 */
public interface MBeanProvider extends Pluggable
{
    /**
     * Tests whether a <code>child</code> can be managed by the mbean
     * provided by this provider.
     */
    boolean isChildManageableByMBean(ConfiguredObject child);

    /**
     * Creates a mbean for this child.  This method should only be called if
     * {@link #isChildManageableByMBean(ConfiguredObject)} has previously returned true.
     *
     * @return newly created mbean
     */
    ManagedObject createMBean(ConfiguredObject child, ManagedObject parent) throws JMException;

}
