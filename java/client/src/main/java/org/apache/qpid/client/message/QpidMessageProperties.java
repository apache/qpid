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
package org.apache.qpid.client.message;

/**
 * Place holder for Qpid specific message properties
 */
public class QpidMessageProperties 
{
    private QpidMessageProperties()
    {
    }

    public static final String QPID_SUBJECT = "qpid.subject";
    public static final String QPID_SUBJECT_JMS_PROPERTY = "JMS_qpid_subject";
    public static final String QPID_SUBJECT_JMS_PROPER = QPID_SUBJECT_JMS_PROPERTY.substring(4);
    
    // AMQP 0-10 related properties
    public static final String AMQP_0_10_APP_ID = "x-amqp-0-10.app-id";
    public static final String AMQP_0_10_ROUTING_KEY = "x-amqp-0-10.routing-key";
}
