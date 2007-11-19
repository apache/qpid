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

import static org.apache.qpid.management.ui.Constants.BUTTON_REFRESH;
import static org.apache.qpid.management.ui.Constants.FONT_BOLD;
import static org.apache.qpid.management.ui.Constants.FONT_ITALIC;
import static org.apache.qpid.management.ui.Constants.FONT_NORMAL;

import java.util.Collections;
import java.util.HashMap;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.AttributeData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;

/**
 * Abstract class to be extended by the Controller classes for different MBean types (Connection, Queue, Exchange)
 */
public abstract class MBeanTypeTabControl
{
    private FormToolkit  _toolkit = null;
    private Form _form = null;
    private TabFolder _tabFolder = null;
    private Composite _composite = null;
    private Composite _headerComposite = null;
    private Composite _listComposite = null;
    private Label _labelName = null;
    private Label _labelDesc = null;
    private Label _labelList = null;
    
    private org.eclipse.swt.widgets.List _list = null;
    private Button _refreshButton = null;
    private Button _addButton = null;
    
    private String _type = null;
    
    // maps an mbean name with the mbean object. Required to get mbean object when an mbean
    // is to be added to the navigation view. 
    private HashMap<String, ManagedBean> _objectsMap = new HashMap<String, ManagedBean>();
    private Sorter _sorterByName = new Sorter();
    
    public MBeanTypeTabControl(TabFolder tabFolder, String type)
    {
        _type = type;
        _tabFolder = tabFolder;
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createForm(_tabFolder);
        createFormComposite();
    }
    
    public FormToolkit getToolkit()
    {
        return _toolkit;
    }
    
    public Control getControl()
    {
        return _form;
    }
    
    public String getType()
    {
        return _type;
    }
    
    protected List getListWidget()
    {
        return _list;
    }
    
    protected HashMap<String, ManagedBean> getMBeansMap()
    {
        return _objectsMap;
    }
    
    public Sorter getMBeanNameSorter()
    {
        return _sorterByName;
    }
    
    public Button getAddButton()
    {
        return _addButton;
    }
    
    public Button getRefreshButton()
    {
        return _refreshButton;
    }
        
    /**
     * Creates the main Composite, which will contain all other Composites and Widgets
     */
    protected void createFormComposite()
    {
        _form.getBody().setLayout(new GridLayout());
        _composite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        _composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout();
        layout.verticalSpacing = 10;
        layout.horizontalSpacing = 0;
        _composite.setLayout(layout);
    }
    
    protected Composite getFormComposite()
    {
        return _composite;
    }
    
    /**
     * Creates the header composite, which has MBean type name and description
     * @param parentComposite
     */
    protected void createHeaderComposite(Composite parentComposite)
    {
        _headerComposite = _toolkit.createComposite(parentComposite);
        _headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout layout = new GridLayout();
        layout.verticalSpacing = 10;
        layout.horizontalSpacing = 0;
        _headerComposite.setLayout(layout);
        
        _labelName = _toolkit.createLabel(_headerComposite, "Type:", SWT.NONE);
        GridData gridData = new GridData(SWT.CENTER, SWT.TOP, true, false);
        _labelName.setLayoutData(gridData);
        _labelName.setFont(ApplicationRegistry.getFont(FONT_BOLD));
        
        _labelDesc = _toolkit.createLabel(_headerComposite, " ", SWT.NONE);
        _labelDesc.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
        _labelDesc.setFont(ApplicationRegistry.getFont(FONT_ITALIC));
        
        _headerComposite.layout();
    }
    
    /**
     * Creates Composite, which contains the common buttons - Add and Refresh.
     * @param parentComposite
     */
    protected void createButtonsComposite(Composite parentComposite)
    {
        Composite composite = _toolkit.createComposite(parentComposite);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout layout = new GridLayout(2, true);
        layout.verticalSpacing = 10;
        layout.horizontalSpacing = 20;
        composite.setLayout(layout);
        
        createAddButton(composite);
        createRefreshButton(composite);
    }
    
    /**
     * Creates the Add button, which adds the selected item to the navigation view
     * @param parentComposite
     */
    protected void createAddButton(Composite parentComposite)
    {
        Button _addButton = _toolkit.createButton(parentComposite, "<- Add to Navigation", SWT.PUSH);
        GridData gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        _addButton.setLayoutData(gridData);
        _addButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                if (_list.getSelectionCount() == 0)
                    return;
                
                String[] selectedItems = _list.getSelection();
                for (int i = 0; i < selectedItems.length; i++)
                {
                    String name = selectedItems[i];
                    // pass the ManagedBean to the navigation view to be added
                    ManagedBean mbean = _objectsMap.get(name);
                    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow(); 
                    NavigationView view = (NavigationView)window.getActivePage().findView(NavigationView.ID);
                    try
                    {
                        view.addManagedBean(mbean);
                    }
                    catch (Exception ex)
                    {
                        MBeanUtility.handleException(mbean, ex);
                    }
                }
            }
        });
    }
    
    /**
     * Creates the Refresh button, which gets syncs the items with the broker server
     * @param parentComposite
     */
    protected void createRefreshButton(Composite parentComposite)
    {
        Button _refreshButton = _toolkit.createButton(parentComposite, BUTTON_REFRESH, SWT.PUSH);
        GridData gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        gridData.widthHint = 120;
        _refreshButton.setLayoutData(gridData);
        _refreshButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                try
                {
                    // refresh the list from the broker server
                    populateList();
                }
                catch (Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
        });
    }
    
    /**
     * Creates the Composite, which contains the items ( Connections, Exchanges or Queues)
     * @param parentComposite
     */
    protected void createListComposite(Composite parentComposite)
    {
        // Composite to contain the item list 
        _listComposite = _toolkit.createComposite(parentComposite);
        GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        _listComposite.setLayoutData(gridData);
        GridLayout layout = new GridLayout();
        layout.verticalSpacing = 0;
        _listComposite.setLayout(layout);
        
        // Label for item name
        _labelList = _toolkit.createLabel(_listComposite, " ", SWT.CENTER);
        gridData = new GridData(SWT.CENTER, SWT.TOP, true, false, 1, 1);
        _labelList.setLayoutData(gridData);
        _labelList.setFont(ApplicationRegistry.getFont(FONT_NORMAL));
        
        _list = new List(_listComposite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true,1, 1);
        _list.setLayoutData(gridData);
        
    }
    
    /**
     * This is called from MBean View to refresh the tab contents
     * @throws Exception
     */
    public void refresh() throws Exception
    {        
        setLabelValues();
        populateList();
        layout();
    }
    
    protected void setLabelValues()
    {
        _labelName.setText("Type : " + _type);        
        _labelDesc.setText("Select the " + _type + "(s) to add in the Navigation View");
        _labelList.setText("-- List of " + _type + "s --");
    }
    
    protected abstract void populateList() throws Exception;
    
    public void layout()
    {
        _form.layout(true);
        _form.getBody().layout(true, true);
    }
    
    // sets the map with appropriate mbean and name
    protected String[] getItems(java.util.List<ManagedBean> list)
    {
        if (list == null)
            return new String[0];
        
        Collections.sort(list, _sorterByName);
        String[] items = new String[list.size()];
        int i = 0;
        for (ManagedBean mbean : list)
        {
            items[i++] = mbean.getName();
            _objectsMap.put(mbean.getName(), mbean);
        }
        return items;
    }
    
    protected class ComparatorImpl implements java.util.Comparator<AttributeData>
    {
        public int compare(AttributeData data1, AttributeData data2)
        {
            Integer int1 = Integer.parseInt(data1.getValue().toString());
            Integer int2 = Integer.parseInt(data2.getValue().toString());
            return int1.compareTo(int2) * -1;
        }
    }
    
    protected class Sorter implements java.util.Comparator<ManagedBean>
    {
        public int compare(ManagedBean mbean1, ManagedBean mbean2)
        {
            return mbean1.getName().compareTo(mbean2.getName());
        }
    }
}
