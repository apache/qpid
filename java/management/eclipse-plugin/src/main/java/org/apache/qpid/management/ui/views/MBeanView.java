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

import java.util.HashMap;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.exceptions.InfoRequiredException;
import org.apache.qpid.management.ui.jmx.JMXServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.AttributeData;
import org.apache.qpid.management.ui.model.OperationData;
import org.apache.qpid.management.ui.model.OperationDataModel;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.ViewPart;


public class MBeanView extends ViewPart
{
    public static final String ID = "org.apache.qpid.management.ui.mbeanView";
    
    private FormToolkit  _toolkit = null;
    private Form _form = null;
    private ManagedBean _mbean = null;
    private HashMap<String, TabFolder> tabFolderMap = new HashMap<String, TabFolder>();
    private ISelectionListener selectionListener = new SelectionListenerImpl();
   
    private static final String ATTRIBUTES_CONTROL = "AttributesTabControl";
    private static final String OPERATIONS_CONTROL = "OperationsTabControl";
    private static final String NOTIFICATIONS_CONTROL = "NotificationsTabControl";
    
    
    /*
     * Listener for the selection events in the navigation view
     */ 
    private class SelectionListenerImpl implements ISelectionListener
    {
        public void selectionChanged(IWorkbenchPart part, ISelection sel)
        {
            if (!(sel instanceof IStructuredSelection))
                return;

            IStructuredSelection ss = (IStructuredSelection) sel;
            TreeObject node = (TreeObject)ss.getFirstElement();
            showSelectedMBean(node);
            /*
            _mbean = null;
            setInvisible();
            
            if (node == null)
            {
                _form.setText("Qpid Management Console");
                return;
            }
            
            if (Constants.NOTIFICATION.equals(node.getType()))
            {
                _mbean = (ManagedBean)node.getParent().getManagedObject();                
            }
            else if (Constants.MBEAN.equals(node.getType()))
            {
                _mbean = (ManagedBean)node.getManagedObject();                
            }
            else
            {
                _form.setText("Qpid Management Console");
                return;
            }
            
            setFocus();
            try
            {                
                MBeanUtility.getMBeanInfo(_mbean);     
            }
            catch(Exception ex)
            {
                MBeanUtility.handleException(_mbean, ex);
                return;
            }

            TabFolder tabFolder = tabFolderMap.get(_mbean.getType());
            if (tabFolder == null)
            {
                tabFolder = createTabFolder();
            }
            
            String text = _mbean.getType();
            if (_mbean.getName() != null && _mbean.getName().length() != 0)
            {
                text = text + ": " + _mbean.getName();
            }
            _form.setText(text);
            int tabIndex = 0;
            if (Constants.NOTIFICATION.equals(node.getType()))
            {
                tabIndex = tabFolder.getItemCount() -1;
            }
           
            TabItem tab = tabFolder.getItem(tabIndex);
            // refreshTab(tab);
            // If folder is being set as visible after tab refresh, then the tab 
            // doesn't have the focus.
                       
            tabFolder.setSelection(tabIndex);
            refreshTab(tab);
            setVisible(tabFolder); 
            _form.layout();
            
            // Set the focus on the first attribute in attributes table
            if (tab.getText().equals(Constants.ATTRIBUTES))
            {
                ((TabControl)tabFolder.getData(ATTRIBUTES_CONTROL)).setFocus();
            }*/
        }
    }

    public void showSelectedMBean(TreeObject node)
    {
        _mbean = null;
        setInvisible();
        
        if (node == null)
        {
            _form.setText("Qpid Management Console");
            return;
        }
        
        if (Constants.NOTIFICATION.equals(node.getType()))
        {
            _mbean = (ManagedBean)node.getParent().getManagedObject();                
        }
        else if (Constants.MBEAN.equals(node.getType()))
        {
            _mbean = (ManagedBean)node.getManagedObject();                
        }
        else
        {
            _form.setText("Qpid Management Console");
            return;
        }
        
        setFocus();
        try
        {                
            MBeanUtility.getMBeanInfo(_mbean);     
        }
        catch(Exception ex)
        {
            MBeanUtility.handleException(_mbean, ex);
            return;
        }

        TabFolder tabFolder = tabFolderMap.get(_mbean.getType());
        if (tabFolder == null)
        {
            tabFolder = createTabFolder();
        }
        
        String text = _mbean.getType();
        if (_mbean.getName() != null && _mbean.getName().length() != 0)
        {
            text = text + ": " + _mbean.getName();
        }
        _form.setText(text);
        int tabIndex = 0;
        if (Constants.NOTIFICATION.equals(node.getType()))
        {
            tabIndex = tabFolder.getItemCount() -1;
        }
       
        TabItem tab = tabFolder.getItem(tabIndex);
        // refreshTab(tab);
        // If folder is being set as visible after tab refresh, then the tab 
        // doesn't have the focus.
                   
        tabFolder.setSelection(tabIndex);
        refreshTab(tab);
        setVisible(tabFolder); 
        _form.layout();
    }
    
    public void createPartControl(Composite parent)
    {
        // Create the Form
        _toolkit = new FormToolkit(parent.getDisplay());
        _form = _toolkit.createForm(parent);
        _form.getBody().setLayout(new FormLayout());
        _form.setText(Constants.APPLICATION_NAME);
        
        // Add selection listener for selection events in the Navigation view
        getSite().getPage().addSelectionListener(NavigationView.ID, selectionListener); 
    }
    
    public void refreshMBeanView()
    {
        if (_mbean == null)
            return;
        
        TabFolder tabFolder = tabFolderMap.get(_mbean.getType());
        if (tabFolder == null)
            return;
        
        int index = tabFolder.getSelectionIndex();
        TabItem tab = tabFolder.getItem(index);
        if (tab == null)
            return;
                
        refreshTab(tab);
        _form.layout();
    }
    
    private TabFolder createTabFolder()
    {
        TabFolder tabFolder = new TabFolder(_form.getBody(), SWT.NONE);
        FormData layoutData = new FormData();
        layoutData.left = new FormAttachment(0);
        layoutData.top = new FormAttachment(0);
        layoutData.right = new FormAttachment(100);
        layoutData.bottom = new FormAttachment(100);
        tabFolder.setLayoutData(layoutData);
        tabFolder.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
        tabFolder.setVisible(false);
        
        createAttributesTab(tabFolder);
        createOperationTabs(tabFolder);
        createNotificationsTab(tabFolder);
        
        tabFolder.addListener(SWT.Selection, new Listener()
        {
            public void handleEvent(Event evt)
            {
                TabItem tab = (TabItem)evt.item;        
                refreshTab(tab);
            }
        });
        
        tabFolderMap.put(_mbean.getType(), tabFolder);               
        return tabFolder;
    }
    
    private void refreshTab(TabItem tab)
    {
        // We can avoid refreshing the attributes tab because it's control
        // already contains the required values. But it is added for now and 
        // will remove if there is any perfornce or any such issue.
        // The operations control should be refreshed because there is only one
        // controller for all operations tab.
        // The Notifications control needs to refresh with latest set of notifications
        
        if (tab == null)
            return;
        
        TabFolder tabFolder = tab.getParent();
        if (tab.getData() != null && (tab.getData() instanceof OperationData))
        {           
            // Refresh selected operation tab
            TabControl control = (TabControl)tabFolder.getData(OPERATIONS_CONTROL);
            if (control == null)
                return;
            
            control.refresh(_mbean, (OperationData)tab.getData());
        }
        else if (tab.getText().equals(Constants.NOTIFICATION))
        {
            TabControl control = (TabControl)tabFolder.getData(NOTIFICATIONS_CONTROL);
            if (control == null)
                return;
            
            control.refresh(_mbean);
        }        
        else if (tab.getText().equals(Constants.ATTRIBUTES))
        {
            TabControl control = (TabControl)tabFolder.getData(ATTRIBUTES_CONTROL);
            if (control == null)
                return;
            
            control.refresh(_mbean);
        }
        
    }
    
    public void setFocus()
    {   
        //_form.setFocus();
    }

    public void dispose()
    {
        _toolkit.dispose();
        super.dispose();
    }
    
    private void createAttributesTab(TabFolder tabFolder)
    {
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(_mbean);
        if (serverRegistry.getAttributeModel(_mbean).getCount() == 0)
        {
            return;
        }
        
        TabItem tab = new TabItem(tabFolder, SWT.NONE);
        tab.setText(Constants.ATTRIBUTES);
        AttributesTabControl control = new AttributesTabControl(tabFolder);
        tab.setControl(control.getControl());
        tabFolder.setData(ATTRIBUTES_CONTROL, control);      
    }
    
    private void createOperationTabs(TabFolder tabFolder)
    {
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(_mbean);        
        int operationsCount = serverRegistry.getOperationModel(_mbean).getCount();
        if (operationsCount == 0)
        {
            return;
        }
        
        OperationTabControl control = new OperationTabControl(tabFolder);
        tabFolder.setData(OPERATIONS_CONTROL, control);
        
        OperationDataModel operationModel = serverRegistry.getOperationModel(_mbean);
        for (OperationData operationData : operationModel.getOperations())
        {
            TabItem operationTab = new TabItem(tabFolder, SWT.NONE);
            operationTab.setText(ViewUtility.getDisplayText(operationData.getName()));
            operationTab.setData(operationData);
            operationTab.setControl(control.getControl());
        }
    }
    
    private void createNotificationsTab(TabFolder tabFolder)
    {
        NotificationsTabControl controller = new NotificationsTabControl(tabFolder);
        tabFolder.setData(NOTIFICATIONS_CONTROL, controller);
        
        TabItem tab = new TabItem(tabFolder, SWT.NONE);
        tab.setText(Constants.NOTIFICATION);
        tab.setControl(controller.getControl());
    }
    
    /**
     * For the EditAttribtue Action. Invoking this from action is same as clicking
     * "EditAttribute" button from Attribute tab.
     */
    public void editAttribute() throws Exception
    {
       if (_mbean == null)
           throw new InfoRequiredException("Please select the managed object and then attribute to be edited");
       
       String name = (_mbean.getName() != null) ? _mbean.getName() : _mbean.getType();
       JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(_mbean);
       if (serverRegistry.getAttributeModel(_mbean).getCount() == 0)
       {
           throw new InfoRequiredException("There are no attributes to be edited for " + name);
       }
       
       TabFolder tabFolder = tabFolderMap.get(_mbean.getType());
       int index = tabFolder.getSelectionIndex();
       if (index != 0)
       {
           tabFolder.setSelection(0);
           throw new InfoRequiredException("Please select the attribute to be edited");
       }
       
       AttributesTabControl tabControl = (AttributesTabControl)tabFolder.getData(ATTRIBUTES_CONTROL);
       AttributeData attribute = tabControl.getSelectionAttribute();
       if (attribute == null)
           throw new InfoRequiredException("Please select the attribute to be edited");
       
       tabControl.createDetailsPopup(attribute);
    }
    
    
    /**
     * hides other folders and makes the given one visible.
     * @param tabFolder
     */
    private void setVisible(TabFolder tabFolder)
    {
        for (TabFolder folder : tabFolderMap.values())
        {
            if (folder == tabFolder)
                folder.setVisible(true);
            else
                folder.setVisible(false);
        }
    }
    
    private void setInvisible()
    {
        for (TabFolder folder : tabFolderMap.values())
        {
            folder.setVisible(false);
        }
    }
    
}
