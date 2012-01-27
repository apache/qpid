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

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.logging.NullRootMessageLogger;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.store.berkeleydb.keys.MessageContentKey_4;
import org.apache.qpid.server.store.berkeleydb.keys.MessageContentKey_5;
import org.apache.qpid.server.store.berkeleydb.records.ExchangeRecord;
import org.apache.qpid.server.store.berkeleydb.records.QueueRecord;
import org.apache.qpid.server.store.berkeleydb.tuples.MessageContentKeyTB_4;
import org.apache.qpid.server.store.berkeleydb.tuples.MessageContentKeyTB_5;
import org.apache.qpid.server.store.berkeleydb.tuples.QueueEntryTB;
import org.apache.qpid.util.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

/**
 * This is a simple BerkeleyDB Store upgrade tool that will upgrade a V4 Store to a V5 Store.
 *
 * Currently upgrade is fixed from v4 -> v5
 *
 * Improvments:
 * - Add List BDBMessageStore.getDatabases(); This can the be iterated to guard against new DBs being added.
 * - A version in the store would allow automated upgrade or later with more available versions interactive upgrade.
 * - Add process logging and disable all Store and Qpid logging.
 */
public class BDBStoreUpgrade
{
    private static final Logger _logger = LoggerFactory.getLogger(BDBStoreUpgrade.class);
    /** The Store Directory that needs upgrading */
    private File _fromDir;
    /** The Directory that will be made to contain the upgraded store */
    private File _toDir;
    /** The Directory that will be made to backup the original store if required */
    private File _backupDir;

    /** The Old Store */
    private BDBMessageStore _oldMessageStore;
    /** The New Store */
    private BDBMessageStore _newMessageStore;
    /** The file ending that is used by BDB Store Files */
    private static final String BDB_FILE_ENDING = ".jdb";

    static final Options _options = new Options();
    private static CommandLine _commandLine;
    private boolean _interactive;
    private boolean _force;

    private static final String VERSION = "3.0";
    private static final String USER_ABORTED_PROCESS = "User aborted process";
    private static final int LOWEST_SUPPORTED_STORE_VERSION = 4;
    private static final String PREVIOUS_STORE_VERSION_UNSUPPORTED = "Store upgrade from version {0} is not supported."
            + " You must first run the previous store upgrade tool.";
    private static final String FOLLOWING_STORE_VERSION_UNSUPPORTED = "Store version {0} is newer than this tool supports. "
            + "You must use a newer version of the store upgrade tool";
    private static final String STORE_ALREADY_UPGRADED = "Store has already been upgraded to version {0}.";
        
    private static final String OPTION_INPUT_SHORT = "i";
    private static final String OPTION_INPUT = "input";
    private static final String OPTION_OUTPUT_SHORT = "o";
    private static final String OPTION_OUTPUT = "output";
    private static final String OPTION_BACKUP_SHORT = "b";
    private static final String OPTION_BACKUP = "backup";
    private static final String OPTION_QUIET_SHORT = "q";
    private static final String OPTION_QUIET = "quiet";
    private static final String OPTION_FORCE_SHORT = "f";
    private static final String OPTION_FORCE = "force";
    private boolean _inplace = false;

    public BDBStoreUpgrade(String fromDir, String toDir, String backupDir, boolean interactive, boolean force)
    {
        _interactive = interactive;
        _force = force;

        _fromDir = new File(fromDir);
        if (!_fromDir.exists())
        {
            throw new IllegalArgumentException("BDBStore path '" + fromDir + "' could not be read. "
                                               + "Ensure the path is correct and that the permissions are correct.");
        }

        if (!isDirectoryAStoreDir(_fromDir))
        {
            throw new IllegalArgumentException("Specified directory '" + fromDir + "' does not contain a valid BDBMessageStore.");
        }

        if (toDir == null)
        {
            _inplace = true;
            _toDir = new File(fromDir+"-Inplace");
        }
        else
        {
            _toDir = new File(toDir);
        }
        
        if (_toDir.exists())
        {
            if (_interactive)
            {
                if (toDir == null)
                {
                    System.out.println("Upgrading in place:" + fromDir);
                }
                else
                {
                    System.out.println("Upgrade destination: '" + toDir + "'");
                }

                if (userInteract("Upgrade destination exists do you wish to replace it?"))
                {
                    if (!FileUtils.delete(_toDir, true))
                    {
                        throw new IllegalArgumentException("Unable to remove upgrade destination '" + _toDir + "'");
                    }
                }
                else
                {
                    throw new IllegalArgumentException("Upgrade destination '" + _toDir + "' already exists. ");
                }
            }
            else
            {
                if (_force)
                {
                    if (!FileUtils.delete(_toDir, true))
                    {
                        throw new IllegalArgumentException("Unable to remove upgrade destination '" + _toDir + "'");
                    }
                }
                else
                {
                    throw new IllegalArgumentException("Upgrade destination '" + _toDir + "' already exists. ");
                }
            }
        }

        if (!_toDir.mkdirs())
        {
            throw new IllegalArgumentException("Upgrade destination '" + _toDir + "' could not be created. "
                                               + "Ensure the path is correct and that the permissions are correct.");
        }

        if (backupDir != null)
        {
            if (backupDir.equals(""))
            {
                _backupDir = new File(_fromDir.getAbsolutePath().toString() + "-Backup");
            }
            else
            {
                _backupDir = new File(backupDir);
            }
        }
        else
        {
            _backupDir = null;
        }
    }

    private static String ANSWER_OPTIONS = " Yes/No/Abort? ";
    private static String ANSWER_NO = "no";
    private static String ANSWER_N = "n";
    private static String ANSWER_YES = "yes";
    private static String ANSWER_Y = "y";
    private static String ANSWER_ABORT = "abort";
    private static String ANSWER_A = "a";

    /**
     * Interact with the user via System.in and System.out. If the user wishes to Abort then a RuntimeException is thrown.
     * Otherwise the method will return based on their response true=yes false=no.
     *
     * @param message Message to print out
     *
     * @return boolean response from user if they wish to proceed
     */
    private boolean userInteract(String message)
    {
        System.out.print(message + ANSWER_OPTIONS);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String input = "";
        try
        {
            input = br.readLine();
        }
        catch (IOException e)
        {
            input = "";
        }

        if (input.equalsIgnoreCase(ANSWER_Y) || input.equalsIgnoreCase(ANSWER_YES))
        {
            return true;
        }
        else
        {
            if (input.equalsIgnoreCase(ANSWER_N) || input.equalsIgnoreCase(ANSWER_NO))
            {
                return false;
            }
            else
            {
                if (input.equalsIgnoreCase(ANSWER_A) || input.equalsIgnoreCase(ANSWER_ABORT))
                {
                    throw new RuntimeException(USER_ABORTED_PROCESS);
                }
            }
        }

        return userInteract(message);
    }

    /**
     * Upgrade a Store of a specified version to the latest version.
     *
     * @param version the version of the current store
     *
     * @throws Exception
     */
    public void upgradeFromVersion(int version) throws Exception
    {
        upgradeFromVersion(version, _fromDir, _toDir, _backupDir, _force,
                           _inplace);
    }

    /**
     * Upgrade a Store of a specified version to the latest version.
     *
     * @param version the version of the current store
     * @param fromDir the directory with the old Store
     * @param toDir   the directrory to hold the newly Upgraded Store
     * @param backupDir the directrory to backup to if required
     * @param force   suppress all questions
     * @param inplace replace the from dir with the upgraded result in toDir 
     *
     * @throws Exception         due to Virtualhost/MessageStore.close() being
     *                              rather poor at exception handling
     * @throws DatabaseException if there is a problem with the store formats
     * @throws AMQException   if there is an issue creating Qpid data structures
     */
    public void upgradeFromVersion(int version, File fromDir, File toDir,
                                   File backupDir, boolean force,
                                   boolean inplace) throws Exception
    {
        _logger.info("Located store to upgrade at '" + fromDir + "'");

        // Verify user has created a backup, giving option to perform backup
        if (_interactive)
        {
            if (!userInteract("Have you performed a DB backup of this store."))
            {
                File backup;
                if (backupDir == null)
                {
                    backup = new File(fromDir.getAbsolutePath().toString() + "-Backup");
                }
                else
                {
                    backup = backupDir;
                }

                if (userInteract("Do you wish to perform a DB backup now? " +
                                 "(Store will be backed up to '" + backup.getName() + "')"))
                {
                    performDBBackup(fromDir, backup, force);
                }
                else
                {
                    if (!userInteract("Are you sure wish to proceed with DB migration without backup? " +
                                      "(For more details of the consequences check the Qpid/BDB Message Store Wiki)."))
                    {
                        throw new IllegalArgumentException("Upgrade stopped at user request as no DB Backup performed.");
                    }
                }
            }
            else
            {
                if (!inplace)
                {
                    _logger.info("Upgrade will create a new store at '" + toDir + "'");
                }

                _logger.info("Using the contents in the Message Store '" + fromDir + "'");

                if (!userInteract("Do you wish to proceed?"))
                {
                    throw new IllegalArgumentException("Upgrade stopped as did not wish to proceed");
                }
            }
        }
        else
        {
            if (backupDir != null)
            {
                performDBBackup(fromDir, backupDir, force);
            }
        }

        CurrentActor.set(new BrokerActor(new NullRootMessageLogger()));

        //Create a new messageStore
        _newMessageStore = new BDBMessageStore();
        _newMessageStore.configure(toDir, false);
        _newMessageStore.start();

        try
        {
            //Load the old MessageStore
            switch (version)
            {
                default:
                case 4:
                    _oldMessageStore = new BDBMessageStore(4);
                    _oldMessageStore.configure(fromDir, true);
                    _oldMessageStore.start();
                    upgradeFromVersion_4();
                    break;
                case 3:
                case 2:
                case 1:
                    throw new IllegalArgumentException(MessageFormat.format(PREVIOUS_STORE_VERSION_UNSUPPORTED,
                            new Object[] { Integer.toString(version) }));
            }
        }
        finally
        {
            _newMessageStore.close();
            if (_oldMessageStore != null)
            {
                _oldMessageStore.close();
            }
            // if we are running inplace then swap fromDir and toDir
            if (inplace)
            {
                // Remove original copy
                if (FileUtils.delete(fromDir, true))
                {
                    // Rename upgraded store
                    toDir.renameTo(fromDir);
                }
                else
                {
                    throw new RuntimeException("Unable to upgrade inplace as " +
                                               "unable to delete source '"
                                               +fromDir+"', Store upgrade " +
                                               "successfully performed to :"
                                               +toDir);
                }
            }
        }
    }

    private void upgradeFromVersion_4() throws AMQException, DatabaseException
    {
        _logger.info("Starting store upgrade from version 4");
        
        //Migrate _exchangeDb;
        _logger.info("Exchanges");

        moveContents(_oldMessageStore.getExchangesDb(), _newMessageStore.getExchangesDb(), "Exchange");

        final List<AMQShortString> topicExchanges = new ArrayList<AMQShortString>();
        final TupleBinding exchangeTB = new ExchangeTB();
        
        DatabaseVisitor exchangeListVisitor = new DatabaseVisitor()
        {           
            public void visit(DatabaseEntry key, DatabaseEntry value) throws DatabaseException
            {
                ExchangeRecord exchangeRec = (ExchangeRecord) exchangeTB.entryToObject(value);
                AMQShortString type = exchangeRec.getType();

                if (ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(type))
                {
                    topicExchanges.add(exchangeRec.getNameShortString());
                }
            }
        };
        _oldMessageStore.visitExchanges(exchangeListVisitor);


        //Migrate _queueBindingsDb;
        _logger.info("Queue Bindings");
        moveContents(_oldMessageStore.getBindingsDb(), _newMessageStore.getBindingsDb(), "Queue Binding");

        //Inspect the bindings to gather a list of queues which are probably durable subscriptions, i.e. those 
        //which have a colon in their name and are bound to the Topic exchanges above
        final List<AMQShortString> durableSubQueues = new ArrayList<AMQShortString>();
        final TupleBinding<BindingKey> bindingTB = _oldMessageStore.getBindingTupleBindingFactory().getInstance();
        
        DatabaseVisitor durSubQueueListVisitor = new DatabaseVisitor()
        {           
            public void visit(DatabaseEntry key, DatabaseEntry value) throws DatabaseException
            {
                BindingKey bindingRec = (BindingKey) bindingTB.entryToObject(key);
                AMQShortString queueName = bindingRec.getQueueName();
                AMQShortString exchangeName = bindingRec.getExchangeName();
                
                if (topicExchanges.contains(exchangeName) && queueName.asString().contains(":"))
                {
                    durableSubQueues.add(queueName);
                }
            }
        };
        _oldMessageStore.visitBindings(durSubQueueListVisitor);


        //Migrate _queueDb;
        _logger.info("Queues");

        // hold the list of existing queue names
        final List<AMQShortString> existingQueues = new ArrayList<AMQShortString>();

        final TupleBinding<QueueRecord> queueTupleBinding = _oldMessageStore.getQueueTupleBindingFactory().getInstance();

        DatabaseVisitor queueVisitor = new DatabaseVisitor()
        {
            public void visit(DatabaseEntry key, DatabaseEntry value) throws AMQStoreException
            {
                QueueRecord queueRec = (QueueRecord) queueTupleBinding.entryToObject(value);
                AMQShortString queueName = queueRec.getNameShortString();

                //if the queue name is in the gathered list then set its exclusivity true
                if (durableSubQueues.contains(queueName))
                {
                    _logger.info("Marking as possible DurableSubscription backing queue: " + queueName);
                    queueRec.setExclusive(true);
                }
                
                //The simple call to createQueue with the QueueRecord object is sufficient for a v2->v3 upgrade as
                //the extra 'exclusive' property in v3 will be defaulted to false in the record creation.
                _newMessageStore.createQueue(queueRec);

                _count++;
                existingQueues.add(queueName);
            }
        };
        _oldMessageStore.visitQueues(queueVisitor);

        logCount(queueVisitor.getVisitedCount(), "Queue");


        // Look for persistent messages stored for non-durable queues
        _logger.info("Checking for messages previously sent to non-durable queues");

        // track all message delivery to existing queues
        final HashSet<Long> queueMessages = new HashSet<Long>();

        // hold all non existing queues and their messages IDs
        final HashMap<String, HashSet<Long>> phantomMessageQueues = new HashMap<String, HashSet<Long>>();

        // delivery DB visitor to check message delivery and identify non existing queues
        final QueueEntryTB queueEntryTB = new QueueEntryTB();
        DatabaseVisitor messageDeliveryCheckVisitor = new DatabaseVisitor()
        {
            public void visit(DatabaseEntry key, DatabaseEntry value) throws DatabaseException
            {
                QueueEntryKey entryKey = (QueueEntryKey) queueEntryTB.entryToObject(key);
                Long messageId = entryKey.getMessageId();
                AMQShortString queueName = entryKey.getQueueName();
                if (!existingQueues.contains(queueName))
                {
                    String name = queueName.asString();
                    HashSet<Long> messages = phantomMessageQueues.get(name);
                    if (messages == null)
                    {
                        messages = new HashSet<Long>();
                        phantomMessageQueues.put(name, messages);
                    }
                    messages.add(messageId);
                    _count++;
                }
                else
                {
                    queueMessages.add(messageId);
                }
            }
        };
        _oldMessageStore.visitDelivery(messageDeliveryCheckVisitor);

        if (phantomMessageQueues.isEmpty())
        {
            _logger.info("No such messages were found");
        }
        else
        {
            _logger.info("Found " + messageDeliveryCheckVisitor.getVisitedCount()+ " such messages in total");

            for (Entry<String, HashSet<Long>> phantomQueue : phantomMessageQueues.entrySet())
            {
                String queueName = phantomQueue.getKey();
                HashSet<Long> messages = phantomQueue.getValue();

                _logger.info(MessageFormat.format("There are {0} messages which were previously delivered to non-durable queue ''{1}''",messages.size(), queueName));

                boolean createQueue;
                if(!_interactive)
                {
                    createQueue = true;
                    _logger.info("Running in batch-mode, marking queue as durable to ensure retention of the messages.");
                }
                else
                {
                    createQueue = userInteract("Do you want to make this queue durable?\n"
                                             + "NOTE: Answering No will result in these messages being discarded!");
                }

                if (createQueue)
                {
                    for (Long messageId : messages)
                    {
                        queueMessages.add(messageId);
                    }
                    AMQShortString name = new AMQShortString(queueName);
                    existingQueues.add(name);
                    QueueRecord record = new QueueRecord(name, null, false, null);
                    _newMessageStore.createQueue(record);
                }
            }
        }


        //Migrate _messageMetaDataDb;
        _logger.info("Message MetaData");
        
        final Database newMetaDataDB = _newMessageStore.getMetaDataDb();
        final TupleBinding<Object> oldMetaDataTupleBinding = _oldMessageStore.getMetaDataTupleBindingFactory().getInstance();
        final TupleBinding<Object> newMetaDataTupleBinding = _newMessageStore.getMetaDataTupleBindingFactory().getInstance();
        
        DatabaseVisitor metaDataVisitor = new DatabaseVisitor()
        {
            public void visit(DatabaseEntry key, DatabaseEntry value) throws DatabaseException
            {
                _count++;
                MessageMetaData metaData = (MessageMetaData) oldMetaDataTupleBinding.entryToObject(value);

                // get message id
                Long messageId = TupleBinding.getPrimitiveBinding(Long.class).entryToObject(key);

                // ONLY copy data if message is delivered to existing queue
                if (!queueMessages.contains(messageId))
                {
                    return;
                }
                DatabaseEntry newValue = new DatabaseEntry();
                newMetaDataTupleBinding.objectToEntry(metaData, newValue);
                
                newMetaDataDB.put(null, key, newValue);
            }
        };
        _oldMessageStore.visitMetaDataDb(metaDataVisitor);

        logCount(metaDataVisitor.getVisitedCount(), "Message MetaData");


        //Migrate _messageContentDb;
        _logger.info("Message Contents");
        final Database newContentDB = _newMessageStore.getContentDb();
        
        final TupleBinding<MessageContentKey> oldContentKeyTupleBinding = new MessageContentKeyTB_4();
        final TupleBinding<MessageContentKey> newContentKeyTupleBinding = new MessageContentKeyTB_5();
        final TupleBinding contentTB = new ContentTB();
        
        DatabaseVisitor contentVisitor = new DatabaseVisitor()
        {
            private long _prevMsgId = -1; //Initialise to invalid value
            private int _bytesSeenSoFar = 0;
            
            public void visit(DatabaseEntry key, DatabaseEntry value) throws DatabaseException
            {
                _count++;

                //determine the msgId of the current entry
                MessageContentKey_4 contentKey = (MessageContentKey_4) oldContentKeyTupleBinding.entryToObject(key);
                long msgId = contentKey.getMessageId();

                // ONLY copy data if message is delivered to existing queue
                if (!queueMessages.contains(msgId))
                {
                    return;
                }
                //if this is a new message, restart the byte offset count.
                if(_prevMsgId != msgId)
                {
                    _bytesSeenSoFar = 0;
                }

                //determine the content size
                ByteBuffer content = (ByteBuffer) contentTB.entryToObject(value);
                int contentSize = content.limit();

                //create the new key: id + previously seen data count
                MessageContentKey_5 newKey = new MessageContentKey_5(msgId, _bytesSeenSoFar);
                DatabaseEntry newKeyEntry = new DatabaseEntry();
                newContentKeyTupleBinding.objectToEntry(newKey, newKeyEntry);

                DatabaseEntry newValueEntry = new DatabaseEntry();
                contentTB.objectToEntry(content, newValueEntry);

                newContentDB.put(null, newKeyEntry, newValueEntry);

                _prevMsgId = msgId;
                _bytesSeenSoFar += contentSize;
            }
        };
        _oldMessageStore.visitContentDb(contentVisitor);

        logCount(contentVisitor.getVisitedCount(), "Message Content");


        //Migrate _deliveryDb;
        _logger.info("Delivery Records");
        final Database deliveryDb =_newMessageStore.getDeliveryDb();
        DatabaseVisitor deliveryDbVisitor = new DatabaseVisitor()
        {

            public void visit(DatabaseEntry key, DatabaseEntry value) throws DatabaseException
            {
                _count++;

                // get message id from entry key
                QueueEntryKey entryKey = (QueueEntryKey) queueEntryTB.entryToObject(key);
                AMQShortString queueName = entryKey.getQueueName();

                // ONLY copy data if message queue exists
                if (existingQueues.contains(queueName))
                {
                    deliveryDb.put(null, key, value);
                }
            }
        };
        _oldMessageStore.visitDelivery(deliveryDbVisitor);
        logCount(contentVisitor.getVisitedCount(), "Delivery Record");
    }

    /**
     * Log the specified count for item in a user friendly way.
     *
     * @param count of items to log
     * @param item  description of what is being logged.
     */
    private void logCount(int count, String item)
    {
        _logger.info(" " + count + " " + item + " " + (count == 1 ? "entry" : "entries"));
    }

    /**
     * @param oldDatabase The old MessageStoreDB to perform the visit on
     * @param newDatabase The new MessageStoreDB to copy the data to.
     * @param contentName The string name of the content for display purposes.
     *
     * @throws AMQException      Due to createQueue thorwing AMQException
     * @throws DatabaseException If there is a problem with the loading of the data
     */
    private void moveContents(Database oldDatabase, final Database newDatabase, String contentName) throws AMQException, DatabaseException
    {

        DatabaseVisitor moveVisitor = new DatabaseVisitor()
        {
            public void visit(DatabaseEntry key, DatabaseEntry value) throws DatabaseException
            {
                _count++;
                newDatabase.put(null, key, value);
            }
        };

        _oldMessageStore.visitDatabase(oldDatabase, moveVisitor);

        logCount(moveVisitor.getVisitedCount(), contentName);
    }

    private static void usage()
    {
        System.out.println("usage: BDBStoreUpgrade:\n [-h|--help] [-q|--quiet] [-f|--force] [-b|--backup <Path to backup-db>] " +
                           "-i|--input <Path to input-db> [-o|--output <Path to upgraded-db>]");
    }

    private static void help()
    {
        System.out.println("usage: BDBStoreUpgrade:");
        System.out.println("Required:");
        for (Object obj : _options.getOptions())
        {
            Option option = (Option) obj;
            if (option.isRequired())
            {
                System.out.println("-" + option.getOpt() + "|--" + option.getLongOpt() + "\t\t-\t" + option.getDescription());
            }
        }

        System.out.println("\nOptions:");
        for (Object obj : _options.getOptions())
        {
            Option option = (Option) obj;
            if (!option.isRequired())
            {
                System.out.println("--" + option.getLongOpt() + "|-" + option.getOpt() + "\t\t-\t" + option.getDescription());
            }
        }
    }

    static boolean isDirectoryAStoreDir(File directory)
    {
        if (directory.isFile())
        {
            return false;
        }

        for (File file : directory.listFiles())
        {
            if (file.isFile())
            {
                if (file.getName().endsWith(BDB_FILE_ENDING))
                {
                    return true;
                }
            }
        }
        return false;
    }

    static File[] discoverDBStores(File fromDir)
    {
        if (!fromDir.exists())
        {
            throw new IllegalArgumentException("'" + fromDir + "' does not exist unable to upgrade.");
        }

        // Ensure we are given a directory
        if (fromDir.isFile())
        {
            throw new IllegalArgumentException("'" + fromDir + "' is not a directory unable to upgrade.");
        }

        // Check to see if we have been given a single directory
        if (isDirectoryAStoreDir(fromDir))
        {
            return new File[]{fromDir};
        }

        // Check to see if we have been give a directory containing stores.
        List<File> stores = new LinkedList<File>();

        for (File directory : fromDir.listFiles())
        {
            if (directory.isDirectory())
            {
                if (isDirectoryAStoreDir(directory))
                {
                    stores.add(directory);
                }
            }
        }

        return stores.toArray(new File[stores.size()]);
    }

    private static void performDBBackup(File source, File backup, boolean force) throws Exception
    {
        if (backup.exists())
        {
            if (force)
            {
                _logger.info("Backup location exists. Forced to remove.");
                FileUtils.delete(backup, true);
            }
            else
            {
                throw new IllegalArgumentException("Unable to perform backup a backup already exists.");
            }
        }

        try
        {
            _logger.info("Backing up '" + source + "' to '" + backup + "'");
            FileUtils.copyRecursive(source, backup);
        }
        catch (FileNotFoundException e)
        {
            //Throwing IAE here as this will be reported as a Backup not started
            throw new IllegalArgumentException("Unable to perform backup:" + e.getMessage());
        }
        catch (FileUtils.UnableToCopyException e)
        {
            //Throwing exception here as this will be reported as a Failed Backup
            throw new Exception("Unable to perform backup due to:" + e.getMessage());
        }
    }

    public static void main(String[] args) throws ParseException
    {
        setOptions(_options);

        final Options helpOptions = new Options();
        setHelpOptions(helpOptions);

        //Display help
        boolean displayHelp = false;
        try
        {
            if (new PosixParser().parse(helpOptions, args).hasOption("h"))
            {
                showHelp();
            }
        }
        catch (ParseException pe)
        {
            displayHelp = true;
        }

        //Parse commandline for required arguments
        try
        {
            _commandLine = new PosixParser().parse(_options, args);
        }
        catch (ParseException mae)
        {
            if (displayHelp)
            {
                showHelp();
            }
            else
            {
                fatalError(mae.getMessage());
            }
        }

        String fromDir = _commandLine.getOptionValue(OPTION_INPUT_SHORT);
        String toDir = _commandLine.getOptionValue(OPTION_OUTPUT_SHORT);
        String backupDir = _commandLine.getOptionValue(OPTION_BACKUP_SHORT);

        if (backupDir == null && _commandLine.hasOption(OPTION_BACKUP_SHORT))
        {
            backupDir = "";
        }

        //Attempt to locate possible Store to upgrade on input path
        File[] stores = new File[0];
        try
        {
            stores = discoverDBStores(new File(fromDir));
        }
        catch (IllegalArgumentException iae)
        {
            fatalError(iae.getMessage());
        }

        boolean interactive = !_commandLine.hasOption(OPTION_QUIET_SHORT);
        boolean force = _commandLine.hasOption(OPTION_FORCE_SHORT);

        try{
            for (File store : stores)
            {

                // if toDir is null then we are upgrading inplace so we don't need
                // to provide an upgraded toDir when upgrading multiple stores.
                if (toDir == null ||
                    // Check to see if we are upgrading a store specified in
                    // fromDir or if the directories are nested.
                    (stores.length > 0
                     && stores[0].toString().length() == fromDir.length()))
                {
                    upgrade(store, toDir, backupDir, interactive, force);
                }
                else
                {
                    // Add the extra part of path from store to the toDir
                    upgrade(store, toDir + File.separator + store.toString().substring(fromDir.length()), backupDir, interactive, force);
                }
            }
        }
        catch (RuntimeException re)
        {
            if (!(USER_ABORTED_PROCESS).equals(re.getMessage()))
            {
                re.printStackTrace();
                _logger.error("Upgrade Failed: " + re.getMessage());
            }
            else
            {
                _logger.error("Upgrade stopped : User aborted");
            }
        }

    }

    @SuppressWarnings("static-access")
    private static void setOptions(Options options)
    {
        Option input =
                OptionBuilder.isRequired().hasArg().withDescription("Location (Path) of store to upgrade.").withLongOpt(OPTION_INPUT)
                        .create(OPTION_INPUT_SHORT);

        Option output =
                OptionBuilder.hasArg().withDescription("Location (Path) for the upgraded-db to be written.").withLongOpt(OPTION_OUTPUT)
                        .create(OPTION_OUTPUT_SHORT);

        Option quiet = new Option(OPTION_QUIET_SHORT, OPTION_QUIET, false, "Disable interactive options.");

        Option force = new Option(OPTION_FORCE_SHORT, OPTION_FORCE, false, "Force upgrade removing any existing upgrade target.");
        Option backup =
                OptionBuilder.hasOptionalArg().withDescription("Location (Path) for the backup-db to be written.").withLongOpt(OPTION_BACKUP)
                        .create(OPTION_BACKUP_SHORT);

        options.addOption(input);
        options.addOption(output);
        options.addOption(quiet);
        options.addOption(force);
        options.addOption(backup);
        setHelpOptions(options);
    }

    private static void setHelpOptions(Options options)
    {
        options.addOption(new Option("h", "help", false, "Show this help."));
    }

    static void upgrade(File fromDir, String toDir, String backupDir, boolean interactive, boolean force)
    {

        _logger.info("Running BDB Message Store upgrade tool: v" + VERSION);
        int version = getStoreVersion(fromDir);
        if (!isVersionUpgradable(version))
        {
            return;
        }
        try
        {
            new BDBStoreUpgrade(fromDir.toString(), toDir, backupDir, interactive, force).upgradeFromVersion(version);

            _logger.info("Upgrade complete.");
        }
        catch (IllegalArgumentException iae)
        {
            _logger.error("Upgrade not started due to: " + iae.getMessage());
        }
        catch (DatabaseException de)
        {
            de.printStackTrace();
            _logger.error("Upgrade Failed: " + de.getMessage());
        }
        catch (RuntimeException re)
        {
            if (!(USER_ABORTED_PROCESS).equals(re.getMessage()))
            {
                re.printStackTrace();
                _logger.error("Upgrade Failed: " + re.getMessage());
            }
            else
            {
                throw re;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            _logger.error("Upgrade Failed: " + e.getMessage());
        }
    }

    /**
     * Utility method to verify if store of given version can be upgraded.
     * 
     * @param version
     *            store version to verify
     * @return true if store can be upgraded, false otherwise
     */
    protected static boolean isVersionUpgradable(int version)
    {
        boolean storeUpgradable = false;
        if (version == 0)
        {
            _logger.error("Existing store version is undefined!");
        }
        else if (version < LOWEST_SUPPORTED_STORE_VERSION)
        {
            _logger.error(MessageFormat.format(PREVIOUS_STORE_VERSION_UNSUPPORTED,
                    new Object[] { Integer.toString(version) }));
        }
        else if (version == BDBMessageStore.DATABASE_FORMAT_VERSION)
        {
            _logger.error(MessageFormat.format(STORE_ALREADY_UPGRADED, new Object[] { Integer.toString(version) }));
        }
        else if (version > BDBMessageStore.DATABASE_FORMAT_VERSION)
        {
            _logger.error(MessageFormat.format(FOLLOWING_STORE_VERSION_UNSUPPORTED,
                    new Object[] { Integer.toString(version) }));
        }
        else
        {
            _logger.info("Existing store version is " + version);
            storeUpgradable = true;
        }
        return storeUpgradable;
    }

    /**
     * Detects existing store version by checking list of database in store
     * environment
     *
     * @param fromDir
     *            store folder
     * @return version
     */
    public static int getStoreVersion(File fromDir)
    {
        int version = 0;
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(false);
        envConfig.setTransactional(false);
        envConfig.setReadOnly(true);
        Environment environment = null;
        try
        {

            environment = new Environment(fromDir, envConfig);
            List<String> databases = environment.getDatabaseNames();
            for (String name : databases)
            {
                if (name.startsWith("exchangeDb"))
                {
                    if (name.startsWith("exchangeDb_v"))
                    {
                        version = Integer.parseInt(name.substring(12));
                    }
                    else
                    {
                        version = 1;
                    }
                    break;
                }
            }
        }
        catch (Exception e)
        {
            _logger.error("Failure to open existing database: " + e.getMessage());
        }
        finally
        {
            if (environment != null)
            {
                try
                {
                    environment.close();
                }
                catch (Exception e)
                {
                    // ignoring. It should never happen.
                }
            }
        }
        return version;
    }

    private static void fatalError(String message)
    {
        System.out.println(message);
        usage();
        System.exit(1);
    }

    private static void showHelp()
    {
        help();
        System.exit(0);
    }

}
