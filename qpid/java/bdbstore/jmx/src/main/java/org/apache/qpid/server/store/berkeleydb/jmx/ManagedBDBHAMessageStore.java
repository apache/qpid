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
 *
 */
package org.apache.qpid.server.store.berkeleydb.jmx;

import java.io.IOException;

import javax.management.JMException;
import javax.management.openmbean.TabularData;

import org.apache.qpid.management.common.mbeans.annotations.MBeanAttribute;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;

public interface ManagedBDBHAMessageStore
{
    public static final String TYPE = "BDBHAMessageStore";

    public static final String ATTR_GROUP_NAME = "GroupName";
    public static final String ATTR_NODE_NAME = "NodeName";
    public static final String ATTR_NODE_HOST_PORT = "NodeHostPort";
    public static final String ATTR_HELPER_HOST_PORT = "HelperHostPort";
    public static final String ATTR_DURABILITY = "Durability";
    public static final String ATTR_NODE_STATE = "NodeState";
    public static final String ATTR_DESIGNATED_PRIMARY = "DesignatedPrimary";
    public static final String ATTR_COALESCING_SYNC = "CoalescingSync";

    public static final String GRP_MEM_COL_NODE_HOST_PORT = "NodeHostPort";
    public static final String GRP_MEM_COL_NODE_NAME = "NodeName";

    @MBeanAttribute(name=ATTR_GROUP_NAME, description="Name identifying the group")
    String getGroupName() throws IOException, JMException;

    @MBeanAttribute(name=ATTR_NODE_NAME, description="Unique name identifying the node within the group")
    String getNodeName() throws IOException, JMException;

    @MBeanAttribute(name=ATTR_NODE_HOST_PORT, description="Host/port used to replicate data between this node and others in the group")
    String getNodeHostPort() throws IOException, JMException;

    @MBeanAttribute(name=ATTR_NODE_STATE, description="Current state of this node")
    String getNodeState() throws IOException, JMException;

    @MBeanAttribute(name=ATTR_HELPER_HOST_PORT, description="Host/port used to allow a new node to discover other group members")
    String getHelperHostPort() throws IOException, JMException;

    @MBeanAttribute(name=ATTR_DURABILITY, description="Durability")
    String getDurability() throws IOException, JMException;

    @MBeanAttribute(name=ATTR_DESIGNATED_PRIMARY, description="Designated primary flag. Applicable to the two node case.")
    boolean getDesignatedPrimary() throws IOException, JMException;

    @MBeanAttribute(name=ATTR_COALESCING_SYNC, description="Coalescing sync flag. Applicable to the master sync policies NO_SYNC and WRITE_NO_SYNC only.")
    boolean getCoalescingSync() throws IOException, JMException;

    @MBeanAttribute(name="getAllNodesInGroup", description="Get all nodes within the group, regardless of whether currently attached or not")
    TabularData getAllNodesInGroup() throws IOException, JMException;

    @MBeanOperation(name="removeNodeFromGroup", description="Remove an existing node from the group")
    void removeNodeFromGroup(@MBeanOperationParameter(name="nodeName", description="name of node")String nodeName) throws JMException;

    @MBeanOperation(name="setDesignatedPrimary", description="Set/unset this node as the designated primary for the group. Applicable to the two node case.")
    void setDesignatedPrimary(@MBeanOperationParameter(name="primary", description="designated primary")boolean primary) throws JMException;

    @MBeanOperation(name="updateAddress", description="Update the address of another node. The node must be in a STOPPED state.")
    void updateAddress(@MBeanOperationParameter(name="nodeName", description="name of node")String nodeName,
                       @MBeanOperationParameter(name="newHostName", description="new hostname")String newHostName,
                       @MBeanOperationParameter(name="newPort", description="new port number")int newPort) throws JMException;
}

