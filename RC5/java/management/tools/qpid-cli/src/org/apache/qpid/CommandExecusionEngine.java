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
/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid;

import org.apache.qpid.utils.CommandLineOptionParser;
import org.apache.qpid.utils.JMXinfo;
import org.apache.qpid.commands.*;
import org.apache.qpid.commands.Command;


public class CommandExecusionEngine {
    private Command currentcommand = null;
    private String commandname = null;
    private JMXinfo info = null;

    public CommandExecusionEngine(JMXinfo info) {
        this.info = info;
        this.commandname = info.getCommandLineOptionParser().getcommandname();
    }

    public boolean CommandSelector() {

        if (CommandConstants.INFO_COMMAND.equalsIgnoreCase(this.commandname))
            currentcommand = new Commandinfo(info, this.commandname);
        else if (CommandConstants.LIST_COMMAND.equalsIgnoreCase(this.commandname))
            currentcommand = new Commandlist(info, this.commandname);
        else if (CommandConstants.HELP_COMMAND.equalsIgnoreCase(this.commandname))
            currentcommand = new Commandhelp(info, this.commandname);
        else if (CommandConstants.DELETE_COMMAND.equalsIgnoreCase(this.commandname))
            currentcommand = new Commanddelete(info, this.commandname);
        else if (CommandConstants.MOVE_COMMAND.equalsIgnoreCase(this.commandname))
            currentcommand = new Commandmove(info, this.commandname);
        else if (CommandConstants.VIEW_COMMAND.equalsIgnoreCase(this.commandname))
            currentcommand = new Commandview(info, this.commandname);
        else if (CommandConstants.VIEWCONTENT_COMMAND.equalsIgnoreCase(this.commandname))
            currentcommand = new Commandviewcontent(info, this.commandname);
        else {
            usage();
            return false;
        }
        return true;


    }

    public void runcommand() {
        currentcommand.execute();
    }

    public void usage() {
        System.out.println(commandname + ":Command not found");
    }
}
