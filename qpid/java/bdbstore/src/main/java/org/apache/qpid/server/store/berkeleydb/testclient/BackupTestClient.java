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
package org.apache.qpid.server.store.berkeleydb.testclient;

import org.apache.log4j.Logger;

import org.apache.qpid.ping.PingDurableClient;
import org.apache.qpid.server.store.berkeleydb.BDBBackup;
import org.apache.qpid.util.CommandLineParser;

import java.util.Properties;

/**
 * BackupTestClient extends {@link PingDurableClient} with an action that takes a BDB backup when a configurable
 * message count is reached. This enables a test user to restore this beackup, knowing how many committed but undelivered
 * messages were in the backup, in order to check that all are re-delivered when the backup is retored.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Perform BDB Backup on configurable message count.
 * </table>
 */
public class BackupTestClient extends PingDurableClient
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(BackupTestClient.class);

    /** Holds the from directory to take backups from. */
    private String fromDir;

    /** Holds the to directory to store backups in. */
    private String toDir;

    /**
     * Default constructor, passes all property overrides to the parent.
     *
     * @param overrides Any property overrides to apply to the defaults.
     *
     * @throws Exception Any underlying exception is allowed to fall through.
     */
    BackupTestClient(Properties overrides) throws Exception
    {
        super(overrides);
    }

    /**
     * Starts the ping/wait/receive process. From and to directory locations for the BDB backups must be specified
     * on the command line:
     *
     * <p/><table><caption>Command Line</caption>
     * <tr><th> Option <th> Comment
     * <tr><td> -fromdir <td> The path to the directory to back the bdb log file from.
     * <tr><td> -todir   <td> The path to the directory to save the backed up bdb log files to.
     * </table>
     *
     * @param args The command line arguments.
     */
    public static void main(String[] args)
    {
        try
        {
            // Use the same command line format as BDBBackup utility, (compulsory from and to directories).
            Properties options =
                CommandLineParser.processCommandLine(args, new CommandLineParser(BDBBackup.COMMAND_LINE_SPEC),
                    System.getProperties());
            BackupTestClient pingProducer = new BackupTestClient(options);

            // Keep the from and to directories for backups.
            pingProducer.fromDir = options.getProperty("fromdir");
            pingProducer.toDir = options.getProperty("todir");

            // Create a shutdown hook to terminate the ping-pong producer.
            Runtime.getRuntime().addShutdownHook(pingProducer.getShutdownHook());

            // Ensure that the ping pong producer is registered to listen for exceptions on the connection too.
            // pingProducer.getConnection().setExceptionListener(pingProducer);

            // Run the test procedure.
            int sent = pingProducer.send();
            pingProducer.waitForUser("Press return to begin receiving the pings.");
            pingProducer.receive(sent);

            System.exit(0);
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
            log.error("Top level handler caught execption.", e);
            System.exit(1);
        }
    }

    /**
     * Supplies a triggered action extension, based on message count. This action takes a BDB log file backup.
     */
    public void takeAction()
    {
        BDBBackup backupUtil = new BDBBackup();
        backupUtil.takeBackupNoLock(fromDir, toDir);
        System.out.println("Took backup of BDB log files from directory: " + fromDir);
    }
}
