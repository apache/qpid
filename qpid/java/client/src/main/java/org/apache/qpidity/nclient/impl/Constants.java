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

package org.apache.qpidity.nclient.impl;

/**
 * This class holds all the 0.10 client constants which value can be set
 * through properties.
 */
public class Constants
{
    static
    {

        String max="message_size_before_sync";// KB's
        try
        {
            MAX_NOT_SYNC_DATA_LENGH=new Long(System.getProperties().getProperty(max, "200000000"));
        }
        catch (NumberFormatException e)
        {
            // use default size
            MAX_NOT_SYNC_DATA_LENGH=200000000;
        }
        String flush="message_size_before_flush";
        try
        {
            MAX_NOT_FLUSH_DATA_LENGH=new Long(System.getProperties().getProperty(flush, "2000000"));
        }
        catch (NumberFormatException e)
        {
            // use default size
            MAX_NOT_FLUSH_DATA_LENGH=20000000;
        }
    }

    /**
     * The total message size in KBs that can be transferted before
     * client and broker are synchronized.
     * A sync will result in the client library releasing the sent messages
     * from memory. (messages are kept
     * in memory so client can reconnect to a broker in the event of a failure)
     * <p>
     * Property name: message_size_before_sync
     * <p>
     * Default value: 200000000
     */
    public static long MAX_NOT_SYNC_DATA_LENGH;
    /**
     * The total message size in KBs that can be transferted before
     * messages are flushed.
     * When a flush returns all messages have reached the broker.
     * <p>
     * Property name: message_size_before_flush
     * <p>
     * Default value: 200000000 
     */
    public static long MAX_NOT_FLUSH_DATA_LENGH;

}
