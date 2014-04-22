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
 *
 */
package org.apache.qpid.disttest.controller.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.qpid.disttest.controller.CommandForClient;
import org.apache.qpid.disttest.message.Command;

public class ConfigTestUtils
{
    public static <C extends Command> void assertCommandForClient(final List<CommandForClient> commandsForClients, final int index, final String expectedRegisteredClientName, final Class<C> expectedCommandClass)
    {
        final CommandForClient commandForClient = commandsForClients.get(index);
        assertEquals(expectedRegisteredClientName, commandForClient.getClientName());
        final Command command = commandForClient.getCommand();
        assertTrue("Command " + index + " is of class " + command.getClass() + " but expecting " + expectedCommandClass,
                expectedCommandClass.isAssignableFrom(command.getClass()));
    }

    public static <C extends Command> void assertCommandEquals(final List<Command> commands, final int index, final Class<C> expectedCommandClass)
    {
        @SuppressWarnings("unchecked")
        C command = (C) getCommand(commands, index); //explicit cast added to get round oracle compiler bug (id 6302954)
        assertTrue("Command " + index + " is of class " + command.getClass() + " but expecting " + expectedCommandClass,
                expectedCommandClass.isAssignableFrom(command.getClass()));
    }

    public static <C extends Command>  C getCommand(final List<Command> commands, final int index)
    {
        @SuppressWarnings("unchecked")
        C command = (C) commands.get(index);
        return command;
    }

}
