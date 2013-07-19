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

package org.apache.qpid.server.qmf2;

// Misc Imports
import java.util.Map;
import java.util.UUID;

// Java Broker Management Imports
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.plugin.PluginFactory;

/**
 * This class is an implementation of org.apache.qpid.server.plugin.PluginFactory which is the interface
 * used by the Qpid Java Broker to create Management Plugins.
 * <p>
 * The factory method is createInstance() which returns a concrete instance of org.apache.qpid.server.model.Plugin
 * in this case the concrete instance is QmfManagementPlugin.
 * <p>
 * The Java broker uses org.apache.qpid.server.plugin.QpidServiceLoader, which wraps java.util.ServiceLoader to
 * load Plugins. This has a prerequisite of having a services file specified in the META-INF see
 * <a href=http://docs.oracle.com/javase/6/docs/api/java/util/ServiceLoader.html>ServiceLoader</a> e.g.
 * <pre>
 * META-INF/services/org.apache.qpid.server.plugin.PluginFactory
 * </pre>
 * This is best done by using the ServiceProvider block in the jar ant task e.g.
 * <pre>
 * &lt;jar destfile="build/lib/qpid-broker-plugins-management-qmf2.jar"
 *      basedir="build/scratch/qpid-broker-plugins-management-qmf2"&gt;
 *
 *      &lt;service type="org.apache.qpid.server.plugin.PluginFactory" 
 *               provider="org.apache.qpid.server.qmf2.QmfManagementFactory"/&gt;
 * &lt;/jar&gt;
 * </pre>
 * @author Fraser Adams
 */
public class QmfManagementFactory implements PluginFactory
{
    /**
     * This factory method creates an instance of QmfManagementPlugin called via the QpidServiceLoader.
     * @param id the UUID of the Plugin.
     * @param attributes a Map containing configuration information for the Plugin.
     * @param broker the root Broker Management Object from which the other Management Objects may be obtained.
     * @return the QmfManagementPlugin instance which creates a QMF2 Agent able to interrogate the broker Management
     * Objects and return their properties as QmfData.
     */
    @Override
    public Plugin createInstance(UUID id, Map<String, Object> attributes, Broker broker)
    {
        if (QmfManagementPlugin.PLUGIN_TYPE.equals(attributes.get(PLUGIN_TYPE)))
        {
            return new QmfManagementPlugin(id, broker, attributes);
        }
        else
        {
            return null;
        }
    }

    @Override
    public String getType()
    {
        return "QMF2 Management";
    }
}
