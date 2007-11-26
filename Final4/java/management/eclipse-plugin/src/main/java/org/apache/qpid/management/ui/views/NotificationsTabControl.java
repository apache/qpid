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

import static org.apache.qpid.management.ui.Constants.BUTTON_CLEAR;
import static org.apache.qpid.management.ui.Constants.BUTTON_REFRESH;
import static org.apache.qpid.management.ui.Constants.DESCRIPTION;
import static org.apache.qpid.management.ui.Constants.FONT_BOLD;
import static org.apache.qpid.management.ui.Constants.FONT_BUTTON;
import static org.apache.qpid.management.ui.Constants.FONT_ITALIC;
import static org.apache.qpid.management.ui.Constants.SUBSCRIBE_BUTTON;
import static org.apache.qpid.management.ui.Constants.UNSUBSCRIBE_BUTTON;

import java.util.List;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.NotificationInfoModel;
import org.apache.qpid.management.ui.model.NotificationObject;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;

/**
 * Creates control composite for Notifications tab
 * @author Bhupendra Bhardwaj
 */
public class NotificationsTabControl extends VHNotificationsTabControl
{    
    private static final String SELECT_NOTIFICATIONNAME = "Select Notification";
    private static final String SELECT_NOTIFICATIONTYPE = "Select Type";
    private SelectionListener selectionListener;
    private SelectionListener comboListener;    
    
    private Combo notificationNameCombo = null;
    private Combo typesCombo = null;
    private Label descriptionLabel = null;
    private Button _subscribeButton   = null;
    private Button _unsubscribeButton = null;
    
    public NotificationsTabControl(TabFolder tabFolder)
    {
        super(tabFolder);
    }
    
    protected void createWidgets()
    {       
        selectionListener = new SelectionListenerImpl();
        comboListener = new ComboSelectionListener();
        createNotificationInfoComposite();
        //addFilterComposite();
        addButtons();  
        createTableViewer();
    }
    
    /**
     * Creates composite and populates for displaying Notification Information (name, type, description)
     * and creates buttons for subscribing or unsubscribing for notifications
     */
    private void createNotificationInfoComposite()
    {
        Composite composite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        composite.setLayout(new FormLayout());
        
        Label label = _toolkit.createLabel(composite, "Select the notification to subscribe or unsubscribe");
        label.setFont(ApplicationRegistry.getFont(FONT_BOLD));
        FormData formData = new FormData();
        formData.top = new FormAttachment(0, 10);
        formData.left = new FormAttachment(0, 10);
        label.setLayoutData(formData);
        
        notificationNameCombo = new Combo(composite, SWT.READ_ONLY | SWT.DROP_DOWN);
        formData = new FormData();
        formData.top = new FormAttachment(label, 10);
        formData.left = new FormAttachment(0, 10);
        formData.right = new FormAttachment(40);
        notificationNameCombo.setLayoutData(formData);
        notificationNameCombo.addSelectionListener(comboListener);
        
        typesCombo = new Combo(composite, SWT.READ_ONLY | SWT.DROP_DOWN);
        formData = new FormData();
        formData.top = new FormAttachment(label, 10);
        formData.left = new FormAttachment(notificationNameCombo, 5);
        formData.right = new FormAttachment(65);
        typesCombo.setLayoutData(formData);
        typesCombo.addSelectionListener(comboListener);
        
        _subscribeButton = new Button(composite, SWT.PUSH | SWT.CENTER);
        _subscribeButton.setFont(ApplicationRegistry.getFont(FONT_BUTTON));
        _subscribeButton.setText(SUBSCRIBE_BUTTON);
        formData = new FormData();
        formData.top = new FormAttachment(label, 10);
        formData.left = new FormAttachment(65, 10);
        formData.width = 80;
        _subscribeButton.setLayoutData(formData);
        _subscribeButton.addSelectionListener(selectionListener);
        
        _unsubscribeButton = new Button(composite, SWT.PUSH | SWT.CENTER);
        _unsubscribeButton.setFont(ApplicationRegistry.getFont(FONT_BUTTON));
        _unsubscribeButton.setText(UNSUBSCRIBE_BUTTON);
        formData = new FormData();
        formData.top = new FormAttachment(label, 10);
        formData.left = new FormAttachment(_subscribeButton, 10);
        formData.width = 80;
        _unsubscribeButton.setLayoutData(formData);
        _unsubscribeButton.addSelectionListener(selectionListener);
        
        Label fixedLabel = _toolkit.createLabel(composite, "");
        formData = new FormData();
        formData.top = new FormAttachment(notificationNameCombo, 5);
        formData.left = new FormAttachment(0, 10);
        fixedLabel.setLayoutData(formData);
        fixedLabel.setText(DESCRIPTION + " : ");
        fixedLabel.setFont(ApplicationRegistry.getFont(FONT_BOLD));
        
        descriptionLabel = _toolkit.createLabel(composite, "");
        formData = new FormData();
        formData.top = new FormAttachment(notificationNameCombo, 5);
        formData.left = new FormAttachment(fixedLabel, 10);
        formData.right = new FormAttachment(100);
        descriptionLabel.setLayoutData(formData);
        descriptionLabel.setText("      ");
        descriptionLabel.setFont(ApplicationRegistry.getFont(FONT_ITALIC));
    }
    
    /**
     * Creates clear buttin and refresh button
     */
    protected void addButtons()
    {    
        Composite composite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        composite.setLayout(new GridLayout(2, true));
        
        // Add Clear Button
        _clearButton = _toolkit.createButton(composite, BUTTON_CLEAR, SWT.PUSH | SWT.CENTER);
        _clearButton.setFont(ApplicationRegistry.getFont(FONT_BUTTON));
        GridData gridData = new GridData(SWT.LEAD, SWT.TOP, true, false);
        gridData.widthHint = 80;
        _clearButton.setLayoutData(gridData);
        _clearButton.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {    
                    if (_mbean == null)
                        return;
                    
                    IStructuredSelection ss = (IStructuredSelection)_tableViewer.getSelection();
                    ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(_mbean);
                    serverRegistry.clearNotifications(_mbean, ss.toList());
                    refresh();
                }
            });
        
        // Add Refresh Button
        _refreshButton = _toolkit.createButton(composite, BUTTON_REFRESH, SWT.PUSH | SWT.CENTER);
        _refreshButton.setFont(ApplicationRegistry.getFont(FONT_BUTTON));
        gridData = new GridData(SWT.TRAIL, SWT.TOP, true, false);
        gridData.widthHint = 80;
        _refreshButton.setLayoutData(gridData);
        _refreshButton.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {    
                    if (_mbean == null)
                        return;
                    
                    refresh();
                }
            });
    }
  
    @Override
    public void refresh(ManagedBean mbean)
    {
        _mbean = mbean;
        _notifications = null;
        _table.deselectAll();
        _tableViewer.getTable().clearAll();
        
        if (_mbean == null)
        {            
            _tableViewer.getTable().clearAll();
            _subscribeButton.setEnabled(false);
            _unsubscribeButton.setEnabled(false);
            return;
        }        
        
        if (!doesMBeanSendsNotification())
        {
            Control[] children = _form.getBody().getChildren();        
            for (int i = 0; i < children.length; i++)
            {
                children[i].setVisible(false);
            }
            
            String name = (_mbean.getName() != null) ? _mbean.getName() : _mbean.getType();
            _form.setText(name + " does not send any notification");
            return;
        }
        
        Control[] children = _form.getBody().getChildren();        
        for (int i = 0; i < children.length; i++)
        {
            children[i].setVisible(true);
        }
        
        populateNotificationInfo();        
        workerRunning = true;
        _form.layout(true);   
        _form.getBody().layout(true, true);
    }
    
    public void refresh()
    {
        _notifications = null;
        _table.deselectAll();
        _tableViewer.getTable().clearAll();
    }
    
    /**
     * Fills the notification information widgets for selected mbean
     */
    private void populateNotificationInfo()
    {
        notificationNameCombo.removeAll();
        NotificationInfoModel[] items = MBeanUtility.getNotificationInfo(_mbean);
        if (items.length > 1)
        {
            notificationNameCombo.add(SELECT_NOTIFICATIONNAME);
        }
        
        for (int i = 0; i < items.length; i++)
        {
            notificationNameCombo.add(items[i].getName());
            notificationNameCombo.setData(items[i].getName(), items[i]);
        } 
        notificationNameCombo.select(0);
        
        typesCombo.removeAll();
        typesCombo.add("Select Type", 0);
        typesCombo.select(0);
        typesCombo.setEnabled(false);
        
        populateNotificationType(notificationNameCombo.getItem(0));
        checkForEnablingButtons();
    }
    
    /**
     * Checks and the enabing/disabling of buttons
     */
    private void checkForEnablingButtons()
    {
        int nameIndex = notificationNameCombo.getSelectionIndex();
        int itemCount = notificationNameCombo.getItems().length;
        if ((itemCount > 1) && (nameIndex == 0))
        {
            _subscribeButton.setEnabled(false);
            _unsubscribeButton.setEnabled(false);
            descriptionLabel.setText("");
            return;
        }
        
        int typeIndex = typesCombo.getSelectionIndex();
        itemCount = typesCombo.getItems().length;
        if ((itemCount > 1) && (typeIndex == 0))
        {
            _subscribeButton.setEnabled(false);
            _unsubscribeButton.setEnabled(false);
            return;
        }
        
        String type = typesCombo.getItem(typeIndex);
        String name = notificationNameCombo.getItem(nameIndex);
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(_mbean);
        
        if (serverRegistry.hasSubscribedForNotifications(_mbean, name, type))
        {
            _subscribeButton.setEnabled(false);
            _unsubscribeButton.setEnabled(true);
        }
        else
        {
            _subscribeButton.setEnabled(true);
            _unsubscribeButton.setEnabled(false);
        }
    }
    
    private boolean doesMBeanSendsNotification()
    {
        NotificationInfoModel[] items = MBeanUtility.getNotificationInfo(_mbean);
        if (items == null || items.length == 0)
            return false;
        else
            return true;
    }
    
    /**
     * Selection listener for subscribing or unsubscribing the notifications
     */
    private class SelectionListenerImpl extends SelectionAdapter
    {
        public void widgetSelected(SelectionEvent e)
        {
            if (_mbean == null)
                return;
            
            Button source = (Button)e.getSource();
            String type = typesCombo.getItem(typesCombo.getSelectionIndex());
            String name = notificationNameCombo.getItem(notificationNameCombo.getSelectionIndex());
            if (source == _unsubscribeButton)
            {
                try
                {
                    MBeanUtility.removeNotificationListener(_mbean, name, type);
                }
                catch(Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
            else if (source == _subscribeButton)
            {
                try
                {
                    MBeanUtility.createNotificationlistener(_mbean, name, type);
                }
                catch(Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
            checkForEnablingButtons();
        }
    }
    
    /**
     * Selection listener class for the Notification Name. The notification type and description will be 
     * displayed accordingly
     */
    private class ComboSelectionListener extends SelectionAdapter
    {
        public void widgetSelected(SelectionEvent e)
        {
            if (_mbean == null)
                return;
            
            Combo combo = (Combo)e.getSource();
            if (combo == notificationNameCombo)
            {
                String selectedItem = combo.getItem(combo.getSelectionIndex());                
                populateNotificationType(selectedItem);
            }
            checkForEnablingButtons();
        }
    }
    
    private void populateNotificationType(String notificationName)
    {
        NotificationInfoModel data = (NotificationInfoModel)notificationNameCombo.getData(notificationName);
        if (data == null)
        {
            descriptionLabel.setText("");
            typesCombo.select(0);
            typesCombo.setEnabled(false);
            return;
        }
        descriptionLabel.setText(data.getDescription());
        typesCombo.removeAll();       
        typesCombo.setItems(data.getTypes());
        if (typesCombo.getItemCount() > 1)
        {
            typesCombo.add(SELECT_NOTIFICATIONTYPE, 0);
        }
        typesCombo.select(0);
        typesCombo.setEnabled(true);
    }
    
    /**
     * Updates the table with new notifications received from mbean server for the selected mbean
     */
    protected void updateTableViewer()
    {
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(_mbean);        
        List<NotificationObject> newList = serverRegistry.getNotifications(_mbean);
        if (newList == null)
            return;
        
        _notifications = newList;
        _tableViewer.setInput(_notifications);
        _tableViewer.refresh();
    }
}
