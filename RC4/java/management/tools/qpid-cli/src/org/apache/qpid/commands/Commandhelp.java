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

import org.apache.qpid.utils.JMXinfo;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Jul 6, 2008
 * Time: 5:55:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class Commandhelp extends Command {
    public Commandhelp(JMXinfo info, String name) {
        super(info, name);
    }

    public void execute() {
        displayhelp();
    }

    private void displayhelp() {
        echo("");
        echo("Current version of qpid CLI is supporting following commands");
        echo("");
        echo("[list]    This command is listing limited information about a given type of object");
        echo("          For more information about list command run `list --help`");
        echo("[info]    This command is listing All the information about a given type of object");
        echo("          For more information about list command run `info --help`");

        echo("");
        echo("[exit]    This command is disconnect the connection with the Qpid Java broker and go back to normal propmt");
        echo("[quit]    This command is disconnect the connection with the Qpid Java broker and go back to normal propmt");
        echo("");
    }
}
