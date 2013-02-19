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
package org.apache.qpid.server.security.acl;

import org.apache.qpid.management.common.mbeans.ServerInformation;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.test.utils.JMXTestUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * Tests that access to the JMX interface is governed only by {@link ObjectType#METHOD}/{@link ObjectType#ALL}
 * rules and AMQP rights have no effect.
 *
 * Ensures that objects outside the Qpid domain are not governed by the ACL model.
 */
public class ExternalACLJMXTest extends AbstractACLTestCase
{

    private JMXTestUtils _jmx;

    private static final String TEST_QUEUE_OWNER = "admin";
    private static final String TEST_VHOST = "test";
    private static final String TEST2_VHOST = "test2";

    @Override
    public void setUp() throws Exception
    {
        createTestVirtualHost(0, TEST_VHOST);
        createTestVirtualHost(0, TEST2_VHOST);

        _jmx = new JMXTestUtils(this);
        _jmx.setUp();
        super.setUp();
        _jmx.open();
    }
    
    @Override
    public void tearDown() throws Exception
    {
        _jmx.close();
        super.tearDown();
    }

    public void setUpDenyAllIsCatchAllRule() throws Exception
    {
        writeACLFile(null,
                "ACL ALLOW admin ACCESS MANAGEMENT",
                "#No more rules, default catch all (deny all) should apply");
    }

    public void testDenyAllIsCatchAllRule() throws Exception
    {
        //try a broker-level method
        ServerInformation info = _jmx.getServerInformation();
        try
        {
            info.resetStatistics();
            fail("Exception not thrown");
        }
        catch (SecurityException e)
        {
            assertEquals("Cause message incorrect", "Permission denied: Update resetStatistics", e.getMessage());
        }

        //try a vhost-level method
        try
        {
            _jmx.createQueue(TEST_VHOST, getTestQueueName(), TEST_QUEUE_OWNER, true);
            fail("Exception not thrown");
        }
        catch (Exception e)
        {
            assertEquals("Cause message incorrect", "Permission denied: Update createNewQueue", e.getMessage());
        }

        // Ensure that calls to MBeans outside the Qpid domain are not impeded.
        final RuntimeMXBean runtimeBean = _jmx.getManagedObject(RuntimeMXBean.class, ManagementFactory.RUNTIME_MXBEAN_NAME);
        runtimeBean.getName();
        // PASS
    }

    /**
     * Ensure an ALLOW ALL ALL rule allows access to both getters/setters.
     */
    public void setUpAllowAll() throws Exception
    {
        writeACLFile(null, "ACL ALLOW ALL ALL");
    }

    public void testAllowAll() throws Exception
    {
        ServerInformation info = _jmx.getServerInformation();
        info.getBuildVersion(); // getter - requires ACCESS
        info.resetStatistics(); // setter - requires UPDATE
        // PASS
    }

    /**
     * admin user is denied at broker level but allowed at vhost level.
     */
    public void setUpVhostAllowOverridesGlobalDeny() throws Exception
    {
        writeACLFile(null,
                "ACL ALLOW admin ACCESS MANAGEMENT",
                "ACL DENY admin UPDATE METHOD component='VirtualHost.VirtualHostManager' name='createNewQueue'");
        writeACLFile(TEST_VHOST,
                "ACL ALLOW admin UPDATE METHOD component='VirtualHost.VirtualHostManager' name='createNewQueue'");
    }

    public void testVhostAllowOverridesGlobalDeny() throws Exception
    {
        //try a vhost-level method on the allowed vhost
        _jmx.createQueue(TEST_VHOST, getTestQueueName(), TEST_QUEUE_OWNER, true);

        //try a vhost-level method on a different vhost
        try
        {
            _jmx.createQueue(TEST2_VHOST, getTestQueueName(), TEST_QUEUE_OWNER, true);
            fail("Exception not thrown");
        }
        catch (SecurityException e)
        {
            assertEquals("Cause message incorrect", "Permission denied: Update createNewQueue", e.getMessage());
        }
    }


    /**
     * admin user is allowed all update methods on the component at broker level.
     */
    public void setUpUpdateComponentOnlyAllow() throws Exception
    {
        writeACLFile(null,
                "ACL ALLOW admin ACCESS MANAGEMENT",
                "ACL ALLOW admin UPDATE METHOD component='VirtualHost.VirtualHostManager'");
    }

    public void testUpdateComponentOnlyAllow() throws Exception
    {
        _jmx.createQueue(TEST_VHOST, getTestQueueName(), TEST_QUEUE_OWNER, true);
        // PASS
        _jmx.deleteQueue(TEST_VHOST, getTestQueueName());
        // PASS
    }


    /**
     * admin user is allowed all update methods on all components at broker level.
     */
    public void setUpUpdateMethodOnlyAllow() throws Exception
    {
        writeACLFile(null,
                "ACL ALLOW admin ACCESS MANAGEMENT",
                "ACL ALLOW admin UPDATE METHOD");
    }

    public void testUpdateMethodOnlyAllow() throws Exception
    {
        _jmx.createQueue(TEST_VHOST, getTestQueueName(), TEST_QUEUE_OWNER, true);
        //PASS
        _jmx.deleteQueue(TEST_VHOST, getTestQueueName());
        // PASS
    }


    /**
     * admin user has JMX right, AMPQ right is irrelevant.
     */
    public void setUpCreateQueueSuccess() throws Exception
    {
        writeACLFile(null, "ACL ALLOW admin ACCESS MANAGEMENT");
        writeACLFile(TEST_VHOST, "ACL ALLOW admin UPDATE METHOD component='VirtualHost.VirtualHostManager' name='createNewQueue'");
    }

    public void testCreateQueueSuccess() throws Exception
    {
        _jmx.createQueue(TEST_VHOST, getTestQueueName(), TEST_QUEUE_OWNER, true);
    }


    /**
     * admin user has JMX right, verifies lack of AMPQ rights is irrelevant.
     */
    public void setUpCreateQueueSuccessNoAMQPRights() throws Exception
    {
        writeACLFile(null, "ACL ALLOW admin ACCESS MANAGEMENT");
        writeACLFile(TEST_VHOST,
                "ACL ALLOW admin UPDATE METHOD component='VirtualHost.VirtualHostManager' name='createNewQueue'",
                "ACL DENY admin CREATE QUEUE");
    }

    public void testCreateQueueSuccessNoAMQPRights() throws Exception
    {
        _jmx.createQueue(TEST_VHOST, getTestQueueName(), TEST_QUEUE_OWNER, true);
    }


    /**
     * admin user does not have JMX right, AMPQ right is irrelevant.
     */
    public void setUpCreateQueueDenied() throws Exception
    {
        writeACLFile(null, "ACL ALLOW admin ACCESS MANAGEMENT");
        writeACLFile(TEST_VHOST,
                "ACL DENY admin UPDATE METHOD component='VirtualHost.VirtualHostManager' name='createNewQueue'");
    }

    public void testCreateQueueDenied() throws Exception
    {
        try
        {
            _jmx.createQueue(TEST_VHOST, getTestQueueName(), TEST_QUEUE_OWNER, true);
            fail("Exception not thrown");
        }
        catch (SecurityException e)
        {
            assertEquals("Cause message incorrect", "Permission denied: Update createNewQueue", e.getMessage());
        }
    }


    /**
     * admin user does not have JMX right
     */
    public void setUpServerInformationUpdateDenied() throws Exception
    {
        writeACLFile(null,
                "ACL ALLOW admin ACCESS MANAGEMENT",
                "ACL DENY admin UPDATE METHOD component='ServerInformation' name='resetStatistics'");
    }

    public void testServerInformationUpdateDenied() throws Exception
    {
        ServerInformation info = _jmx.getServerInformation();
        try
        {
            info.resetStatistics();
            fail("Exception not thrown");
        }
        catch (SecurityException e)
        {
            assertEquals("Cause message incorrect", "Permission denied: Update resetStatistics", e.getMessage());
        }
    }


    /**
     * admin user has JMX right to check management API major version (but not minor version)
     */
    public void setUpServerInformationAccessGranted() throws Exception
    {
        writeACLFile(null,
        "ACL ALLOW admin ACCESS MANAGEMENT",
        "ACL ALLOW-LOG admin ACCESS METHOD component='ServerInformation' name='getManagementApiMajorVersion'");
    }

    public void testServerInformationAccessGranted() throws Exception
    {
        ServerInformation info = _jmx.getServerInformation();
        info.getManagementApiMajorVersion();

        try
        {
            info.getManagementApiMinorVersion();
            fail("Exception not thrown");
        }
        catch (SecurityException e)
        {
            assertEquals("Cause message incorrect", "Permission denied: Access getManagementApiMinorVersion", e.getMessage());
        }
    }


    /**
     * admin user has JMX right to use the update method
     */
    public void setUpServerInformationUpdateMethodPermission() throws Exception
    {
        writeACLFile(null,
                "ACL ALLOW admin ACCESS MANAGEMENT",
                "ACL ALLOW admin UPDATE METHOD component='ServerInformation' name='resetStatistics'");
    }

    public void testServerInformationUpdateMethodPermission() throws Exception
    {
        ServerInformation info = _jmx.getServerInformation();
        info.resetStatistics();
        // PASS
    }


    /**
     * admin user has JMX right to use all types of method on ServerInformation
     */
    public void setUpServerInformationAllMethodPermissions() throws Exception
    {
        writeACLFile(null,
                "ACL ALLOW admin ACCESS MANAGEMENT",
                "ACL ALLOW admin ALL METHOD component='ServerInformation'");
    }

    public void testServerInformationAllMethodPermissions() throws Exception
    {
        //try an update method
        ServerInformation info = _jmx.getServerInformation();
        info.resetStatistics();
        // PASS
        //try an access method
        info.getManagementApiMinorVersion();
        // PASS
    }

}
