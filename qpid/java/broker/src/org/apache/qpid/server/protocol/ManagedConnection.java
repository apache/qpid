/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.qpid.server.protocol;

import org.apache.qpid.AMQException;

import javax.management.openmbean.TabularData;
import javax.management.openmbean.OpenDataException;
import java.util.Date;

/**
 * The management interface exposed to allow management of Connections.
 * @author   Bhupendra Bhardwaj
 * @version  0.1
 */
public interface ManagedConnection
{
    static final String TYPE = "Connection";

    /**
     * Tells the last time, the IO operation was done.
     * @return last IO time.
     */
    Date getLastIoTime();

    /**
     * Tells the remote address of this connection.
     * @return  remote address
     */
    String getRemoteAddress();

    /**
     * Tells the total number of bytes written till now.
     * @return number of bytes written.
     */
    long getWrittenBytes();

    /**
     * Tells the total number of bytes read till now.
     * @return number of bytes read.
     */
    long getReadBytes();

    /**
     * Tells the maximum number of channels that can be opened using
     * this connection.  This is useful in setting notifications or
     * taking required action is there are more channels being created.
     * @return maximum number of channels allowed to be created.
     */
    long getMaximumNumberOfAllowedChannels();

    /**
     * Sets the maximum number of channels allowed to be created using
     * this connection.
     * @param value
     */
    void setMaximumNumberOfAllowedChannels(long value);

    //********** Operations *****************//

    /**
     * Returns channel details of all the channels opened for this connection.
     * @return  channel details.
     */
    TabularData viewChannels() throws OpenDataException;

    /**
     * Closes all the channels and unregisters this connection from managed objects.
     */
    void closeConnection() throws Exception;

    /**
     * Unsubscribes the consumers and unregisters the channel from managed objects.
     */
    void closeChannel(int channelId) throws Exception;
}
