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

package org.apache.qpid.commands;


import org.apache.qpid.utils.CommandLineOptionParser;
import org.apache.qpid.utils.JMXinfo;
import org.apache.qpid.utils.CommandLineOption;

import javax.management.remote.JMXConnector;
import javax.management.MBeanServerConnection;
import java.util.Map;


public class Command {
    public JMXinfo info = null;
    public String commandname = null;


    public Command(JMXinfo info, String name) {
        this.info = info;
        this.commandname = name;
    }

    public Command() {

    }

    public void execute() {


    }

    public void printusage() {
    }


    public String optionchecker(String option_letter) {
        Map map = info.getCommandLineOptionParser().getAlloptions();
        if (map == null)
            return null;
        CommandLineOption option = (CommandLineOption) map.get(option_letter);
        if (option == null)
            return null;
        String value = option.getOptionValue();
        return value;
    }

    public boolean checkoptionsetting(String option_letter) {
        Map map = info.getCommandLineOptionParser().getAlloptions();
        if (map == null)
            return false;
        CommandLineOption option = (CommandLineOption) map.get(option_letter);
        if (option == null)
            return false;
        String value = option.getOptionType();

        if (value != null)
            return true;
        else
            return false;
    }

    public void optionvaluechecker() {

    }

    public void echo(String str) {
        System.out.println(str);
    }

    public void unrecognizeoption() {
        echo("list: Unrecognized option");
        echo("Try `" + this.commandname + " --help` for more information");
    }

}
