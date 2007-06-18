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

package org.apache.qpid.management.ui.views;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.eclipse.swt.widgets.TabFolder;

/**
 * Controller class, which takes care of displaying appropriate information and widgets for Connections.
 * This allows user to select Connections and add those to the navigation view
 */
public class ConnectionTypeTabControl extends MBeanTypeTabControl
{

    public ConnectionTypeTabControl(TabFolder tabFolder)
    {
        super(tabFolder, Constants.CONNECTION);
        createWidgets();
    }
    
    protected void createWidgets()
    {
        createHeaderComposite(getFormComposite());
        createButtonsComposite(getFormComposite());
        createListComposite(getFormComposite());
    }
    
    protected void populateList() throws Exception
    {
        // map should be cleared before populating it with new values
        getMBeansMap().clear();
        
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(MBeanView.getServer());
        java.util.List<ManagedBean> list = serverRegistry.getConnections(MBeanView.getVirtualHost());
        getListWidget().setItems(getItems(list));  
    }
}
