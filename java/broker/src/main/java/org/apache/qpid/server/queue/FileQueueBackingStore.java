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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class FileQueueBackingStore implements QueueBackingStore
{
    private static final Logger _log = Logger.getLogger(FileQueueBackingStore.class);

    private String _flowToDiskLocation;

    public FileQueueBackingStore(String location)
    {
        _flowToDiskLocation = location;
    }

    public AMQMessage load(Long messageId)
    {
        _log.info("Loading Message (ID:" + messageId + ")");

        MessageMetaData mmd;

        File handle = getFileHandle(messageId);

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
            if (((BasicContentHeaderProperties) chb.properties).getDeliveryMode() == 
                                                BasicContentHeaderProperties.PERSISTENT)
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

                try
                {
                    message.recoverContentBodyFrame(new RecoverDataBuffer(length, data), (chunk + 1 == chunkCount));
                }
                catch (AMQException e)
                {
                    //ignore as this will not occur.
                    // It is thrown by the _transactionLog method in load on PersistentAMQMessage
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
                if (input != null)
                {
                    input.close();
                }
            }
            catch (IOException e)
            {
                _log.info("Unable to close input on message(" + messageId + ") recovery due to:" + e.getMessage());
            }
        }

        throw new UnableToRecoverMessageException(error);
    }

    /**
     * Thread safety is ensured here by synchronizing on the message object.
     *
     * This is safe as load() calls will fail until the first thread through here has created the file on disk
     * and fully written the content.
     *
     * After this point new AMQMessages can exist that reference the same data thus breaking the synchronisation.
     *
     * Thread safety is maintained here as the existence of the file is checked allowing then subsequent unload() calls
     * to skip the writing.
     *
     * Multiple unload() calls will initially be blocked using the synchronization until the data exists on disk thus
     * safely allowing any reference to the message to be cleared prompting a load call.
     *
     * @param message the message to unload
     * @throws UnableToFlowMessageException
     */
    public void unload(AMQMessage message) throws UnableToFlowMessageException
    {
        //Synchorize on the message to ensure that one only thread can unload at a time.
        // If a second unload is attempted then it will block until the unload has completed.
        synchronized (message)
        {
            long messageId = message.getMessageId();

            File handle = getFileHandle(messageId);

            //If we have written the data once then we don't need to do it again.
            if (handle.exists())
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug("Message(ID:" + messageId + ") already unloaded.");
                }
                return;
            }

            if (_log.isInfoEnabled())
            {
                _log.info("Unloading Message (ID:" + messageId + ")");
            }

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
                    int length = chunk.getSize();

                    byte[] chunk_underlying = new byte[length];

                    ByteBuffer chunk_buf = chunk.getData();

                    chunk_buf.duplicate().rewind().get(chunk_underlying);

                    writer.writeInt(length);
                    writer.write(chunk_underlying, 0, length);
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
                // In a FileNotFound situation writer will be null.
                if (writer != null)
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
            }

            if (error != null)
            {
                _log.error("Unable to unload message(" + messageId + ") to disk, restoring state.");
                handle.delete();
                throw new UnableToFlowMessageException(messageId, error);
            }
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

        String bin_path = _flowToDiskLocation + File.separator + bin;
        File bin_dir = new File(bin_path);

        if (!bin_dir.exists())
        {
            bin_dir.mkdirs();
        }

        String id = bin_path + File.separator + messageId;

        return new File(id);
    }

    public void delete(Long messageId)
    {
        File handle = getFileHandle(messageId);

        if (handle.exists())
        {
            if (_log.isInfoEnabled())
            {
                _log.info("Message(" + messageId + ") delete flowToDisk.");
            }
            if (!handle.delete())
            {
                throw new RuntimeException("Unable to delete flowToDisk data");
            }
        }
    }

    public void close()
    {
        _log.info("Closing Backing store at:" + _flowToDiskLocation);
        if (!FileUtils.delete(new File(_flowToDiskLocation), true))
        {
            // Attempting a second time appears to ensure that it is deleted.
            if (!FileUtils.delete(new File(_flowToDiskLocation), true))
            {
                _log.error("Unable to fully delete backing store location");
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

