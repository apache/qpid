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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.util.FileUtils;

import java.io.File;

public class FileQueueBackingStoreFactory implements QueueBackingStoreFactory
{
    private static final Logger _log = Logger.getLogger(FileQueueBackingStoreFactory.class);

    private String _flowToDiskLocation;
    private static final String QUEUE_BACKING_DIR = "queueBacking";

    public void configure(VirtualHost virtualHost, VirtualHostConfiguration config) throws ConfigurationException
    {
        setFlowToDisk(virtualHost.getName(), config.getFlowToDiskLocation());
    }

    private void setFlowToDisk(String vHostName, String location) throws ConfigurationException
    {
        if (vHostName == null)
        {
            throw new ConfigurationException("Unable to setup to Flow to Disk as Virtualhost name was not specified");
        }

        if (location == null)
        {
            throw new ConfigurationException("Unable to setup to Flow to Disk as location was not specified.");
        }

        _flowToDiskLocation = location;

        _flowToDiskLocation += File.separator + QUEUE_BACKING_DIR + File.separator + vHostName;

        //Check the location we will create QUEUE_BACKING_DIR in.
        File root = new File(location);
        if (!root.exists())
        {
            throw new ConfigurationException("Specified Flow to Disk root does not exist:" + root.getAbsolutePath());
        }
        else
        {

            if (root.isFile())
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk store as specified root is a file:" +
                                                 root.getAbsolutePath());
            }

            if (!root.canWrite())
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk store. Unable to write to specified root:" +
                                                 root.getAbsolutePath());
            }

        }
        
        // if we don't mark QUEUE_BAKCING_DIR as a deleteOnExit it will remain.        
        File backingDir = new File(location + File.separator + QUEUE_BACKING_DIR);
        if (backingDir.exists())
        {
            if (!FileUtils.delete(backingDir, true))
            {
                throw new ConfigurationException("Unable to delete existing Flow to Disk root at:"
                                                 + backingDir.getAbsolutePath());
            }

            if (backingDir.isFile())
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk root as specified location is a file:" +
                                                 backingDir.getAbsolutePath());
            }
        }
        
        backingDir.deleteOnExit();
        if (!backingDir.mkdirs())
        {
            throw new ConfigurationException("Unable to create Temporary Flow to Disk root:" + location + File.separator + QUEUE_BACKING_DIR);
        }


        File store = new File(_flowToDiskLocation);
        if (store.exists())
        {
            if (!FileUtils.delete(store, true))
            {
                throw new ConfigurationException("Unable to delete existing Flow to Disk store at:"
                                                 + store.getAbsolutePath());
            }

            if (store.isFile())
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk store as specified location is a file:" +
                                                 store.getAbsolutePath());
            }

        }

        _log.info("Creating Flow to Disk Store : " + store.getAbsolutePath());
        store.deleteOnExit();
        if (!store.mkdir())
        {
            throw new ConfigurationException("Unable to create Temporary Flow to Disk store:" + store.getAbsolutePath());
        }
    }

    public QueueBackingStore createBacking(AMQQueue queue)
    {
        return new FileQueueBackingStore(createStore(queue.getName().toString()));
    }

    private String createStore(String name)
    {
        return createStore(name, 0);
    }

    /**
     * Returns a hash code for non-null Object x.
     * Uses the same hash code spreader as most other java.util hash tables.
     *
     * Borrowed from the Apache Harmony project
     * @param x the object serving as a key
     * @return the hash code
     */
    public static int hash(Object x) {
        int h = x.hashCode();
        h += ~(h << 9);
        h ^=  (h >>> 14);
        h +=  (h << 4);
        h ^=  (h >>> 10);
        return h;
    }

    private String createStore(String name, int index)
    {

        int hash = hash(name);

        long bin = hash & 0xFFL;

        String store = _flowToDiskLocation + File.separator + bin + File.separator + name;

        if (index > 0)
        {
            store += "-" + index;
        }

        //TODO ensure name is safe for the OS i.e. on OSX you can't have any ':'
        // Does java take care of this?

        File storeFile = new File(store);

        if (storeFile.exists())
        {
            return createStore(name, index + 1);
        }

        // Ensure we report an error if we cannot create the backing store.
        if (!storeFile.mkdirs())
        {
            _log.error("Unable to create queue backing directory for queue:" + name);
            throw new RuntimeException("Unable to create queue backing directory for queue:" + name);
        }

        storeFile.deleteOnExit();

        return store;
    }

}
