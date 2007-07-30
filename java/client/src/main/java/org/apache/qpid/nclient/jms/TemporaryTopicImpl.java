/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.nclient.jms;

import javax.jms.TemporaryTopic;
import javax.jms.JMSException;


/**
 * Implements TemporaryTopic
 */
public class TemporaryTopicImpl extends TopicImpl implements TemporaryTopic, TemporaryDestination
{
    /**
     * Indicates whether this temporary topic is deleted.
     */
    private boolean _isDeleted = false;

    //--- constructor
    public TemporaryTopicImpl()
    {
        // temporary destinations do not have names and are not registered in the JNDI namespace.
        super("NAME_NOT_SET");
    }

    //-- TemporaryDestination Interface
    public boolean isdeleted()
    {
        return _isDeleted;
    }

    //-- TemporaryTopic Interface
    public void delete() throws JMSException
    {
        // todo: delete this destinaiton
        _isDeleted = true;
    }
}
