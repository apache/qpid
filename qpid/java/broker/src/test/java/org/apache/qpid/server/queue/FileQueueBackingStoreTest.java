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
package org.apache.qpid.server.queue;

import junit.framework.TestCase;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.framing.amqp_8_0.BasicPublishBodyImpl;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.io.File;

public class FileQueueBackingStoreTest extends TestCase
{
    FileQueueBackingStore _backing;
    private TransactionLog _transactionLog;
    VirtualHost _vhost;
        VirtualHostConfiguration _vhostConfig;

    public void setUp() throws Exception
    {
        _backing = new FileQueueBackingStore();
        PropertiesConfiguration config = new PropertiesConfiguration();
        config.addProperty("store.class", MemoryMessageStore.class.getName());
        _vhostConfig = new VirtualHostConfiguration(this.getName() + "-Vhost", config);
        _vhost = new VirtualHost(_vhostConfig);
        _transactionLog = _vhost.getTransactionLog();

        _backing.configure(_vhost, _vhost.getConfiguration());

    }

    private void resetBacking(Configuration configuration) throws Exception
    {
        configuration.addProperty("store.class", MemoryMessageStore.class.getName());
        _vhostConfig = new VirtualHostConfiguration(this.getName() + "-Vhost", configuration);
        _vhost = new VirtualHost(_vhostConfig);
        _transactionLog = _vhost.getTransactionLog();

        _backing = new FileQueueBackingStore();

        _backing.configure(_vhost, _vhost.getConfiguration());
    }

    public void testInvalidSetupRootExistsIsFile() throws Exception
    {

        File fileAsRoot = File.createTempFile("tmpRoot", "");
        fileAsRoot.deleteOnExit();

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(VirtualHostConfiguration.FLOW_TO_DISK_PATH, fileAsRoot.getAbsolutePath());

        try
        {
            resetBacking(configuration);
            fail("Exception expected to be thrown");
        }
        catch (ConfigurationException ce)
        {
            assertTrue("Expected Exception not thrown, expecting:" +
                       "Unable to create Temporary Flow to Disk store as specified root is a file:",
                       ce.getMessage().
                               startsWith("Unable to create Temporary Flow to Disk store as specified root is a file:"));
        }

    }

    public void testInvalidSetupRootExistsCantWrite() throws Exception
    {

        File fileAsRoot = new File("/var/log");

        PropertiesConfiguration configuration = new PropertiesConfiguration();

        configuration.addProperty(VirtualHostConfiguration.FLOW_TO_DISK_PATH, fileAsRoot.getAbsolutePath());

        try
        {
            resetBacking(configuration);
            fail("Exception expected to be thrown");
        }
        catch (ConfigurationException ce)
        {
            assertEquals("Unable to create Temporary Flow to Disk store. Unable to write to specified root:/var/log",
                         ce.getMessage());
        }

    }

    public void testEmptyTransientFlowToDisk() throws UnableToFlowMessageException, AMQException
    {
        AMQMessage original = MessageFactory.getInstance().createMessage(null, false);

        ContentHeaderBody chb = new ContentHeaderBody(new BasicContentHeaderProperties(), BasicPublishBodyImpl.CLASS_ID);
        chb.bodySize = 0L;

        runTestWithMessage(original, chb);
    }

    public void testEmptyPersistentFlowToDisk() throws UnableToFlowMessageException, AMQException
    {

        AMQMessage original = MessageFactory.getInstance().createMessage(_transactionLog, true);
        ContentHeaderBody chb = new ContentHeaderBody(new BasicContentHeaderProperties(), BasicPublishBodyImpl.CLASS_ID);
        chb.bodySize = 0L;
        ((BasicContentHeaderProperties) chb.properties).setDeliveryMode((byte) 2);

        runTestWithMessage(original, chb);

    }

    public void testNonEmptyTransientFlowToDisk() throws UnableToFlowMessageException, AMQException
    {
        AMQMessage original = MessageFactory.getInstance().createMessage(null, false);

        ContentHeaderBody chb = new ContentHeaderBody(new BasicContentHeaderProperties(), BasicPublishBodyImpl.CLASS_ID);
        chb.bodySize = 100L;

        runTestWithMessage(original, chb);
    }

    public void testNonEmptyPersistentFlowToDisk() throws UnableToFlowMessageException, AMQException
    {
        AMQMessage original = MessageFactory.getInstance().createMessage(_transactionLog, true);
        ContentHeaderBody chb = new ContentHeaderBody(new BasicContentHeaderProperties(), BasicPublishBodyImpl.CLASS_ID);
        chb.bodySize = 100L;
        ((BasicContentHeaderProperties) chb.properties).setDeliveryMode((byte) 2);

        runTestWithMessage(original, chb);
    }

    void runTestWithMessage(AMQMessage original, ContentHeaderBody chb) throws UnableToFlowMessageException, AMQException
    {

        // Create message

        original.setPublishAndContentHeaderBody(null,
                                                new MessagePublishInfoImpl(ExchangeDefaults.DIRECT_EXCHANGE_NAME,
                                                                           false, false, new AMQShortString("routing")),
                                                chb);
        if (chb.bodySize > 0)
        {
            ContentChunk chunk = new MockContentChunk((int) chb.bodySize/2);

            original.addContentBodyFrame(null, chunk, false);

            chunk = new MockContentChunk((int) chb.bodySize/2);

            original.addContentBodyFrame(null, chunk, true);            
        }

        _backing.flow(original);

        AMQMessage fromDisk = _backing.recover(original.getMessageId());

        assertEquals("Message IDs do not match", original.getMessageId(), fromDisk.getMessageId());
        assertEquals("Message arrival times do not match", original.getArrivalTime(), fromDisk.getArrivalTime());
        assertEquals(original.isPersistent(), fromDisk.isPersistent());

        // Validate the MPI data was restored correctly
        MessagePublishInfo originalMPI = original.getMessagePublishInfo();
        MessagePublishInfo fromDiskMPI = fromDisk.getMessagePublishInfo();
        assertEquals("Exchange", originalMPI.getExchange(), fromDiskMPI.getExchange());
        assertEquals(originalMPI.isImmediate(), fromDiskMPI.isImmediate());
        assertEquals(originalMPI.isMandatory(), fromDiskMPI.isMandatory());
        assertEquals(originalMPI.getRoutingKey(), fromDiskMPI.getRoutingKey());

        // Validate BodyCounts.
        int originalBodyCount = original.getBodyCount();
        assertEquals(originalBodyCount, fromDisk.getBodyCount());

        if (originalBodyCount > 0)
        {
            for (int index = 0; index < originalBodyCount; index++)
            {
                ContentChunk originalChunk = original.getContentChunk(index);
                ContentChunk fromDiskChunk = fromDisk.getContentChunk(index);

                assertEquals(originalChunk.getSize(), fromDiskChunk.getSize());
                assertEquals(originalChunk.getData(), fromDiskChunk.getData());
            }
        }

    }

}
