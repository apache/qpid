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

/**
 * MessageListeners are used to asynchronously receive messages.
 *
 * Created by Arnaud Simon
 * Date: 2o-Jul-2007
 * Time: 09:42:52
 */
public interface MessageListener
{
    /**
     * Deliver a message to the listener.
     *
     * @param message The message delivered to the listner. 
     */
    public void onMessage(Message message);
}
