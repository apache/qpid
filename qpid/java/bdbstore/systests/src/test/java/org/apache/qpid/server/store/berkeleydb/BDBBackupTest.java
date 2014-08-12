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
 */
package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBVirtualHostNode;
import org.apache.qpid.test.utils.Piper;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.util.Strings;
import org.apache.qpid.util.SystemUtils;

/**
 * Tests the BDB backup script can successfully perform a backup and that
 * backup can be restored and used by the Broker.
 */
public class BDBBackupTest extends QpidBrokerTestCase
{
    protected static final Logger LOGGER = Logger.getLogger(BDBBackupTest.class);

    private static final String BACKUP_SCRIPT = "/bin/backup.sh";
    private static final String BACKUP_COMPLETE_MESSAGE = "Hot Backup Completed";

    private static final String TEST_VHOST = "test";
    private static final String SYSTEM_TMP_DIR = System.getProperty("java.io.tmpdir");

    private File _backupToDir;
    private File _backupFromDir;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _backupToDir = new File(SYSTEM_TMP_DIR + File.separator + getTestName());
        _backupToDir.mkdirs();

        Map<String, Object> virtualHostNodeAttributes = getBrokerConfiguration().getObjectAttributes(VirtualHostNode.class, TEST_VHOST);
        _backupFromDir = new File(Strings.expand((String) virtualHostNodeAttributes.get(BDBVirtualHostNode.STORE_PATH)));
        boolean fromDirExistsAndIsDir = _backupFromDir.isDirectory();
        assertTrue("backupFromDir " + _backupFromDir + " should already exist", fromDirExistsAndIsDir);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            FileUtils.delete(_backupToDir, true);
        }
    }

    public void testBackupAndRestoreMaintainsMessages() throws Exception
    {
        sendNumberedMessages(0, 10);
        invokeBdbBackup(_backupFromDir, _backupToDir);
        sendNumberedMessages(10, 20);
        confirmBrokerHasMessages(0, 20);
        stopBroker();

        deleteStore(_backupFromDir);
        replaceStoreWithBackup(_backupToDir, _backupFromDir);

        startBroker();
        confirmBrokerHasMessages(0, 10);
    }

    private void sendNumberedMessages(final int startIndex, final int endIndex) throws JMSException, Exception
    {
        Connection con = getConnection();
        Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue(getTestQueueName());
        // Create queue by consumer side-effect
        session.createConsumer(destination).close();

        final int numOfMessages = endIndex - startIndex;
        final int batchSize = 0;
        sendMessage(session, destination, numOfMessages, startIndex, batchSize);
        con.close();
    }

    private void confirmBrokerHasMessages(final int startIndex, final int endIndex) throws Exception
    {
        Connection con = getConnection();
        Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        con.start();
        Destination destination = session.createQueue(getTestQueueName());
        MessageConsumer consumer = session.createConsumer(destination);
        for (int i = startIndex; i < endIndex; i++)
        {
            Message msg = consumer.receive(RECEIVE_TIMEOUT);
            assertNotNull("Message " + i + " not received", msg);
            assertEquals("Did not receive the expected message", i, msg.getIntProperty(INDEX));
        }

        Message msg = consumer.receive(100);
        if(msg != null)
        {
            fail("No more messages should be received, but received additional message with index: " + msg.getIntProperty(INDEX));
        }
        con.close();
    }

    private void invokeBdbBackup(final File backupFromDir, final File backupToDir) throws Exception
    {
        if (SystemUtils.isWindows())
        {
            BDBBackup.main(new String[]{"-todir", backupToDir.getAbsolutePath(), "-fromdir", backupFromDir.getAbsolutePath()});
        }
        else
        {
            runBdbBackupScript(backupFromDir, backupToDir);
        }
    }

    private void runBdbBackupScript(final File backupFromDir, final File backupToDir) throws IOException,
            InterruptedException
    {
        Process backupProcess = null;
        try
        {
            String qpidHome = System.getProperty(QPID_HOME);
            ProcessBuilder pb = new ProcessBuilder(qpidHome + BACKUP_SCRIPT, "-todir", backupToDir.getAbsolutePath(), "-fromdir", backupFromDir.getAbsolutePath());
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            env.put(QPID_HOME, qpidHome);

            LOGGER.debug("Backup command is " + pb.command());
            backupProcess = pb.start();
            Piper piper = new Piper(backupProcess.getInputStream(), _testcaseOutputStream, null, BACKUP_COMPLETE_MESSAGE);
            piper.start();
            piper.await(2, TimeUnit.SECONDS);
            backupProcess.waitFor();
            piper.join();

            LOGGER.debug("Backup command completed " + backupProcess.exitValue());
            assertEquals("Unexpected exit value from backup script", 0, backupProcess.exitValue());
        }
        finally
        {
            if (backupProcess != null)
            {
                backupProcess.getErrorStream().close();
                backupProcess.getInputStream().close();
                backupProcess.getOutputStream().close();
            }
        }
    }

    private void replaceStoreWithBackup(File source, File dst) throws Exception
    {
        LOGGER.debug("Copying store " + source  + " to " + dst);
        FileUtils.copyRecursive(source, dst);
    }

    private void deleteStore(File storeDir)
    {
        LOGGER.debug("Deleting store " + storeDir);
        FileUtils.delete(storeDir, true);
    }

}
