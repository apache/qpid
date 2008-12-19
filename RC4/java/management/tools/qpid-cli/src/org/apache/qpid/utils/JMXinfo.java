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
package org.apache.qpid.utils;

import javax.management.remote.JMXConnector;
import javax.management.MBeanServerConnection;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Jun 3, 2008
 * Time: 9:21:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class JMXinfo {
    private JMXConnector jmxconnector;
    private CommandLineOptionParser input;
    private MBeanServerConnection mbserverconnector;

    public JMXinfo(JMXConnector jmxc, CommandLineOptionParser input, MBeanServerConnection mbsc) {
        this.jmxconnector = jmxc;
        this.input = input;
        this.mbserverconnector = mbsc;
    }

    public JMXConnector getjmxconnectot() {
        return jmxconnector;
    }

    public CommandLineOptionParser getCommandLineOptionParser() {
        return input;
    }

    public MBeanServerConnection getmbserverconnector() {
        return mbserverconnector;
    }
}
