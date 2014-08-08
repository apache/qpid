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
package org.apache.qpid.client;

/**
 * This enum can be used only with for 0-8/0-9/0-9-1 protocols connections to notify
 * the client to delegate the requeue/DLQ decision to the server
 * if <code>SERVER</code> value is specified. Otherwise the messages won't be moved to
 * the DLQ (or dropped) when delivery count exceeds the maximum.
 */
public enum RejectBehaviour
{
    NORMAL, SERVER;
}
