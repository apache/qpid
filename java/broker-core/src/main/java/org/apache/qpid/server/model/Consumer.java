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
package org.apache.qpid.server.model;

@ManagedObject
public interface Consumer<X extends Consumer<X>> extends ConfiguredObject<X>
{
    public String DISTRIBUTION_MODE = "distributionMode";
    public String EXCLUSIVE = "exclusive";
    public String NO_LOCAL = "noLocal";
    public String SELECTOR = "selector";
    public String SETTLEMENT_MODE = "settlementMode";

    @ManagedAttribute( automate = true )
    String getDistributionMode();

    @ManagedAttribute( automate = true )
    String getSettlementMode();

    @ManagedAttribute( automate = true )
    boolean isExclusive();

    @ManagedAttribute( automate = true )
    boolean isNoLocal();

    @ManagedAttribute( automate = true )
    String getSelector();

    @ManagedStatistic
    long getBytesOut();

    @ManagedStatistic
    long getMessagesOut();

    @ManagedStatistic
    long getUnacknowledgedBytes();

    @ManagedStatistic
    long getUnacknowledgedMessages();

}
