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
package org.apache.qpid.server.stats;

/**
 * This interface is to be implemented by any broker business object that
 * wishes to gather statistics about messages delivered through it.
 * 
 * These statistics are exposed using a separate JMX Mbean interface, which
 * calls these methods to retrieve the underlying {@link StatisticsCounter}s
 * and return their attributes. This interface gives a standard way for
 * parts of the broker to set up and configure statistics generation.
 * <p>
 * When creating these objects, there should be a parent/child relationship
 * between them, such that the lowest level gatherer can record staticics if
 * enabled, and pass on the notification to the parent object to allow higher
 * level aggregation. When resetting statistics, this works in the opposite
 * direction, with higher level gatherers also resetting all of their children.
 */
public interface StatisticsGatherer
{
    /**
     * Initialise the statistics gathering for this object.
     * 
     * This method is responsible for creating any {@link StatisticsCounter}
     * objects and for determining whether statistics generation should be
     * enabled, by checking broker and system configuration.
     * 
     * @see StatisticsCounter#DISABLE_STATISTICS
     */
    void initialiseStatistics();
    
    /**
     * This method is responsible for registering the receipt of a message
     * with the counters, and also for passing this notification to any parent
     * {@link StatisticsGatherer}s. If statistics generation is not enabled,
     * then this method should simple delegate to the parent gatherer.
     * 
     * @param messageSize the size in bytes of the delivered message
     * @param timestamp the time the message was delivered
     */
    void registerMessageReceived(long messageSize, long timestamp);
    
    /**
     * This method is responsible for registering the delivery of a message
     * with the counters. Message delivery is recorded by the counter using
     * the current system time, as opposed to the message timestamp.
     * 
     * @param messageSize the size in bytes of the delivered message
     * @see #registerMessageReceived(long, long)
     */
    void registerMessageDelivered(long messageSize);
    
    /**
     * Gives access to the {@link StatisticsCounter} that is used to count
     * delivered message statistics.
     * 
     * @return the {@link StatisticsCounter} that counts delivered messages
     */
    StatisticsCounter getMessageDeliveryStatistics();
    
    /**
     * Gives access to the {@link StatisticsCounter} that is used to count
     * received message statistics.
     * 
     * @return the {@link StatisticsCounter} that counts received messages
     */
    StatisticsCounter getMessageReceiptStatistics();
    
    /**
     * Gives access to the {@link StatisticsCounter} that is used to count
     * delivered message size statistics.
     * 
     * @return the {@link StatisticsCounter} that counts delivered bytes
     */
    StatisticsCounter getDataDeliveryStatistics();
    
    /**
     * Gives access to the {@link StatisticsCounter} that is used to count
     * received message size statistics.
     * 
     * @return the {@link StatisticsCounter} that counts received bytes
     */
    StatisticsCounter getDataReceiptStatistics();
    
    /**
     * Reset the counters for this, and any child {@link StatisticsGatherer}s.
     */
    void resetStatistics();
    
    /**
     * Check if this object has statistics generation enabled.
     * 
     * @return true if statistics generation is enabled
     */
    boolean isStatisticsEnabled();
    
    /**
     * Enable or disable statistics generation for this object.
     */
    void setStatisticsEnabled(boolean enabled);
}
