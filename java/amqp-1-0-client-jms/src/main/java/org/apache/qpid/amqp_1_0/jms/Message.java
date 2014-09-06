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

package org.apache.qpid.amqp_1_0.jms;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;


public interface Message extends javax.jms.Message
{

    Destination getJMSReplyTo() throws JMSException;

    Destination getJMSDestination() throws JMSException;

    // properties can be keyed by any valid apache.qpid.amqp_1_0 datatype, not just strings

    boolean propertyExists(Object name) throws JMSException;

    boolean getBooleanProperty(Object name) throws JMSException;

    byte getByteProperty(Object name) throws JMSException;

    short getShortProperty(Object name) throws JMSException;

    int getIntProperty(Object name) throws JMSException;

    long getLongProperty(Object name) throws JMSException;

    float getFloatProperty(Object name) throws JMSException;

    double getDoubleProperty(Object name) throws JMSException;

    String getStringProperty(Object name) throws JMSException;

    Object getObjectProperty(Object name) throws JMSException;

    // apache.qpid.amqp_1_0 allows for lists, maps, and unsigned integral data types

    List<Object> getListProperty(Object name) throws JMSException;

    Map<Object,Object> getMapProperty(Object name) throws JMSException;

    UnsignedByte getUnsignedByteProperty(Object name) throws JMSException;

    UnsignedShort getUnsignedShortProperty(Object name) throws JMSException;

    UnsignedInteger getUnsignedIntProperty(Object name) throws JMSException;

    UnsignedLong getUnsignedLongProperty(Object name) throws JMSException;

    // properties can be keyed by any valid apache.qpid.amqp_1_0 datatype, not just strings

    void setBooleanProperty(Object name, boolean b) throws JMSException;

    void setByteProperty(Object name, byte b) throws JMSException;

    void setShortProperty(Object name, short i) throws JMSException;

    void setIntProperty(Object name, int i) throws JMSException;

    void setLongProperty(Object name, long l) throws JMSException;

    void setFloatProperty(Object name, float v) throws JMSException;

    void setDoubleProperty(Object name, double v) throws JMSException;

    void setStringProperty(Object name, String s1) throws JMSException;

    void setObjectProperty(Object name, Object o) throws JMSException;

    // apache.qpid.amqp_1_0 allows for lists, maps, and unsigned integral data types

    void setListProperty(Object name, List<Object> list) throws JMSException;

    void setMapProperty(Object name, Map<Object,Object> map) throws JMSException;

    void setUnsignedByteProperty(Object name, UnsignedByte b) throws JMSException;

    void setUnsignedShortProperty(Object name, UnsignedShort s) throws JMSException;

    void setUnsignedIntProperty(Object name, UnsignedInteger i) throws JMSException;

    void setUnsignedLongProperty(Object name, UnsignedLong l) throws JMSException;

    // delegation accessors for Header section

    UnsignedInteger getDeliveryFailures();

    void setDeliveryFailures(UnsignedInteger failures);

    Boolean getDurable();

    void setDurable(Boolean durable);

    UnsignedByte getPriority();

    void setPriority(UnsignedByte priority);

    Date getTransmitTime();

    void setTransmitTime(Date transmitTime);

    UnsignedInteger getTtl();

    void setTtl(UnsignedInteger ttl);

    UnsignedInteger getFormerAcquirers();

    void setFormerAcquirers(UnsignedInteger formerAcquirers);

    // delegation accessors for Properties section

    Object getMessageId();

    void setMessageId(Object messageId);

    Binary getUserId();

    void setUserId(Binary userId);

    String getTo();

    void setTo(String to);

    String getSubject();

    void setSubject(String subject);

    String getReplyTo();

    void setReplyTo(String replyTo);

    Object getCorrelationId();

    void setCorrelationId(Binary correlationId);

    Symbol getContentType();

    void setContentType(Symbol contentType);
}
