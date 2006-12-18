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

import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.model.OperationData;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TabFolder;

/**
 * Abstract class for all the control classes of tabs.
 * @author Bhupendra Bhardwaj
 */
public abstract class TabControl
{
    protected ManagedBean _mbean = null;
    protected TabFolder _tabFolder = null;
    
    public TabControl(TabFolder tabFolder)
    {
        _tabFolder = tabFolder;
    }
    
    /**
     * @return controller composite for the tab
     */
    public Control getControl()
    {
        return null;
    }
    
    public abstract void refresh(ManagedBean mbean);
    
    public void refresh(ManagedBean mbean, OperationData opData)
    {
        
    }
    
    /**
     * Sets focus on a widget
     */
    public void setFocus()
    {
        
    }
}
