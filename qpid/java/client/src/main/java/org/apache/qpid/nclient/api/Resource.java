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
package org.apache.qpid.nclient.api;

import org.apache.qpidity.QpidException;

/**
 * A Resource is associated with a session and can be independently closed.
 *
 * Created by Arnaud Simon
 * Date: 21-Jul-2007
 * Time: 09:41:30
 */
public interface Resource
{

    /**
     * Close this resource.
     * <p> Any blocking receive must return null.
     * <p> For asynchronous receiver, this operation blocks until the message listener
     * finishes processing the current message,
     * 
     * @throws QpidException If the session fails to close this resource due to some error
     */
    public void close() throws
                        QpidException;

    /**
     * Get this resource session.
     *
     * @return This resource's session. 
     */
    public Session  getSession();

    /**
     * Get the queue name to which this resource is tied.
     *
     * @return The queue name of this resource. 
     */
    public String getQueueNAme();
}
