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
package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.DbBackup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.util.CommandLineParser;
import org.apache.qpid.util.FileUtils;

/**
 * BDBBackup is a utility for taking hot backups of the current state of a BDB transaction log database.
 * <p>
 * This utility makes the following assumptions/performs the following actions:
 * <p>
 * <ul> <li>The from and to directory locations will already exist. This scripts does not create them. <li>If this
 * script fails to complete in one minute it will terminate. <li>This script always exits with code 1 on error, code 0
 * on success (standard unix convention). <li>This script will log out at info level, when it starts and ends and a list
 * of all files backed up. <li>This script logs all errors at error level. <li>This script does not perform regular
 * backups, wrap its calling script in a cron job or similar to do this. </ul>
 * <p>
 * This utility is build around the BDB provided backup helper utility class, DbBackup. This utility class provides
 * an ability to force BDB to stop writing to the current log file set, whilst the backup is taken, to ensure that a
 * consistent snapshot is acquired. Preventing BDB from writing to the current log file set, does not stop BDB from
 * continuing to run concurrently while the backup is running, it simply moves onto a new set of log files; this
 * provides a 'hot' backup facility.
 * <p>
 * DbBackup can also help with incremental backups, by providing the number of the last log file backed up.
 * Subsequent backups can be taken, from later log files only. In a messaging application, messages are not expected to
 * be long-lived in most cases, so the log files will usually have been completely turned over between backups. This
 * utility does not support incremental backups for this reason.
 * <p>
 * If the database is locked by BDB, as is required when using transactions, and therefore will always be the case
 * in Qpid, this utility cannot make use of the DbBackup utility in a seperate process. DbBackup, needs to ensure that
 * the BDB envinronment used to take the backup has exclusive write access to the log files. This utility can take a
 * backup as a standalone utility against log files, when a broker is not running, using the {@link #takeBackup(String,
 *String,com.sleepycat.je.Environment)} method.
 * <p>
 * A seperate backup machanism is provided by the {@link #takeBackupNoLock(String,String)} method which can take a
 * hot backup against a running broker. This works by finding out the set of files to copy, and then opening them all to
 * read, and repeating this process until a consistent set of open files is obtained. This is done to avoid the
 * situation where the BDB cleanup thread deletes a file, between the directory listing and opening of the file to copy.
 * All consistently opened files are copied. This is the default mechanism the the {@link #main} method of this utility
 * uses.
 */
public class BDBBackup
{
    /** Used for debugging. */
    private static final Logger log = LoggerFactory.getLogger(BDBBackup.class);

    /** Used for communicating with the user. */
    private static final Logger console = LoggerFactory.getLogger("Console");

    /** Defines the suffix used to identify BDB log files. */
    private static final String LOG_FILE_SUFFIX = ".jdb";

    /** Defines the command line format for this utility. */
    public static final String[][] COMMAND_LINE_SPEC =
        new String[][]
        {
            { "fromdir", "The path to the directory to back the bdb log file from.", "dir", "true" },
            { "todir", "The path to the directory to save the backed up bdb log files to.", "dir", "true" }
        };

    /** Defines the timeout to terminate the backup operation on if it fails to complete. One minte. */
    public static final long TIMEOUT = 60000;

    /**
     * Runs a backup of the BDB log files in a specified directory, copying the backed up files to another specified
     * directory.
     * <p>
     * The following arguments must be specified:
     * <table>
     * <caption>Command Line</caption> <tr><th> Option <th> Comment <tr><td> -fromdir <td> The path to the
     * directory to back the bdb log file from. <tr><td> -todir   <td> The path to the directory to save the backed up
     * bdb log files to. </table>
     *
     * @param args The command line arguments.
     */
    public static void main(String[] args)
    {
        // Process the command line using standard handling (errors and usage followed by System.exit when it is wrong).
        Properties options =
            CommandLineParser.processCommandLine(args, new CommandLineParser(COMMAND_LINE_SPEC), System.getProperties());

        // Extract the from and to directory locations and perform a backup between them.
        try
        {
            String fromDir = options.getProperty("fromdir");
            String toDir = options.getProperty("todir");

            log.info("BDBBackup Utility: Starting Hot Backup.");

            BDBBackup bdbBackup = new BDBBackup();
            String[] backedUpFiles = bdbBackup.takeBackupNoLock(fromDir, toDir);

            if (log.isInfoEnabled())
            {
                log.info("BDBBackup Utility: Hot Backup Completed.");
                log.info(backedUpFiles.length + " file(s) backed-up:");
                for(String backedUpFile : backedUpFiles)
                {
                    log.info(backedUpFile);
                }
            }
        }
        catch (Exception e)
        {
            console.info("Backup script encountered an error and has failed: " + e.getMessage());
            log.error("Backup script got exception: " + e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Creates a backup of the BDB log files in the source directory, copying them to the destination directory.
     *
     * @param fromdir     The source directory path.
     * @param todir       The destination directory path.
     * @param environment An open BDB environment to perform the back up.
     *
     * @throws DatabaseException Any underlying execeptions from BDB are allowed to fall through.
     */
    public void takeBackup(String fromdir, String todir, Environment environment) throws DatabaseException
    {
        DbBackup backupHelper = null;

        try
        {
            backupHelper = new DbBackup(environment);

            // Prevent BDB from writing to its log files while the backup it taken.
            backupHelper.startBackup();

            // Back up the BDB log files to the destination directory.
            String[] filesForBackup = backupHelper.getLogFilesInBackupSet();

            for (int i = 0; i < filesForBackup.length; i++)
            {
                File sourceFile = new File(fromdir + File.separator + filesForBackup[i]);
                File destFile = new File(todir + File.separator + filesForBackup[i]);
                FileUtils.copy(sourceFile, destFile);
            }
        }
        finally
        {
            // Remember to exit backup mode, or all log files won't be cleaned and disk usage will bloat.
            if (backupHelper != null)
            {
                backupHelper.endBackup();
            }
        }
    }

    /**
     * Takes a hot backup when another process has locked the BDB database.
     *
     * @param fromdir The source directory path.
     * @param todir   The destination directory path.
     *
     * @return A list of all of the names of the files succesfully backed up.
     */
    public String[] takeBackupNoLock(String fromdir, String todir)
    {
        if (log.isDebugEnabled())
        {
            log.debug("public void takeBackupNoLock(String fromdir = " + fromdir + ", String todir = " + todir
                + "): called");
        }

        File fromDirFile = new File(fromdir);

        if (!fromDirFile.isDirectory())
        {
            throw new IllegalArgumentException("The specified fromdir(" + fromdir
                + ") must be the directory containing your bdbstore.");
        }

        File toDirFile = new File(todir);

        if (!toDirFile.exists())
        {
            // Create directory if it doesn't exist
            toDirFile.mkdirs();

            if (log.isDebugEnabled())
            {
                log.debug("Created backup directory:" + toDirFile);
            }
        }

        if (!toDirFile.isDirectory())
        {
            throw new IllegalArgumentException("The specified todir(" + todir + ") must be a directory.");
        }

        // Repeat until manage to open consistent set of files for reading.
        boolean consistentSet = false;
        FileInputStream[] fileInputStreams = new FileInputStream[0];
        File[] fileSet = new File[0];
        long start = System.currentTimeMillis();

        while (!consistentSet)
        {
            // List all .jdb files in the directory.
            fileSet = fromDirFile.listFiles(new FilenameFilter()
                    {
                        public boolean accept(File dir, String name)
                        {
                            return name.endsWith(LOG_FILE_SUFFIX);
                        }
                    });

            // Open them all for reading.
            fileInputStreams = new FileInputStream[fileSet.length];

            if (fileSet.length == 0)
            {
                throw new StoreException("There are no BDB log files to backup in the " + fromdir + " directory.");
            }

            for (int i = 0; i < fileSet.length; i++)
            {
                try
                {
                    fileInputStreams[i] = new FileInputStream(fileSet[i]);
                }
                catch (FileNotFoundException e)
                {
                    // Close any files opened for reading so far.
                    for (int j = 0; j < i; j++)
                    {
                        if (fileInputStreams[j] != null)
                        {
                            try
                            {
                                fileInputStreams[j].close();
                            }
                            catch (IOException ioEx)
                            {
                                // Rethrow this as a runtime exception, as something strange has happened.
                                throw new StoreException(ioEx);
                            }
                        }
                    }

                    // Could not open a consistent file set so try again.
                    break;
                }

                // A consistent set has been opened if all files were sucesfully opened for reading.
                if (i == (fileSet.length - 1))
                {
                    consistentSet = true;
                }
            }

            // Check that the script has not timed out, and raise an error if it has.
            long now = System.currentTimeMillis();
            if ((now - start) > TIMEOUT)
            {
                throw new StoreException("Hot backup script failed to complete in " + (TIMEOUT / 1000) + " seconds.");
            }
        }

        // Copy the consistent set of open files.
        List<String> backedUpFileNames = new LinkedList<String>();

        for (int j = 0; j < fileSet.length; j++)
        {
            File destFile = new File(todir + File.separator + fileSet[j].getName());
            try
            {
                FileUtils.copy(fileSet[j], destFile);
            }
            catch (RuntimeException re)
            {
                Throwable cause = re.getCause();
                if ((cause != null) && (cause instanceof IOException))
                {
                    throw new StoreException(re.getMessage() + " fromDir:" + fromdir + " toDir:" + toDirFile, cause);
                }
                else
                {
                    throw re;
                }
            }

            backedUpFileNames.add(destFile.getName());

            // Close all of the files.
            try
            {
                fileInputStreams[j].close();
            }
            catch (IOException e)
            {
                // Rethrow this as a runtime exception, as something strange has happened.
                throw new StoreException(e);
            }
        }

        return backedUpFileNames.toArray(new String[backedUpFileNames.size()]);
    }

    /*
     * Creates an environment for the bdb log files in the specified directory. This envrinonment can only be used
     * to backup these files, if they are not locked by another database instance.
     *
     * @param fromdir The path to the directory to create the environment for.
     *
     * @throws DatabaseException Any underlying exceptions from BDB are allowed to fall through.
     */
    private Environment createSourceDirEnvironment(String fromdir) throws DatabaseException
    {
        // Initialize the BDB backup utility on the source directory.
        return new Environment(new File(fromdir), new EnvironmentConfig());
    }
}
