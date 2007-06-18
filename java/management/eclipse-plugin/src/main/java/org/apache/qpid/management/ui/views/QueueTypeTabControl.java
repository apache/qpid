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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.AttributeData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.TabFolder;

/**
 * Controller class, which takes care of displaying appropriate information and widgets for Queues.
 * This allows user to select Queues and add those to the navigation view
 */
public class QueueTypeTabControl extends MBeanTypeTabControl
{
    private boolean _showTempQueues = false;
    private Button _sortBySizeButton = null;
    private Button _sortByConsumercountButton = null;
    private Button _sortByNameButton = null;
    private Button _showTempQueuesButton = null;
    
    private ComparatorImpl _sorterByAttribute = new ComparatorImpl();
    
    // Map required for sorting queues based on attribute values
    private Map<AttributeData, ManagedBean> _queueDepthMap = new LinkedHashMap<AttributeData, ManagedBean>();
    // Map used for sorting Queues based on consumer count
    private Map<AttributeData, ManagedBean> _queueConsumerCountMap = new LinkedHashMap<AttributeData, ManagedBean>();

    
    public QueueTypeTabControl(TabFolder tabFolder)
    {
        super(tabFolder, Constants.QUEUE);
        createWidgets();
    }
    
    protected void createWidgets()
    {        
        createHeaderComposite(getFormComposite());
        createButtonsComposite(getFormComposite());
        createListComposite();
    }
    
    @Override
    public void refresh() throws Exception
    {
        setLabelValues();
        selectDefaultSortingButton();
        populateList();
        layout();
    }
    
    /**
     * populates the map with mbean name and the mbean object.
     * @throws Exception
     */
    protected void populateList() throws Exception
    {
        // map should be cleared before populating it with new values
        getMBeansMap().clear();
        _queueDepthMap.clear();
        _queueConsumerCountMap.clear();
        
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(MBeanView.getServer());
        String[] items = null;
        java.util.List<ManagedBean> list = null;
        
        // populate the map and list with appropriate mbeans
        list = serverRegistry.getQueues(MBeanView.getVirtualHost());
        items = getQueueItems(list);
        // sort the refreshed list in the selected order
        if (_sortBySizeButton.getSelection())
        {
            sortQueuesByQueueDepth();
        }
        else if (_sortByConsumercountButton.getSelection())
        {
            sortQueuesByConsumerCount();
        }
        else
        {
            getListWidget().setItems(items);
        }      
    }
    
    private void selectDefaultSortingButton()
    {
        _sortByNameButton.setSelection(true);
        _sortBySizeButton.setSelection(false);
        _sortByConsumercountButton.setSelection(false);
        
        _showTempQueues = false;
        _showTempQueuesButton.setSelection(_showTempQueues);
    }
     
    protected void createListComposite()
    {
        // Composite to contain the item list 
        Composite composite = getToolkit().createComposite(getFormComposite());
        GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        composite.setLayoutData(gridData);
        GridLayout layout = new GridLayout(2, true);
        layout.verticalSpacing = 0;
        composite.setLayout(layout);
        
        createListComposite(composite);        
        
        // Composite to contain buttons like - Sort by size
        Composite _sortingComposite = getToolkit().createComposite(composite);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        _sortingComposite.setLayoutData(gridData);
        GridLayout gridLayout = new GridLayout();
        gridLayout.verticalSpacing = 20;
        _sortingComposite.setLayout(gridLayout);
        
        Group sortingGroup = new Group(_sortingComposite, SWT.SHADOW_NONE);
        sortingGroup.setBackground(_sortingComposite.getBackground());
        sortingGroup.setText(" Sort List By ");
        sortingGroup.setFont(ApplicationRegistry.getFont(Constants.FONT_BOLD));
        gridData = new GridData(SWT.CENTER, SWT.TOP, true, false);
        sortingGroup.setLayoutData(gridData);
        sortingGroup.setLayout(new GridLayout());
        
        _sortByNameButton = getToolkit().createButton(sortingGroup, Constants.QUEUE_SORT_BY_NAME, SWT.RADIO);
        gridData = new GridData(SWT.LEAD, SWT.CENTER, true, false);
        _sortByNameButton.setLayoutData(gridData);     
        _sortByNameButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                try
                {
                    // sort the stored list of items
                    java.util.List<String> list = new ArrayList<String>(getMBeansMap().keySet());
                    Collections.sort(list);
                    getListWidget().setItems(list.toArray(new String[0]));
                }
                catch (Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
        });
               
        _sortBySizeButton = getToolkit().createButton(sortingGroup, Constants.QUEUE_SORT_BY_DEPTH, SWT.RADIO);
        gridData = new GridData(SWT.LEAD, SWT.CENTER, true, false);
        _sortBySizeButton.setLayoutData(gridData);     
        _sortBySizeButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                try
                {
                    // sort the stored list of items
                    sortQueuesByQueueDepth();
                }
                catch (Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
        });
        
        _sortByConsumercountButton = getToolkit().createButton(sortingGroup, Constants.QUEUE_SORT_BY_CONSUMERCOUNT, SWT.RADIO);
        gridData = new GridData(SWT.LEAD, SWT.CENTER, true, false);
        _sortByConsumercountButton.setLayoutData(gridData);
        _sortByConsumercountButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                try
                {
                    sortQueuesByConsumerCount();
                }
                catch (Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
        });
        
        _showTempQueuesButton = getToolkit().createButton(_sortingComposite, Constants.QUEUE_SHOW_TEMP_QUEUES, SWT.CHECK);
        _showTempQueuesButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
        _showTempQueuesButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                Button button = (Button)e.widget;
                _showTempQueues = button.getSelection();
                try
                {
                    populateList();
                }
                catch (Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
        });
    }
    

    private String[] getQueueItems(java.util.List<ManagedBean> list) throws Exception
    {
        if (list == null)
            return new String[0];
        
        // Sort the list. It will keep the mbeans in sorted order in the _queueMap, which is required for
        // sorting the queue according to size etc
        Collections.sort(list, getMBeanNameSorter());
        java.util.List<String> items = new ArrayList<String>();;
        int i = 0;
        for (ManagedBean mbean : list)
        {
            if ((!_showTempQueues && mbean.isTempQueue()))
            {
                continue;
            }
            AttributeData data = MBeanUtility.getAttributeData(mbean, Constants.ATTRIBUTE_QUEUE_DEPTH);
            String value = mbean.getName() + " (" + data.getValue().toString() + " KB)";
            items.add(value);
            //items[i] = mbean.getName() + " (" + value + " KB)";
            getMBeansMap().put(value, mbean);
            _queueDepthMap.put(data, mbean);
            data = MBeanUtility.getAttributeData(mbean, Constants.ATTRIBUTE_QUEUE_CONSUMERCOUNT);
            _queueConsumerCountMap.put(data, mbean);
            i++;
        }
        
        return items.toArray(new String[0]);
    }
    
    
    private void sortQueuesByQueueDepth()
    {
        // Queues are already in the alphabetically sorted order in _queueMap, now sort for queueDepth
        java.util.List<AttributeData> list = new ArrayList<AttributeData>(_queueDepthMap.keySet());
        Collections.sort(list, _sorterByAttribute);
        
        String[] items = new String[list.size()];
        int i = 0;
        for (AttributeData data : list)
        {
            ManagedBean mbean = _queueDepthMap.get(data);
            String value = data.getValue().toString();
            items[i++] = mbean.getName() + " (" + value + " KB)";
        }
        getListWidget().setItems(items);
    }
    
    private void sortQueuesByConsumerCount()
    {
        java.util.List<AttributeData> list = new ArrayList<AttributeData>(_queueConsumerCountMap.keySet());
        Collections.sort(list, _sorterByAttribute);
        
        String[] items = new String[list.size()];
        int i = 0;
        for (AttributeData data : list)
        {
            ManagedBean mbean = _queueConsumerCountMap.get(data);
            String value = data.getValue().toString();
            items[i++] = mbean.getName() + " (" + value + " )";
        }
        getListWidget().setItems(items);
    }
}
