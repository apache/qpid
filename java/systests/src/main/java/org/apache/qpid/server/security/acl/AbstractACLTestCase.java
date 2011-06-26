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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.naming.NamingException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.lang.StringUtils;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.URLSyntaxException;

/**
 * Abstract test case for ACLs.
 * 
 * This base class contains convenience methods to mange ACL files and implements a mechanism that allows each
 * test method to run its own setup code before the broker starts.
 * 
 * TODO move the pre broker-startup setup method invocation code to {@link QpidBrokerTestCase}
 * 
 * @see ExternalACLTest
 * @see ExternalACLFileTest
 * @see ExternalACLJMXTest
 * @see ExternalAdminACLTest
 * @see ExhaustiveACLTest
 */
public abstract class AbstractACLTestCase extends QpidBrokerTestCase implements ConnectionListener
{
    /** Used to synchronise {@link #tearDown()} when exceptions are thrown */
	protected CountDownLatch _exceptionReceived;
	
    /** Override this to return the name of the configuration XML file. */
    public abstract String getConfig();
    
    /** Override this to setup external ACL files for virtual hosts. */
    public List<String> getHostList()
    {
        return Collections.emptyList();
    }
    
    /**
     * This setup method checks {@link #getConfig()} and {@link #getHostList()} to initialise the broker with specific
     * ACL configurations and then runs an optional per-test setup method, which is simply a method with the same name
     * as the test, but starting with {@code setUp} rather than {@code test}.
     * 
     * @see #setUpACLFile(String)
     * @see org.apache.qpid.test.utils.QpidBrokerTestCase#setUp()
     */
    @Override
    public void setUp() throws Exception
    {
        if (QpidHome == null)
        {
            fail("QPID_HOME not set");
        }

        // Initialise ACLs.
        _configFile = new File(QpidHome, "etc" + File.separator + getConfig());
        
        // Initialise ACL files
        for (String virtualHost : getHostList())
        {
            setUpACLFile(virtualHost);
        }
        
        // run test specific setup
        String testSetup = StringUtils.replace(getName(), "test", "setUp");
        try
        {
            Method setup = getClass().getDeclaredMethod(testSetup);
            setup.invoke(this);
        }
        catch (NoSuchMethodException e)
        {
            // Ignore
        }
        catch (InvocationTargetException e)
        {
            throw (Exception) e.getTargetException();
        }
        
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        catch (JMSException e)
        {
            //we're throwing this away as it can happen in this test as the state manager remembers exceptions
            //that we provoked with authentication failures, where the test passes - we can ignore on con close
        }
    }
    
    /**
     * Configures specific ACL files for a virtual host.
     * 
     * This method checks for ACL files that exist on the filesystem. If dynamically generatyed ACL files are required in a test, 
     * then it is easier to use the {@code setUp} prefix on a method to generate the ACL file. In order, this method looks
     * for three files:
     * <ol>
     * <li><em>virtualhost</em>-<em>class</em>-<em>test</em>.txt
     * <li><em>virtualhost</em>-<em>class</em>.txt
     * <li><em>virtualhost</em>-default.txt
     * </ol>
     * The <em>class</em> and <em>test</em> parts are the test class and method names respectively, with the word {@code test}
     * removed and the rest of the text converted to lowercase. For example, the test class and method named
     * {@code org.apache.qpid.test.AccessExampleTest#testExampleMethod} on the {@code testhost} virtualhost would use
     * one of the following files:
     * <ol>
     * <li>testhost-accessexample-examplemethod.txt
     * <li>testhost-accessexample.txt
     * <li>testhost-default.txt
     * </ol>
     * These files should be copied to the <em>${QPID_HOME}/etc</em> directory when the test is run.
     * 
     * @see #writeACLFile(String, String...)
     */
    public void setUpACLFile(String virtualHost) throws IOException, ConfigurationException
    {
        String path = QpidHome + File.separator + "etc";
        String className = StringUtils.substringBeforeLast(getClass().getSimpleName().toLowerCase(), "test");
        String testName = StringUtils.substringAfter(getName(), "test").toLowerCase();
        
        File aclFile = new File(path, virtualHost + "-" + className + "-" + testName + ".txt");        
        if (!aclFile.exists())
        {
            aclFile = new File(path, virtualHost + "-" + className + ".txt");      
            if (!aclFile.exists())
            {
                aclFile = new File(path, virtualHost + "-" + "default.txt");
            }
        }
        
        // Set the ACL file configuration property
		if (virtualHost.equals("global"))
		{
			setConfigurationProperty("security.aclv2", aclFile.getAbsolutePath());
		}
		else
		{
			setConfigurationProperty("virtualhosts.virtualhost." + virtualHost + ".security.aclv2", aclFile.getAbsolutePath());
		}
    }

    public void writeACLFile(String vhost, String...rules) throws ConfigurationException, IOException
    {
        File aclFile = File.createTempFile(getClass().getSimpleName(), getName());
        aclFile.deleteOnExit();

        if ("global".equals(vhost))
        {
	        setConfigurationProperty("security.aclv2", aclFile.getAbsolutePath());
        }
        else
        {
	        setConfigurationProperty("virtualhosts.virtualhost." + vhost + ".security.aclv2", aclFile.getAbsolutePath());
        }

        PrintWriter out = new PrintWriter(new FileWriter(aclFile));
        out.println(String.format("# %s", _testName));
        for (String line : rules)
        {
            out.println(line);
        }
        out.close();
    }

    /**
     * Creates a connection to the broker, and sets a connection listener to prevent failover and an exception listener 
     * with a {@link CountDownLatch} to synchronise in the {@link #check403Exception(Throwable)} method and allow the
     * {@link #tearDown()} method to complete properly.
     */
    public Connection getConnection(String vhost, String username, String password) throws NamingException, JMSException, URLSyntaxException
    {
        AMQConnection connection = (AMQConnection) getConnection(createConnectionURL(vhost, username, password));

        //Prevent Failover
        connection.setConnectionListener(this);
        
        //QPID-2081: use a latch to sync on exception causing connection close, to work 
        //around the connection close race during tearDown() causing sporadic failures
        _exceptionReceived = new CountDownLatch(1);

        connection.setExceptionListener(new ExceptionListener()
        {
            public void onException(JMSException e)
            {
                _exceptionReceived.countDown();
            }
        });

        return (Connection) connection;
    }

    // Connection Listener Interface - Used here to block failover

    public void bytesSent(long count)
    {
    }

    public void bytesReceived(long count)
    {
    }

    public boolean preFailover(boolean redirect)
    {
        //Prevent failover.
        return false;
    }

    public boolean preResubscribe()
    {
        return false;
    }

    public void failoverComplete()
    {
    }

    /**
     * Convenience method to build an {@link AMQConnectionURL} with the right parameters.
     */
    public AMQConnectionURL createConnectionURL(String vhost, String username, String password) throws URLSyntaxException
    {
        String url = "amqp://" + username + ":" + password + "@clientid/" + vhost + "?brokerlist='" + getBroker() + "?retries='0''";
        return new AMQConnectionURL(url);
    }

    /**
     * Convenience method to validate a JMS exception with a linked {@link AMQConstant#ACCESS_REFUSED} 403 error code exception.
     */
    public void check403Exception(Throwable t) throws Exception
    {
        assertNotNull("There was no linked exception", t);
        assertTrue("Wrong linked exception type", t instanceof AMQException);
        assertEquals("Incorrect error code received", 403, ((AMQException) t).getErrorCode().getCode());
    
        //use the latch to ensure the control thread waits long enough for the exception thread 
        //to have done enough to mark the connection closed before teardown commences
        assertTrue("Timed out waiting for conneciton to report close", _exceptionReceived.await(2, TimeUnit.SECONDS));
    }
}
