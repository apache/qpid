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

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.AMQProtocolVersionException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.AMQException;
import org.apache.commons.configuration.ConfigurationException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileQueueBackingStore implements QueueBackingStore
{
    private static final Logger _log = Logger.getLogger(FileQueueBackingStore.class);

    private AtomicBoolean _closed = new AtomicBoolean(false);
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

        File root = new File(location);
        if (!root.exists())
        {
            throw new ConfigurationException("Specified Flow to Disk root does not exist:" + root.getAbsolutePath());
        }
        else
        {

            if (root.isFile())
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk store as specified root is a file:"+
                           root.getAbsolutePath());
            }

            if(!root.canWrite())
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk store. Unable to write to specified root:"+
                           root.getAbsolutePath());
            }

        }


        File store = new File(_flowToDiskLocation);
        if (store.exists())
        {
            if (!FileUtils.delete(store, true))
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk store as directory already exsits:"
                           + store.getAbsolutePath());
            }

            if (store.isFile())
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk store as specified location is a file:"+
                           store.getAbsolutePath());
            }

        }
        else
        {
            if (!store.getParentFile().getParentFile().canWrite())
            {
                throw new ConfigurationException("Unable to create Temporary Flow to Disk store. Unable to write to parent location:"+
                           store.getParentFile().getParentFile().getAbsolutePath());
            }
        }


        _log.info("Creating Flow to Disk Store : " + store.getAbsolutePath());
        store.deleteOnExit();
        if (!store.mkdirs())
        {
            throw new ConfigurationException("Unable to create Temporary Flow to Disk store:" + store.getAbsolutePath());
        }
    }


    public AMQMessage recover(Long messageId)
     {
         MessageMetaData mmd;
         List<ContentChunk> contentBodies = new LinkedList<ContentChunk>();

         File handle = getFileHandle(messageId);
         handle.deleteOnExit();

         ObjectInputStream input = null;

         Exception error = null;
         try
         {
             input = new ObjectInputStream(new FileInputStream(handle));

             long arrivaltime = input.readLong();

             final AMQShortString exchange = new AMQShortString(input.readUTF());
             final AMQShortString routingKey = new AMQShortString(input.readUTF());
             final boolean mandatory = input.readBoolean();
             final boolean immediate = input.readBoolean();

             int bodySize = input.readInt();
             byte[] underlying = new byte[bodySize];

             input.readFully(underlying, 0, bodySize);

             ByteBuffer buf = ByteBuffer.wrap(underlying);

             ContentHeaderBody chb = ContentHeaderBody.createFromBuffer(buf, bodySize);

             int chunkCount = input.readInt();

             // There are WAY to many annonymous MPIs in the code this should be made concrete.
             MessagePublishInfo info = new MessagePublishInfo()
             {

                 public AMQShortString getExchange()
                 {
                     return exchange;
                 }

                 public void setExchange(AMQShortString exchange)
                 {

                 }

                 public boolean isImmediate()
                 {
                     return immediate;
                 }

                 public boolean isMandatory()
                 {
                     return mandatory;
                 }

                 public AMQShortString getRoutingKey()
                 {
                     return routingKey;
                 }
             };

             mmd = new MessageMetaData(info, chb, chunkCount);
             mmd.setArrivalTime(arrivaltime);

             AMQMessage message;
             if (((BasicContentHeaderProperties) chb.properties).getDeliveryMode() == 2)
             {
                 message = new PersistentAMQMessage(messageId, null);
             }
             else
             {
                 message = new TransientAMQMessage(messageId);
             }

             message.recoverFromMessageMetaData(mmd);

             for (int chunk = 0; chunk < chunkCount; chunk++)
             {
                 int length = input.readInt();

                 byte[] data = new byte[length];

                 input.readFully(data, 0, length);

                 // There are WAY to many annonymous CCs in the code this should be made concrete.
                 try
                 {
                     message.recoverContentBodyFrame(new RecoverDataBuffer(length, data), (chunk + 1 == chunkCount));
                 }
                 catch (AMQException e)
                 {
                     //ignore as this will not occur.
                     // It is thrown by the _transactionLog method in recover on PersistentAMQMessage
                     // but we have created the message with a null log and will never call that method.
                 }
             }

             return message;
         }
         catch (Exception e)
         {
             error = e;
         }
         finally
         {
             try
             {
                 input.close();
             }
             catch (IOException e)
             {
                 _log.info("Unable to close input on message("+messageId+") recovery due to:"+e.getMessage());
             }
         }

        throw new UnableToRecoverMessageException(error);
    }


    public void flow(AMQMessage message) throws UnableToFlowMessageException
    {
        long messageId = message.getMessageId();

        File handle = getFileHandle(messageId);

        //If we have written the data once then we don't need to do it again.
        if (handle.exists())
        {
            _log.debug("Message(" + messageId + ") already flowed to disk.");
            return;
        }

        handle.deleteOnExit();

        ObjectOutputStream writer = null;
        Exception error = null;

        try
        {
            writer = new ObjectOutputStream(new FileOutputStream(handle));

            writer.writeLong(message.getArrivalTime());

            MessagePublishInfo mpi = message.getMessagePublishInfo();
            writer.writeUTF(String.valueOf(mpi.getExchange()));
            writer.writeUTF(String.valueOf(mpi.getRoutingKey()));
            writer.writeBoolean(mpi.isMandatory());
            writer.writeBoolean(mpi.isImmediate());
            ContentHeaderBody chb = message.getContentHeaderBody();

            // write out the content header body
            final int bodySize = chb.getSize();
            byte[] underlying = new byte[bodySize];
            ByteBuffer buf = ByteBuffer.wrap(underlying);
            chb.writePayload(buf);

            writer.writeInt(bodySize);
            writer.write(underlying, 0, bodySize);

            int bodyCount = message.getBodyCount();
            writer.writeInt(bodyCount);

            //WriteContentBody
            for (int index = 0; index < bodyCount; index++)
            {
                ContentChunk chunk = message.getContentChunk(index);
                chunk.reduceToFit();

                byte[] chunkData = chunk.getData().array();

                int length = chunk.getSize();
                writer.writeInt(length);
                writer.write(chunkData, 0, length);
            }
        }
        catch (FileNotFoundException e)
        {
            error = e;
        }
        catch (IOException e)
        {
            error = e;
        }
        finally
        {
            try
            {
                writer.flush();
                writer.close();
            }
            catch (IOException e)
            {
                error = e;
            }
        }

        if (error != null)
        {
            _log.error("Unable to flow message(" + messageId + ") to disk, restoring state.");
            handle.delete();
            throw new UnableToFlowMessageException(messageId, error);
        }
    }

    /**
     * Use the messageId to calculate the file path on disk.
     *
     * Current implementation will give us 256 bins.
     * Therefore the maximum messages that can be flowed before error/platform is:
     * ext3 : 256 bins * 32000  = 8192000
     * FAT32 : 256 bins * 65534 = 16776704
     * Other FS have much greater limits than we need to worry about.
     *
     * @param messageId the Message we need a file Handle for.
     *
     * @return the File handle
     */
    private File getFileHandle(long messageId)
    {
        // grab the 8 LSB to give us 256 bins
        long bin = messageId & 0xFFL;

        String bin_path =_flowToDiskLocation + File.separator + bin;
        File bin_dir = new File(bin_path);

        if (!bin_dir.exists())
        {
            bin_dir.mkdirs();
            bin_dir.deleteOnExit();
        }

        String id = bin_path + File.separator + messageId;

        return new File(id);
    }

    public void delete(Long messageId)
    {
        String id = String.valueOf(messageId);
        File handle = new File(_flowToDiskLocation, id);

        if (handle.exists())
        {
            _log.debug("Message(" + messageId + ") delete flowToDisk.");
            if (!handle.delete())
            {
                throw new RuntimeException("Unable to delete flowToDisk data");
            }
        }
    }

    private class RecoverDataBuffer implements ContentChunk
    {
        private int _length;
        private ByteBuffer _dataBuffer;

        public RecoverDataBuffer(int length, byte[] data)
        {
            _length = length;
            _dataBuffer = ByteBuffer.wrap(data);
        }

        public int getSize()
        {
            return _length;
        }

        public ByteBuffer getData()
        {
            return _dataBuffer;
        }

        public void reduceToFit()
        {

        }

    }

}

