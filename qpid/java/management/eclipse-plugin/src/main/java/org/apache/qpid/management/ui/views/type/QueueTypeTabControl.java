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
package org.apache.qpid.management.ui.views.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;

import static org.apache.qpid.management.ui.Constants.QUEUE;
import static org.apache.qpid.management.ui.Constants.ATTRIBUTE_QUEUE_DEPTH;

import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ManagedServer;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.AttributeData;
import org.apache.qpid.management.ui.views.MBeanView;
import org.apache.qpid.management.ui.views.NavigationView;
import org.apache.qpid.management.ui.views.ViewUtility;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class QueueTypeTabControl extends MBeanTypeTabControl
{   
    private MBeanServerConnection _mbsc;
    private ManagedBroker _vhmb;
    //Map for storing queue depths for servers using Qpid JMX API 1.2 and below
    private Map<ManagedBean,Long> _queueDepths = new HashMap<ManagedBean, Long>();
    
    public QueueTypeTabControl(TabFolder tabFolder, ManagedServer server, String virtualHost)
    {
        super(tabFolder,server,virtualHost,QUEUE);
        _mbsc = (MBeanServerConnection) _serverRegistry.getServerConnection();
        _vhmb = (ManagedBroker) MBeanServerInvocationHandler.newProxyInstance(_mbsc, 
                                _vhostMbean.getObjectName(), ManagedBroker.class, false);
    }
    
    @Override
    public void refresh(ManagedBean mbean)
    {
        if(_ApiVersion.greaterThanOrEqualTo(1, 3))
        {
            //Qpid JMX API 1.3+, use this virtualhosts VirtualHostManager MBean
            //to retrieve the Queue Name and Queue Depth
            
            Map<String,Long> queueNamesDepths = null;
            try
            {
                queueNamesDepths = _vhmb.viewQueueNamesDepths();
            }
            catch(Exception e)
            {
                MBeanUtility.handleException(_vhostMbean, e);
            }
            
            _tableViewer.setInput(queueNamesDepths);
        }
        else
        {
            //Qpid JMX API 1.2 or below, use the ManagedBeans and look
            //up the attribute value for each
            _mbeans = getMbeans();
            _tableViewer.setInput(_mbeans);
        }

        layout();
    }
    
    @Override
    protected List<ManagedBean> getMbeans()
    {
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(MBeanView.getServer());
        
        try
        {
            return extractQueueDetails(serverRegistry.getQueues(MBeanView.getVirtualHost()));
        }
        catch(Exception e)
        {
            MBeanUtility.handleException(null, e);
            return null;
        }
       
    }
    
    private List<ManagedBean> extractQueueDetails(List<ManagedBean> list) throws Exception
    {
        _queueDepths.clear();

        List<ManagedBean> items = new ArrayList<ManagedBean>();
        for (ManagedBean mbean : list)
        {         
            AttributeData data = MBeanUtility.getAttributeData(mbean, ATTRIBUTE_QUEUE_DEPTH);
            _queueDepths.put(mbean, Long.valueOf(data.getValue().toString()));

            items.add(mbean);
        }

        return items;
    }

    @Override
    protected void createTable(Composite tableComposite)
    {
        _table = new Table (tableComposite, SWT.MULTI | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _table.setLinesVisible (true);
        _table.setHeaderVisible (true);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        _table.setLayoutData(data);
        
        _tableViewer = new TableViewer(_table);

        String[] titles = new String[]{"Queue Name", "Queue Depth"};
        int[] bounds = new int[]{325, 200};
        
        final TableSorter tableSorter;
        
        if(_ApiVersion.greaterThanOrEqualTo(1, 3))
        {
            //QpidJMX API 1.3+ using the new getQueueNamesDepths method in VHostManager MBean.
            //requires sorting Map.Entry elements
            tableSorter = new NewerTableSorter();
        }
        else
        {
            //QpidJMX API 1.2 or below. Requires sorting ManagedBeans and using the _queueDepths map
            tableSorter = new OlderTableSorter();
        }
                
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
        
        if(_ApiVersion.greaterThanOrEqualTo(1, 3))
        {
            _tableViewer.setContentProvider(new NewerContentProviderImpl());
            _tableViewer.setLabelProvider(new NewerLabelProviderImpl());
        }
        else
        {
            _tableViewer.setContentProvider(new OlderContentProviderImpl());
            _tableViewer.setLabelProvider(new OlderLabelProviderImpl());
        }
        
        _tableViewer.setUseHashlookup(true);
        _tableViewer.setSorter(tableSorter);
        _table.setSortColumn(_table.getColumn(0));
        _table.setSortDirection(SWT.UP);
    }
    
    /**
     * Content Provider class for the table viewer for Qpid JMX API 1.2 and below.
     */
    private class OlderContentProviderImpl  implements IStructuredContentProvider
    {
        
        public void inputChanged(Viewer v, Object oldInput, Object newInput)
        {
            
        }
        
        public void dispose()
        {
            
        }
        
        @SuppressWarnings("unchecked")
        public Object[] getElements(Object parent)
        {
            return ((List<ManagedBean>) parent).toArray();
        }
    }
    
    /**
     * Label Provider class for the table viewer for Qpid JMX API 1.2 and below.
     */
    private class OlderLabelProviderImpl extends LabelProvider implements ITableLabelProvider
    {
        @Override
        public String getColumnText(Object element, int columnIndex)
        {
            ManagedBean mbean = (ManagedBean) element;
            
            switch (columnIndex)
            {
                case 0 : // name column 
                    return mbean.getName();
                case 1 : // queue depth column 
                    return getQueueDepthString(_queueDepths.get(mbean));
                default:
                    return "-";
            }
        }
        
        @Override
        public Image getColumnImage(Object element, int columnIndex)
        {
            return null;
        }    
    }
    
    private String getQueueDepthString(Long value)
    {
        if(value == null)
        {
            return "-";
        }

        if (_ApiVersion.lessThanOrEqualTo(1,1))
        {
            //Qpid JMX API 1.1 or below, returns KB  
            double mb = 1024.0;

            if(value > mb) //MB
            {
                return String.format("%.3f", (Double)(value / mb)) + " MB";
            }
            else //KB
            {
                return value + " KB";
            }
        }
        else
        {
            //Qpid JMX API 1.2 or above, returns Bytes
            double mb = 1024.0 * 1024.0;
            double kb = 1024.0;

            if(value >= mb) //MB
            {
                return String.format("%.3f", (Double)(value / mb)) + " MB";
            }
            else if (value >= kb) //KB
            {
                return String.format("%.3f", (Double)(value / kb)) + " KB";
            }
            else //Bytes
            {
                return value + " Bytes";
            }
        }
    }
    

    /**
     * Abstract sorter class for the table viewer.
     *
     */
    private abstract class TableSorter extends ViewerSorter
    {
        protected int column;
        protected static final int ASCENDING = 0;
        protected static final int DESCENDING = 1;

        protected int direction;

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
        public abstract int compare(Viewer viewer, Object e1, Object e2);
    }
    
    /**
     * sorter class for the table viewer for Qpid JMX API 1.2 and below.
     *
     */
    private class OlderTableSorter extends TableSorter
    {
        public OlderTableSorter()
        {
            super();
        }

        @Override
        public int compare(Viewer viewer, Object e1, Object e2)
        {
            ManagedBean mbean1 = (ManagedBean) e1;
            ManagedBean mbean2 = (ManagedBean) e2;
            
            int comparison = 0;
            switch(column)
            {
                case 0: //name
                    comparison = mbean1.getName().compareTo(mbean2.getName());
                    break;
                case 1: //queue depth
                    comparison = _queueDepths.get(mbean1).compareTo(_queueDepths.get(mbean2));
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
    
    /**
     * sorter class for the table viewer for Qpid JMX API 1.3 and above.
     *
     */
    private class NewerTableSorter extends TableSorter
    {
        public NewerTableSorter()
        {
            super();
        }

        @SuppressWarnings("unchecked")
        @Override
        public int compare(Viewer viewer, Object e1, Object e2)
        {
            Map.Entry<String, Long> queue1 = (Map.Entry<String, Long>) e1;
            Map.Entry<String, Long> queue2 = (Map.Entry<String, Long>) e2;
            
            int comparison = 0;
            switch(column)
            {
                case 0://name
                    comparison = (queue1.getKey()).compareTo(queue2.getKey());
                    break;
                case 1://queue depth
                    comparison = (queue1.getValue()).compareTo(queue2.getValue());;
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

    /**
     * Content Provider class for the table viewer for Qpid JMX API 1.3 and above.
     */
    private class NewerContentProviderImpl  implements IStructuredContentProvider
    {
        
        public void inputChanged(Viewer v, Object oldInput, Object newInput)
        {
            
        }
        
        public void dispose()
        {
            
        }
        
        @SuppressWarnings("unchecked")
        public Object[] getElements(Object parent)
        {
            Map<String, Long> map = (Map<String, Long>) parent;
            return map.entrySet().toArray(new Map.Entry[0]);
        }
    }
    
    /**
     * Label Provider class for the table viewer for for Qpid JMX API 1.3 and above.
     */
    private class NewerLabelProviderImpl extends LabelProvider implements ITableLabelProvider
    {
        @SuppressWarnings("unchecked")
        @Override
        public String getColumnText(Object element, int columnIndex)
        {
            Map.Entry<String, Long> queue = (Map.Entry<String, Long>) element;
            
            switch (columnIndex)
            {
                case 0 : // name column 
                    return queue.getKey();
                case 1 : // depth column 
                    return getQueueDepthString(queue.getValue());
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
    
    @Override
    protected void addMBeanToFavourites()
    {
        int selectionIndex = _table.getSelectionIndex();

        if (selectionIndex == -1)
        {
            return;
        }

        int[] selectedIndices = _table.getSelectionIndices();
        
        ArrayList<ManagedBean> selectedMBeans = new ArrayList<ManagedBean>();
        
        if(_ApiVersion.greaterThanOrEqualTo(1, 3))
        {
            //if we have Qpid JMX API 1.3+ the entries are created from Map.Entry<String,Long>
            for(int index = 0; index < selectedIndices.length ; index++)
            {
                Map.Entry<String, Long> queueEntry = (Map.Entry<String, Long>) _table.getItem(selectedIndices[index]).getData();
                String queueName = queueEntry.getKey();
                selectedMBeans.add(_serverRegistry.getQueue(queueName, _virtualHost));
            }
        }
        else
        {
            //if we have a Qpid JMX API 1.2 or less server, entries are created from ManagedBeans directly
            for(int index = 0; index < selectedIndices.length ; index++)
            {
                ManagedBean mbean = (ManagedBean) _table.getItem(selectedIndices[index]).getData();
                selectedMBeans.add(mbean);
            }
        }

        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow(); 
        NavigationView view = (NavigationView)window.getActivePage().findView(NavigationView.ID);
        
        ManagedBean bean = null;
        try
        {
            for(ManagedBean mbean: selectedMBeans)
            {
                bean = mbean;
                view.addManagedBean(mbean);
            }
        }
        catch (Exception ex)
        {
            MBeanUtility.handleException(bean, ex);
        }
    }
    
    @Override
    protected void openMBean()
    {
        int selectionIndex = _table.getSelectionIndex();

        if (selectionIndex == -1)
        {
            return;
        }
        
        ManagedBean selectedMBean;
        
        if(_ApiVersion.greaterThanOrEqualTo(1, 3))
        {
            //if we have Qpid JMX API 1.3+ the entries are created from Map.Entry<String,Long>
            Map.Entry<String, Long> queueEntry = (Map.Entry<String, Long>) _table.getItem(selectionIndex).getData();

            String queueName = queueEntry.getKey();
            selectedMBean = _serverRegistry.getQueue(queueName, _virtualHost);
        }
        else
        {
            //if we have a Qpid JMX API 1.2 or less server, entries are created from ManagedBeans directly
            selectedMBean = (ManagedBean)_table.getItem(selectionIndex).getData();
        }

        if(selectedMBean == null)
        {
            ViewUtility.popupErrorMessage("Error", "Unable to retrieve the selected MBean to open it");
            return;
        }

        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow(); 
        MBeanView view = (MBeanView) window.getActivePage().findView(MBeanView.ID);
        try
        {
            view.openMBean(selectedMBean);
        }
        catch (Exception ex)
        {
            MBeanUtility.handleException(selectedMBean, ex);
        }
    }
}
