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
package org.apache.qpid.management.ui.views.users;

import java.util.Collection;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.management.ui.jmx.JMXManagedObject;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.views.TabControl;
import org.apache.qpid.management.ui.views.ViewUtility;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;


/**
 * Control class for the UserManagement mbean.
 */
public class UserManagementTabControl extends TabControl
{
    private FormToolkit _toolkit;
    private Form        _form;
    private Table _table = null;
    private TableViewer _tableViewer = null;

    private TabularDataSupport _userDetails = null;
    private UserManagement _ummb;
    
    static final String USERNAME = UserManagement.COMPOSITE_ITEM_NAMES[0];
    static final String RIGHTS_READ_ONLY = UserManagement.COMPOSITE_ITEM_NAMES[1];
    static final String RIGHTS_READ_WRITE = UserManagement.COMPOSITE_ITEM_NAMES[2];
    static final String RIGHTS_ADMIN = UserManagement.COMPOSITE_ITEM_NAMES[3];
    
    public UserManagementTabControl(TabFolder tabFolder, JMXManagedObject mbean, MBeanServerConnection mbsc)
    {
        super(tabFolder);
        _mbean = mbean;
        _ummb = (UserManagement)
                MBeanServerInvocationHandler.newProxyInstance(mbsc, mbean.getObjectName(),
                                                            UserManagement.class, false);
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createForm(_tabFolder);
        _form.getBody().setLayout(new GridLayout());
        createWidgets();
    }

    /**
     * @see TabControl#getControl()
     */
    public Control getControl()
    {
        return _form;
    }
    
    /**
     * @see TabControl#setFocus()
     */
    public void setFocus()
    {
        _table.setFocus();
    }
    
    @Override
    public void refresh(ManagedBean mbean)
    {
        _mbean = mbean;
        if (_mbean == null)
        {
            _tableViewer.setInput(null);
            return;
        }
        
        try
        {
            _userDetails = (TabularDataSupport) _ummb.viewUsers();
        }
        catch(Exception e)
        {
            MBeanUtility.handleException(_mbean, e);
            _userDetails = null;
        }

        _form.setVisible(false);
        _tableViewer.setInput(_userDetails);
        _form.setVisible(true);
        layout();
    }
    
    public void layout()
    {
        _form.layout(true);
        _form.getBody().layout(true, true);
    }
    
    private void createWidgets()
    {
        Composite paramsComposite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        paramsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        paramsComposite.setLayout(new GridLayout());
        
        Group viewUsersGroup = new Group(paramsComposite, SWT.SHADOW_NONE);
        viewUsersGroup.setBackground(paramsComposite.getBackground());
        viewUsersGroup.setText("Users");
        viewUsersGroup.setLayout(new GridLayout(2,false));
        viewUsersGroup.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
        
        Composite tableComposite = _toolkit.createComposite(viewUsersGroup);
        GridData gridData = new GridData(SWT.FILL, SWT.FILL, false, true);
        tableComposite .setLayoutData(gridData);
        gridData.heightHint = 250;
        tableComposite .setLayout(new GridLayout(2,false));
        
        _table = new Table (tableComposite, SWT.SINGLE | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _table.setLinesVisible (true);
        _table.setHeaderVisible (true);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.heightHint = 350;
        _table.setLayoutData(data);
        
        _tableViewer = new TableViewer(_table);
        final TableSorter tableSorter = new TableSorter();
        
        String[] titles = { "Username", "JMX Management Rights" };
        int[] bounds = { 310, 200 };
        for (int i = 0; i < titles.length; i++) 
        {
            final int index = i;
            final TableViewerColumn viewerColumn = new TableViewerColumn(_tableViewer, SWT.NONE);
            final TableColumn column = viewerColumn.getColumn();

            column.setText(titles[i]);
            column.setWidth(bounds[i]);
            column.setResizable(true);

            //Setting the right sorter
            column.addSelectionListener(new SelectionAdapter() 
            {
                @Override
                public void widgetSelected(SelectionEvent e) 
                {
                    tableSorter.setColumn(index);
                    final TableViewer viewer = _tableViewer;
                    int dir = viewer .getTable().getSortDirection();
                    if (viewer.getTable().getSortColumn() == column) 
                    {
                        dir = dir == SWT.UP ? SWT.DOWN : SWT.UP;
                    } 
                    else 
                    {
                        dir = SWT.UP;
                    }
                    viewer.getTable().setSortDirection(dir);
                    viewer.getTable().setSortColumn(column);
                    viewer.refresh();
                }
            });

        }
        
        _tableViewer.setContentProvider(new ContentProviderImpl());
        _tableViewer.setLabelProvider(new LabelProviderImpl());
        _tableViewer.setSorter(tableSorter);
        _table.setSortColumn(_table.getColumn(0));
        _table.setSortDirection(SWT.UP);
        
        Composite buttonsComposite = _toolkit.createComposite(tableComposite);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        buttonsComposite.setLayoutData(gridData);
        buttonsComposite.setLayout(new GridLayout(1,false));
        
        final Button deleteUserButton = _toolkit.createButton(buttonsComposite, "Delete User", SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.TOP, false, false);
        gridData.widthHint = 125;
        deleteUserButton.setLayoutData(gridData);
        deleteUserButton.setEnabled(false);
        deleteUserButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                int selectionIndex = _table.getSelectionIndex();

                if (selectionIndex != -1)
                {
                    final CompositeData selectedLogger = (CompositeData)_table.getItem(selectionIndex).getData();
                    String user = selectedLogger.get(USERNAME).toString(); 

                    int response = ViewUtility.popupOkCancelConfirmationMessage("User Management", 
                                                                    "Delete user: " + user + " ?");
                    if (response == SWT.OK)
                    {
                        try
                        {
                            _ummb.deleteUser(user);
                        }
                        catch(Exception e1)
                        {
                            MBeanUtility.handleException(_mbean, e1);
                        }
                        //TODO:display result
                        refresh(_mbean);;
                    }
                }
            }
        });
        
        final Button setPasswordButton = _toolkit.createButton(buttonsComposite, "Set Password ...", SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.TOP, false, false);
        gridData.widthHint = 125;
        setPasswordButton.setLayoutData(gridData);
        setPasswordButton.setEnabled(false);
        setPasswordButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                int selectionIndex = _table.getSelectionIndex();

                if (selectionIndex != -1)
                {
                    final CompositeData selectedLogger = (CompositeData)_table.getItem(
                                                                        selectionIndex).getData();
                    String user = selectedLogger.get(USERNAME).toString();
                    InputDialog id = new InputDialog(setPasswordButton.getShell(),"Set Password",
                                        "Please enter the new password for '" + user + "':",null,null);
                    
                    int returnValue;
                    while((returnValue = id.open()) == InputDialog.OK)
                    {
                        if (id.getValue() == null || id.getValue().toString().length() == 0)
                        {                            
                            ViewUtility.popupErrorMessage("Set Password", "Please enter a valid password");                       
                        }
                        else
                        {
                            break;
                        }
                    }
                    
                    if (returnValue  == InputDialog.OK)
                    {
                        char[] password = id.getValue().toCharArray();

                        // Retrieve the MBean version. If we have a version 1 UMMBean then 
                        // it expects the password to be sent as a hashed value.
                        if (_mbean.getVersion() == 1)
                        {
                            try
                            {
                                password = ViewUtility.getHash(id.getValue());
                            }
                            catch (Exception hashException)
                            {
                                ViewUtility.popupErrorMessage("Set Password",
                                        "Unable to calculate hash for Password:"
                                        + hashException.getMessage());
                                return;
                            }
                        }

                        try
                        {
                            _ummb.setPassword(user, password);
                            //TODO display result
                        }
                        catch(Exception e2)
                        {
                            MBeanUtility.handleException(_mbean, e2);
                        }
                    }
                }
            }
        });
        
        final Button setRightsButton = _toolkit.createButton(buttonsComposite, "Set Rights ...", SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.TOP, false, false);
        gridData.widthHint = 125;
        setRightsButton.setLayoutData(gridData);
        setRightsButton.setEnabled(false);
        setRightsButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                int selectionIndex = _table.getSelectionIndex();

                if (selectionIndex != -1)
                {
                    final CompositeData selectedLogger = (CompositeData)_table.getItem(
                                                                        selectionIndex).getData();
                    String user = selectedLogger.get(USERNAME).toString();
                    
                    setRights(setRightsButton.getShell(), user);
                }
            }
        });
        
        final Button addUserButton = _toolkit.createButton(buttonsComposite, "Add New User ...", SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.BOTTOM, false, true);
        gridData.widthHint = 125;
        addUserButton.setLayoutData(gridData);
        addUserButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                addUser(addUserButton.getShell());
            }
        });
        
        _tableViewer.addSelectionChangedListener(new ISelectionChangedListener(){
            public void selectionChanged(SelectionChangedEvent evt)
            {
                int selectionIndex = _table.getSelectionIndex();

                if (selectionIndex != -1)
                {
                    deleteUserButton.setEnabled(true);
                    setPasswordButton.setEnabled(true);
                    setRightsButton.setEnabled(true);
                }
                else
                {
                    deleteUserButton.setEnabled(false);
                    setPasswordButton.setEnabled(false);
                    setRightsButton.setEnabled(false);
                }
            }
        });
        
        Group miscGroup = new Group(paramsComposite, SWT.SHADOW_NONE);
        miscGroup.setBackground(paramsComposite.getBackground());
        miscGroup.setText("Misc");
        gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        miscGroup.setLayoutData(gridData);
        miscGroup.setLayout(new GridLayout(2,false));

        final Button runtimeRootLoggerLevelButton = _toolkit.createButton(miscGroup, 
                                                                "Reload User Details", SWT.PUSH);
        if(_mbean.getVersion() == 1)
        {
            _toolkit.createLabel(miscGroup, " Loads the current management rights file from disk");
        }
        else
        {
            _toolkit.createLabel(miscGroup, " Loads the current password and management rights files from disk");
        }
        runtimeRootLoggerLevelButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                int response = ViewUtility.popupOkCancelConfirmationMessage("Reload User Data", 
                                                            "Do you want to reload user data ?");
                if (response == SWT.OK)
                {
                    try
                    {
                        _ummb.reloadData();
                        //TODO:display result
                    }
                    catch(Exception e3)
                    {
                        MBeanUtility.handleException(_mbean, e3);
                    }
                    refresh(_mbean);
                }
            }
        });

    }

    
    /**
     * Content Provider class for the table viewer
     */
    private class ContentProviderImpl  implements IStructuredContentProvider
    {
        
        public void inputChanged(Viewer v, Object oldInput, Object newInput)
        {
            
        }
        
        public void dispose()
        {
            
        }
        
        public Object[] getElements(Object parent)
        {
            Collection<Object> rowCollection = ((TabularDataSupport) parent).values();
           
            return rowCollection.toArray();
        }
    }
    
    /**
     * Label Provider class for the table viewer
     */
    private class LabelProviderImpl extends LabelProvider implements ITableLabelProvider
    {
        @Override
        public String getColumnText(Object element, int columnIndex)
        {
            switch (columnIndex)
            {
                case 0 : // username column 
                    return (String) ((CompositeData) element).get(USERNAME);
                case 1 : // rights column 
                    return classifyUserRights((CompositeData) element);
                default :
                    return "-";
            }
        }
        
        @Override
        public Image getColumnImage(Object element, int columnIndex)
        {
            return null;
        }
        
    }

    /**
     * Sorter class for the table viewer.
     *
     */
    private class TableSorter extends ViewerSorter
    {
        private int column;
        private static final int ASCENDING = 0;
        private static final int DESCENDING = 1;

        private int direction;

        public TableSorter()
        {
            this.column = 0;
            direction = ASCENDING;
        }

        public void setColumn(int column)
        {
            if(column == this.column)
            {
                // Same column as last sort; toggle the direction
                direction = 1 - direction;
            }
            else
            {
                // New column; do an ascending sort
                this.column = column;
                direction = ASCENDING;
            }
        }

        @Override
        public int compare(Viewer viewer, Object e1, Object e2)
        {
            CompositeData user1 = (CompositeData) e1;
            CompositeData user2 = (CompositeData) e2;
            
            int comparison = 0;
            switch(column)
            {
                case 0:
                    comparison = String.valueOf(user1.get(USERNAME)).compareTo(
                                                String.valueOf(user2.get(USERNAME)));
                    break;
                case 1:
                    comparison = classifyUserRights(user1).compareTo(classifyUserRights(user2));
                    break;
                default:
                    comparison = 0;
            }
            // If descending order, flip the direction
            if(direction == DESCENDING)
            {
                comparison = -comparison;
            }
            return comparison;
        }
    }
    
    private String classifyUserRights(CompositeData user)
    {
        Boolean read = (Boolean)user.get(RIGHTS_READ_ONLY);
        Boolean write = (Boolean)user.get(RIGHTS_READ_WRITE);
        Boolean admin = (Boolean)user.get(RIGHTS_ADMIN);
        
        if(admin)
        {
            return "Admin";
        }
        else if(write)
        {
            return "Read & Write";
        }
        else if(read)
        {
            return "Read Only";
        }
        else
        {
            return "No Access";
        }
    }
    
    private void setRights(final Shell parent, final String user)
    {
        final Shell shell = ViewUtility.createModalDialogShell(parent, "Set Rights");
        
        Label overview = _toolkit.createLabel(shell,"Select rights for user '" + user + "':");
        overview.setBackground(shell.getBackground());
        
        Composite buttons = _toolkit.createComposite(shell, SWT.NONE);
        buttons.setBackground(shell.getBackground());
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        buttons.setLayout(new GridLayout(4,false));

        final Button noneButton = new Button(buttons, SWT.RADIO);
        noneButton.setText("No Access");
        noneButton.setSelection(true);
        final Button readButton = new Button(buttons, SWT.RADIO);
        readButton.setText("Read Only");
        final Button writeButton = new Button(buttons, SWT.RADIO);
        writeButton.setText("Read + Write");
        final Button adminButton = new Button(buttons, SWT.RADIO);
        adminButton.setText("Admin");

        Composite okCancelButtonsComp = _toolkit.createComposite(shell);
        okCancelButtonsComp.setBackground(shell.getBackground());
        okCancelButtonsComp.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, true, true));
        okCancelButtonsComp.setLayout(new GridLayout(2,false));
        
        Button okButton = _toolkit.createButton(okCancelButtonsComp, "OK", SWT.PUSH);
        okButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
        Button cancelButton = _toolkit.createButton(okCancelButtonsComp, "Cancel", SWT.PUSH);
        cancelButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));

        okButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                boolean read = readButton.getSelection();
                boolean write = writeButton.getSelection();
                boolean admin = adminButton.getSelection();
                
                shell.dispose();
                try
                {
                    _ummb.setRights(user,read,write,admin);
                    //TODO: display result?
                }
                catch(Exception e4)
                {
                    MBeanUtility.handleException(_mbean, e4);
                }
                refresh(_mbean);
            }
        });

        cancelButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                shell.dispose();
            }
        });

        shell.setDefaultButton(okButton);
        shell.pack();
        shell.open();
    }
    
    private void addUser(final Shell parent)
    {
        final Shell shell = ViewUtility.createModalDialogShell(parent, "Add New User");
        
        Composite usernameComposite = _toolkit.createComposite(shell, SWT.NONE);
        usernameComposite.setBackground(shell.getBackground());
        usernameComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        usernameComposite.setLayout(new GridLayout(2,false));
        
        _toolkit.createLabel(usernameComposite,"Username:").setBackground(shell.getBackground());
        final Text usernameText = new Text(usernameComposite, SWT.BORDER);
        usernameText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        
        Composite passwordComposite = _toolkit.createComposite(shell, SWT.NONE);
        passwordComposite.setBackground(shell.getBackground());
        passwordComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        passwordComposite.setLayout(new GridLayout(2,false));
        
        _toolkit.createLabel(passwordComposite,"Password:").setBackground(shell.getBackground());
        final Text passwordText = new Text(passwordComposite, SWT.BORDER);
        passwordText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        
        Group buttonGroup = new Group(shell, SWT.NONE);
        buttonGroup.setText("JMX Management Rights");
        buttonGroup.setBackground(shell.getBackground());
        buttonGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        buttonGroup.setLayout(new GridLayout(4,false));

        final Button noneButton = new Button(buttonGroup, SWT.RADIO);
        noneButton.setText("No Access");
        noneButton.setSelection(true);
        final Button readButton = new Button(buttonGroup, SWT.RADIO);
        readButton.setText("Read Only");
        final Button writeButton = new Button(buttonGroup, SWT.RADIO);
        writeButton.setText("Read + Write");
        final Button adminButton = new Button(buttonGroup, SWT.RADIO);
        adminButton.setText("Admin");

        Composite okCancelButtonsComp = _toolkit.createComposite(shell);
        okCancelButtonsComp.setBackground(shell.getBackground());
        okCancelButtonsComp.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, true, true));
        okCancelButtonsComp.setLayout(new GridLayout(2,false));
        
        Button okButton = _toolkit.createButton(okCancelButtonsComp, "OK", SWT.PUSH);
        okButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
        Button cancelButton = _toolkit.createButton(okCancelButtonsComp, "Cancel", SWT.PUSH);
        cancelButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));

        okButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                String username = usernameText.getText();
                String password = passwordText.getText();
                
                if (username == null || username.length() == 0)
                {                            
                    ViewUtility.popupErrorMessage("Add New User", "Please enter a valid username");
                    return;
                }
                
                if (password == null || password.length() == 0)
                {                            
                    ViewUtility.popupErrorMessage("Add New User", "Please enter a valid password");
                    return;
                }
                
                boolean read = readButton.getSelection();
                boolean write = writeButton.getSelection();
                boolean admin = adminButton.getSelection();
                
                shell.dispose();
                try
                {
                    _ummb.createUser(username, password.toCharArray(), read, write, admin);
                    //TODO: display result?
                }
                catch(Exception e5)
                {
                    MBeanUtility.handleException(_mbean, e5);
                }

                refresh(_mbean);
            }
        });

        cancelButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                shell.dispose();
            }
        });

        shell.setDefaultButton(okButton);
        shell.pack();
        shell.open();
    }
}
