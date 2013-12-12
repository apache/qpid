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

// This header file is just for doxygen documentation purposes.

/** \mainpage Qpid Developer Documentation
 *
 * <h2>Starting Points: Broker</h2>
 *
 *  
 * <p>Message flow generally starts from the following methods:</p>
 *
 * <ul>
 *   <li><p>qpid::broker::Connection::received() - handles incoming decoded frames from the IO layer</p></li>
 *   <li><p>qpid::broker::Connection::doOutput() - sends messages to eligible consumers</p></li>
 * </ul>
 *
 * <p>The following classes are useful starting points for
 * understanding the structure of the broker:</p>
 *
 * <p>IO:</p>
 * <ul><li><p>qpid::sys::Poller</p></li></ul>
 * 
 * <p>Broker - per Client</p>
 * <ul>
 *   <li><p>qpid::broker::Connection</p></li>
 *   <li><p>qpid::broker::SemanticState</p></li>
 *   <li><p>qpid::broker::SemanticState::ConsumerImpl</p></li>
 * </ul>
 *
 * <p>Broker - shared among all clients</p>
 *
 * <ul>
 *   <li><p>qpid::broker::Queue</p></li>
 *   <li><p>qpid::broker::Exchange</p></li>
 *   <li><p>Subclasses of Queue and Exchange</p></li>
 * </ul>
 *
 * Add-on modules to the broker:
 *
 * - \ref ha-module "High Availability"
 *
 *
 *
 * <h2>Starting Points: Client</h2>
 *
 * <p>TBD</p>
 *
 * <h2>Starting Points: Management</h2>
 *
 * <p>TBD</p>
 *
 */
