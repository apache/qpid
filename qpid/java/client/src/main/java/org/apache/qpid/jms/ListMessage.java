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
package org.apache.qpid.jms;

import javax.jms.JMSException;

import java.util.Iterator;
import java.util.List;

public interface ListMessage extends javax.jms.StreamMessage
{
    boolean add(Object e) throws JMSException;

    void add(int index, Object e) throws JMSException;

    boolean contains(Object e) throws JMSException;

    Object get(int index) throws JMSException;

    int indexOf(Object e) throws JMSException;

    Iterator<Object> iterator() throws JMSException;

    Object remove(int index) throws JMSException;

    boolean remove(Object e)throws JMSException;

    Object set(int index, Object e) throws JMSException;

    int size() throws JMSException;

    Object[] toArray() throws JMSException;

    List<Object> asList() throws JMSException;

    void setList(List<Object> l) throws JMSException;
}
