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

/**
 * This file allows the initial set of Console Connections to be configured and delivered to the client.
 * The format is as a JSON array of JSON objects containing a url property and optional name, connectionOptions and
 * disableEvents properties. The connectionOptions property has a value that is itself a JSON object containing
 * the connectionOptions supported by the qpid::messaging API.
 */

/*
// Example Console Connection Configuration.
qmfui.Console.consoleConnections = [
    {name: "default", url: ""},
    {name: "localhost", url: "localhost:5672"},
    {name: "wildcard", url: "anonymous/@0.0.0.0:5672", connectionOptions: {sasl_mechs:"ANONYMOUS"}, disableEvents: true}
];
*/

