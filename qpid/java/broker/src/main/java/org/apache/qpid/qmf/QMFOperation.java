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

package org.apache.qpid.qmf;

public enum QMFOperation
{


    BROKER_REQUEST('B'),
    BROKER_RESPONSE('b'),           // This message contains a broker response, sent from the broker in response to a broker request message.
    COMMAND_COMPLETION('z'),        // This message is sent to indicate the completion of a request.
    CLASS_QUERY('Q'),               // Class query messages are used by a management console to request a list of schema classes that are known by the management broker.
    CLASS_INDICATION('q'),          // Sent by the management broker, a class indication notifies the peer of the existence of a schema class.
    SCHEMA_REQUEST('S'),            // Schema request messages are used to request the full schema details for a class.
    SCHEMA_RESPONSE('s'),           // Schema response message contain a full description of the schema for a class.
    HEARTBEAT_INDEICATION('h'),     // This message is published once per publish-interval. It can be used by a client to positively determine which objects did not change during the interval (since updates are not published for objects with no changes).
    CONFIG_INDICATION('c'),
    INSTRUMENTATION_INDICATION('i'),
    GET_QUERY_RESPONSE('g'),      // This message contains a content record. Content records contain the values of all properties or statistics in an object. Such records are broadcast on a periodic interval if 1) a change has been made in the value of one of the elements, or 2) if a new management client has bound a queue to the management exchange.
    GET_QUERY('G'), 	            // Sent by a management console, a get query requests that the management broker provide content indications for all objects that match the query criteria.
    METHOD_REQUEST('M'), 	        // This message contains a method request.
    METHOD_RESPONSE('m'), 	        // This message contains a method result.
    PACKAGE_QUERY('P'), 	        // This message contains a schema package query request, requesting that the broker dump the list of known packages
    PACKAGE_INDICATION('p'), 	    // This message contains a schema package indication, identifying a package known by the broker
    AGENT_ATTACH_REUQEST('A'), 	    // This message is sent by a remote agent when it wishes to attach to a management broker
    AGENT_ATTACH_RESPONSE('a'), 	// The management broker sends this response if an attaching remote agent is permitted to join
    CONSOLE_ADDED_INDICATION('x'),     // This message is sent to all remote agents by the management broker when a new console binds to the management exchange
    EVENT('e')
    ;


    private final char _opcode;

    private static final QMFOperation[] OP_CODES = new QMFOperation[256];


    QMFOperation(char opcode)
    {
        _opcode = opcode;
    }


    public char getOpcode()
    {
        return _opcode;
    }

}
