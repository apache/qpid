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

import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.NotificationInfoModel;
import org.apache.qpid.management.ui.model.NotificationObject;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;

/**
 * Creates control composite for Notifications tab
 * @author Bhupendra Bhardwaj
 */
public class NotificationsTabControl extends TabControl
{
    private FormToolkit  _toolkit;
    private Form _form;
    private Table table = null;
    private TableViewer _tableViewer  = null;
    
    private IStructuredContentProvider contentProvider = new ContentProviderImpl();
    private SelectionListener selectionListener = new SelectionListenerImpl();
    private SelectionListener comboListener = new ComboSelectionListener();
    
    private Thread worker = null;
    
    private List<NotificationObject> _notifications = null;
    private static final String COLUMN_SEQ  = "Sequence No";
    private static final String COLUMN_TIME = "TimeStamp";
    private static final String COLUMN_TYPE  = "Type";
    private static final String COLUMN_MSG  = "Notification Message";
    private static final String[] _tableTitles = new String [] {
            COLUMN_SEQ,
            COLUMN_TIME,
            COLUMN_TYPE,
            COLUMN_MSG
         };
    
    private Combo notificationNameCombo = null;
    private Combo typesCombo = null;
    private Label descriptionLabel = null;
    private Button _subscribeButton   = null;
    private Button _unsubscribeButton = null;
    private Button _clearButton       = null;
    private Button _refreshButton       = null;
    
    
    public NotificationsTabControl(TabFolder tabFolder)
    {
        super(tabFolder);
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createForm(_tabFolder);
        GridLayout gridLayout = new GridLayout();      
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;       
        _form.getBody().setLayout(gridLayout);
        
        createWidgets();
        worker = new Thread(new Worker()); 
        worker.start();
    }
    
    private void createWidgets()
    {       
        createNotificationInfoComposite();
        //addFilterComposite();
        addButtons();  
        createTableViewer();
    }
    
    /**
     * @see TabControl#getControl()
     */
    public Control getControl()
    {
        return _form;
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
        label.setFont(ApplicationRegistry.getFont(Constants.FONT_BOLD));
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
        _subscribeButton.setFont(ApplicationRegistry.getFont(Constants.FONT_BUTTON));
        _subscribeButton.setText(Constants.SUBSCRIBE_BUTTON);
        formData = new FormData();
        formData.top = new FormAttachment(label, 10);
        formData.left = new FormAttachment(65, 10);
        formData.width = 80;
        _subscribeButton.setLayoutData(formData);
        _subscribeButton.addSelectionListener(selectionListener);
        
        _unsubscribeButton = new Button(composite, SWT.PUSH | SWT.CENTER);
        _unsubscribeButton.setFont(ApplicationRegistry.getFont(Constants.FONT_BUTTON));
        _unsubscribeButton.setText(Constants.UNSUBSCRIBE_BUTTON);
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
        fixedLabel.setText(Constants.DESCRIPTION + " : ");
        fixedLabel.setFont(ApplicationRegistry.getFont(Constants.FONT_BOLD));
        
        descriptionLabel = _toolkit.createLabel(composite, "");
        formData = new FormData();
        formData.top = new FormAttachment(notificationNameCombo, 5);
        formData.left = new FormAttachment(fixedLabel, 10);
        formData.right = new FormAttachment(100);
        descriptionLabel.setLayoutData(formData);
        descriptionLabel.setText("      ");
        descriptionLabel.setFont(ApplicationRegistry.getFont(Constants.FONT_ITALIC));
    }
    
    /**
     * Creates clear buttin and refresh button
     */
    private void addButtons()
    {    
        Composite composite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        composite.setLayout(new GridLayout(2, true));
        
        // Add Clear Button
        _clearButton = _toolkit.createButton(composite, Constants.BUTTON_CLEAR, SWT.PUSH | SWT.CENTER);
        _clearButton.setFont(ApplicationRegistry.getFont(Constants.FONT_BUTTON));
        GridData gridData = new GridData(SWT.LEAD, SWT.TOP, true, false);
        gridData.widthHint = 80;
        _clearButton.setLayoutData(gridData);
        _clearButton.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {    
                    if (_mbean == null)
                        return;
                    
                    ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(_mbean);
                    serverRegistry.clearNotifications(_mbean);
                    refresh();
                }
            });
        
        // Add Refresh Button
        _refreshButton = _toolkit.createButton(composite, Constants.BUTTON_REFRESH, SWT.PUSH | SWT.CENTER);
        _refreshButton.setFont(ApplicationRegistry.getFont(Constants.FONT_BUTTON));
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
    
    /**
     * Creates table to display notifications
     */
    private void createTable()
    {
        table = _toolkit.createTable(_form.getBody(),  SWT.FULL_SELECTION);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        TableColumn column = new TableColumn(table, SWT.NONE);
        column.setText(_tableTitles[0]);
        column.pack();        //column.setWidth(200);

        column = new TableColumn(table, SWT.NONE);
        column.setText(_tableTitles[1]);
        column.setWidth(150);
        
        column = new TableColumn(table, SWT.NONE);
        column.setText(_tableTitles[2]);
        column.setWidth(100);
        
        column = new TableColumn(table, SWT.NONE);
        column.setText(_tableTitles[3]);
        column.setWidth(500);
        
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
    }
    
    /**
     * Creates JFace viewer for the notifications table
     */
    protected void createTableViewer()
    {
        createTable();
        _tableViewer = new TableViewer(table);
        //_tableViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        _tableViewer.setUseHashlookup(true);
        _tableViewer.setContentProvider(contentProvider);
        _tableViewer.setLabelProvider(new LabelProviderImpl());
        _tableViewer.setColumnProperties(_tableTitles);
        /*
        CellEditor[] cellEditors = new CellEditor[_tableTitles.length];
        TextCellEditor textEditor = new TextCellEditor(table);
        cellEditors[0] = textEditor;
        textEditor = new TextCellEditor(table);
        cellEditors[1] = textEditor;
        textEditor = new TextCellEditor(table);
        cellEditors[2] = textEditor;
        textEditor = new TextCellEditor(table);
        cellEditors[3] = textEditor;
        
        // Assign the cell editors to the viewer 
        _tableViewer.setCellEditors(cellEditors);
        _tableViewer.setCellModifier(new TableCellModifier());
        */
        
        addTableListeners();
        
        //_tableViewer.addSelectionChangedListener(new );
        
        //_notificationDetails = new Composite(_tabControl, SWT.BORDER);
        //_notificationDetails.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        //_tabControl.layout();
        //viewerComposite.layout();
    }
    
    /**
     * Adds listeners to the viewer for displaying notification details 
     */
    private void addTableListeners()
    {
        _tableViewer.addDoubleClickListener(new IDoubleClickListener()
            {
                Display display = null;
                Shell   shell = null;
                public void doubleClick(DoubleClickEvent event)
                {
                    display = Display.getCurrent();
                    shell = new Shell(display, SWT.BORDER | SWT.CLOSE | SWT.MIN |
                            SWT.MAX | SWT.RESIZE);
                    shell.setText("Notification");

                    int x = display.getBounds().width;
                    int y = display.getBounds().height;
                    shell.setBounds(x/4, y/4, x/2, y/3);
                    StructuredSelection selection = (StructuredSelection)event.getSelection();
                    createPopupContents((NotificationObject)selection.getFirstElement());
                    shell.open();
                    while (!shell.isDisposed()) {
                        if (!display.readAndDispatch()) {
                            display.sleep();
                        }
                    }
                    
                    //If you create it, you dispose it.
                    shell.dispose();
                }

                private void createPopupContents(NotificationObject obj)
                {                    
                    shell.setLayout(new GridLayout());
                    
                    Composite parent = new Composite(shell, SWT.NONE);
                    parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
                    GridLayout layout = new GridLayout(4, true);
                    parent.setLayout(layout);

                    Label key = new Label(parent, SWT.TRAIL);               
                    key.setText(COLUMN_SEQ);
                    GridData layoutData = new GridData(SWT.TRAIL, SWT.TOP, false, false,1,1);
                    key.setLayoutData(layoutData);
                    Text  value = new Text(parent, SWT.BEGINNING | SWT.BORDER |SWT.READ_ONLY);
                    value.setText(""+obj.getSequenceNo());
                    value.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false,3,1));

                    // Time row
                    key = new Label(parent, SWT.TRAIL);
                    key.setText(COLUMN_TIME);
                    key.setLayoutData(new GridData(SWT.TRAIL, SWT.TOP, true, false,1,1));
                    value = new Text(parent, SWT.BEGINNING | SWT.BORDER | SWT.READ_ONLY);
                    value.setText(""+obj.getTimeStamp());
                    value.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false,3,1));

                    key = new Label(parent, SWT.TRAIL);
                    key.setText(COLUMN_TYPE);
                    key.setLayoutData(new GridData(SWT.TRAIL, SWT.TOP, true, false,1,1));
                    value = new Text(parent, SWT.BEGINNING | SWT.BORDER | SWT.READ_ONLY);
                    value.setText(""+obj.getType());
                    value.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false,3,1));

                    key = new Label(parent, SWT.TRAIL);
                    key.setText(COLUMN_MSG);
                    key.setLayoutData(new GridData(SWT.TRAIL, SWT.TOP, true, false,1,1));
                    value = new Text(parent, SWT.MULTI | SWT.WRAP| SWT.BORDER | SWT.V_SCROLL | SWT.READ_ONLY);
                    value.setText(""+obj.getMessage());
                    GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
                    gridData.heightHint = 100;
                    value.setLayoutData(gridData);
                }
            });
    }
    
    @Override
    public void refresh(ManagedBean mbean)
    {
        _mbean = mbean;
        _notifications = null;
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
        _form.layout();       
    }
    
    private void refresh()
    {
        _notifications = null;
        _tableViewer.getTable().clearAll();
    }
    
    /**
     * Fills the notification information widgets for selected mbean
     */
    private void populateNotificationInfo()
    {
        notificationNameCombo.removeAll();
        NotificationInfoModel[] items = MBeanUtility.getNotificationInfo(_mbean);
        notificationNameCombo.add("Select Notification");
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
        
        checkForEnablingButtons();
    }
    
    /**
     * Checks and the enabing/disabling of buttons
     */
    private void checkForEnablingButtons()
    {
        int nameIndex = notificationNameCombo.getSelectionIndex();
        if (nameIndex == 0)
        {
            _subscribeButton.setEnabled(false);
            _unsubscribeButton.setEnabled(false);
            descriptionLabel.setText("");
            return;
        }
        
        int typeIndex = typesCombo.getSelectionIndex();
        if (typeIndex == 0)
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
                if (combo.getSelectionIndex() == 0)
                {
                    descriptionLabel.setText("");
                    typesCombo.select(0);
                    typesCombo.setEnabled(false);
                    return;
                }
                String index = combo.getItem(combo.getSelectionIndex());                
                NotificationInfoModel data = (NotificationInfoModel)combo.getData(index);
                descriptionLabel.setText(data.getDescription());
                typesCombo.removeAll();       
                typesCombo.setItems(data.getTypes());
                typesCombo.add("Select Type", 0);
                typesCombo.select(0);
                typesCombo.setEnabled(true);
            }
            checkForEnablingButtons();
        }
    }
    
    /**
     * Content provider class for the table viewer
     */
    private class ContentProviderImpl implements IStructuredContentProvider, INotificationViewer
    {
        public void inputChanged(Viewer v, Object oldInput, Object newInput)
        {
            
        }
        public void dispose()
        {
            
        }
        public Object[] getElements(Object parent) 
        {
            return _notifications.toArray(new NotificationObject[0]);
        }
        public void addNotification(NotificationObject notification)
        {
            _tableViewer.add(notification);
        }
        
        public void addNotification(List<NotificationObject> notificationList)
        {
            _tableViewer.add(notificationList.toArray(new NotificationObject[0]));
        }
    }
    
    /**
     * Label provider for the table viewer
     */
    private class LabelProviderImpl implements ITableLabelProvider
    {
        List<ILabelProviderListener> listeners = new ArrayList<ILabelProviderListener>();       
        public void addListener(ILabelProviderListener listener)
        {
            listeners.add(listener);
        }
        
        public void dispose(){
            
        }
        
        public Image getColumnImage(Object element, int columnIndex)
        {
            return null;
        }
        
        public String getColumnText(Object element, int columnIndex)
        {
            String result = null;
            NotificationObject t = (NotificationObject)element;
            switch(columnIndex)
            {
            case 0 : 
                result = String.valueOf(t.getSequenceNo());
                break;
            case 1 :
                result = String.valueOf(t.getTimeStamp());
                break;
            case 2 : 
                result = t.getType();
                break;
            case 3 : 
                result = t.getMessage();
                break;
            default : 
                result = "";
            }
            
            return result;
        }
        
        public boolean isLabelProperty(Object element, String property)
        {
            return false;
        }
        
        public void removeListener(ILabelProviderListener listener)
        {
            listeners.remove(listener);
        }
    } // end of LabelProviderImpl
    
    private boolean workerRunning = false;
    private void setWorkerRunning(boolean running)
    {
        workerRunning = running;
    }
    
    /**
     * Worker class which keeps looking if there are new notifications coming from server for the selected mbean
     */
    private class Worker implements Runnable
    {
        public void run()
        {
            Display display = _tabFolder.getDisplay();
            while(true)
            {
                if (!workerRunning || _mbean == null || display == null)
                {
                    sleep();
                    continue;
                }
                
                display.syncExec(new Runnable()
                {
                    public void run()
                    {
                        setWorkerRunning(_form.isVisible());
                        if (!workerRunning) return;
                        
                        updateTableViewer();
                    }
                });     
            
                sleep();
            }
        }
        
        private void sleep()
        {
            try
            {
                Thread.sleep(2000);
            }
            catch(Exception ex)
            {

            }  
        }
    }
    
    /**
     * Updates the table with new notifications received from mbean server for the selected mbean
     */
    private void updateTableViewer()
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
