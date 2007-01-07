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
import org.apache.qpid.management.ui.ManagedServer;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.exceptions.InfoRequiredException;
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
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.ViewPart;

/**
 * MBean View create appropriate view based on the user selection on the Navigation View.
 * Create TabFolder for all MBeans and displays the attribtues and method tabs.
 * @author Bhupendra Bhardwaj
 *
 */
public class MBeanView extends ViewPart
{
    public static final String ID = "org.apache.qpid.management.ui.mbeanView";
    
    private FormToolkit  _toolkit = null;
    private Form _form = null;
    private static ManagedServer _server = null;
    private TreeObject _selectedNode = null;
    private ManagedBean _mbean = null;
    // This map contains a TabFolder for each kind of MBean. TabFolder is mapped with mbeantype(eg Connection, Queue etc)
    private HashMap<String, TabFolder> tabFolderMap = new HashMap<String, TabFolder>();
    private ISelectionListener selectionListener = new SelectionListenerImpl();
   
    private static final String ATTRIBUTES_CONTROL = "AttributesTabControl";
    private static final String OPERATIONS_CONTROL = "OperationsTabControl";
    private static final String NOTIFICATIONS_CONTROL = "NotificationsTabControl";
    
    // TabFolder to list all the mbeans for a given mbeantype(eg Connection, Queue, Exchange)
    private TabFolder typeTabFolder = null;
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
            _selectedNode = (TreeObject)ss.getFirstElement();
            
            
            // mbean should be set to null. A selection done on the navigation view can be either an mbean or
            // an mbeantype. For mbeantype selection(eg Connection, Queue, Exchange) _mbean will remain null.
            _mbean = null;
            setInvisible();
            _form.setText(Constants.APPLICATION_NAME);
            
            // If a selected node(mbean) gets unregistered from mbena server, mbenaview should should 
            // make the tabfolber for that mbean invisible
            if (_selectedNode == null)            
                return;
            
            setServer();
            try
            {
                if (Constants.TYPE.equals(_selectedNode.getType()))
                {
                    refreshTypeTabFolder(_selectedNode.getName());
                }
                else if (Constants.DOMAIN.equals(_selectedNode.getType()))
                {
                    refreshTypeTabFolder(typeTabFolder.getItem(0));
                }
                else
                {
                    showSelectedMBean();
                }
                _form.layout();
            }
            catch(Exception ex)
            {
                MBeanUtility.handleException(_mbean, ex);
            }
        }
    }

    /**
     * Sets the managedServer based on the selection in the navigation view
     * At any given time MBeanView will be displaying information for an mbean of mbeantype
     * for a specifiv managed server. This server information will be used by the tab controllers
     * to get server registry.
     */
    private void setServer()
    {
        if (Constants.SERVER.equals(_selectedNode.getType()))
        {
            _server = (ManagedServer)_selectedNode.getManagedObject();
        }
        else
        {
            TreeObject parent = _selectedNode.getParent();
            while (parent != null && !parent.getType().equals(Constants.SERVER))
            {
                parent = parent.getParent();
            }
            
            if (parent != null && parent.getType().equals(Constants.SERVER))
                _server = (ManagedServer)parent.getManagedObject();
        }
    }
    
    public static ManagedServer getServer()
    {
        return _server;
    }
    
    private void showSelectedMBean() throws Exception
    {     
        if (Constants.NOTIFICATION.equals(_selectedNode.getType()))
        {
            _mbean = (ManagedBean)_selectedNode.getParent().getManagedObject();                
        }
        else if (Constants.MBEAN.equals(_selectedNode.getType()))
        {
            _mbean = (ManagedBean)_selectedNode.getManagedObject();                
        }
        else
        {
            return;
        }
        
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
        if (Constants.NOTIFICATION.equals(_selectedNode.getType()))
        {
            tabIndex = tabFolder.getItemCount() -1;
        }
       
        TabItem tab = tabFolder.getItem(tabIndex);
        // If folder is being set as visible after tab refresh, then the tab 
        // doesn't have the focus.                  
        tabFolder.setSelection(tabIndex);
        refreshTab(tab);
        setVisible(tabFolder); 
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
        
        // Add mbeantype TabFolder. This will list all the mbeans under a mbeantype (eg Queue, Exchange).
        // Using this list mbeans will be added in the navigation view
        createTypeTabFolder();
    }
    
    public void refreshMBeanView() throws Exception
    {
        int tabIndex = 0;
        TabItem tab = null;
        if (_mbean == null)
        {
            tabIndex = typeTabFolder.getSelectionIndex();
            if (tabIndex == -1)
                return;

            tab = typeTabFolder.getItem(tabIndex);
            refreshTypeTabFolder(tab);
        }
        else
        {
            TabFolder tabFolder = tabFolderMap.get(_mbean.getType());
            if (tabFolder == null)
                return;

            tabIndex = tabFolder.getSelectionIndex();
            tab = tabFolder.getItem(tabIndex);
            if (tab == null)
                return;

            refreshTab(tab);
        }
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
        // will remove if there is any performance issue or any other issue.
        // The operations control should be refreshed because there is only one
        // controller for all operations tab.
        // The Notifications control needs to refresh with latest set of notifications
        
        if (tab == null)
            return;
        
        TabFolder tabFolder = tab.getParent();
        // If an operation tab is selected, the operation data is attached with the tab
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
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(_mbean);
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
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(_mbean);        
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
       ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(_mbean);
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
     * Creates TabFolder and tabs for each mbeantype (eg Connection, Queue, Exchange)
     */
    private void createTypeTabFolder()
    {
        typeTabFolder = new TabFolder(_form.getBody(), SWT.NONE);
        FormData layoutData = new FormData();
        layoutData.left = new FormAttachment(0);
        layoutData.top = new FormAttachment(0);
        layoutData.right = new FormAttachment(100);
        layoutData.bottom = new FormAttachment(100);
        typeTabFolder.setLayoutData(layoutData);
        typeTabFolder.setVisible(false);
        
        MBeanTypeTabControl controller = new MBeanTypeTabControl(typeTabFolder);
        typeTabFolder.setData("CONTROLLER", controller);
        
        TabItem tab = new TabItem(typeTabFolder, SWT.NONE);
        tab.setText(Constants.CONNECTION);       
        tab.setControl(controller.getControl());
        
        tab = new TabItem(typeTabFolder, SWT.NONE);
        tab.setText(Constants.EXCHANGE);       
        tab.setControl(controller.getControl());
        
        tab = new TabItem(typeTabFolder, SWT.NONE);
        tab.setText(Constants.QUEUE);       
        tab.setControl(controller.getControl());
        
        typeTabFolder.addListener(SWT.Selection, new Listener()
        {
            public void handleEvent(Event evt)
            {
                TabItem tab = (TabItem)evt.item;     
                try
                {
                    refreshTypeTabFolder(tab);
                }
                catch (Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
        });
    }
    
    /**
     * Refreshes the Selected mbeantype tab. The control lists all the available mbeans
     * for an mbeantype(eg Queue, Exchange etc)
     * @param tab
     * @throws Exception
     */
    private void refreshTypeTabFolder(TabItem tab) throws Exception
    {
        if (tab == null)
        {
            return;
        }
        typeTabFolder.setSelection(tab);
        MBeanTypeTabControl controller = (MBeanTypeTabControl)typeTabFolder.getData("CONTROLLER");
        controller.refresh(tab.getText());
        typeTabFolder.setVisible(true);
    }
    
    private void refreshTypeTabFolder(String type) throws Exception
    {
        if (Constants.CONNECTION.equals(type))
        {
            refreshTypeTabFolder(typeTabFolder.getItem(0));
        }
        else if (Constants.EXCHANGE.equals(type))
        {
            refreshTypeTabFolder(typeTabFolder.getItem(1));
        }
        else if (Constants.QUEUE.equals(type))
        {
            refreshTypeTabFolder(typeTabFolder.getItem(2));
        }
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
        
        if (typeTabFolder != null)
        {
            typeTabFolder.setVisible(false);
        }
    }
    
}
