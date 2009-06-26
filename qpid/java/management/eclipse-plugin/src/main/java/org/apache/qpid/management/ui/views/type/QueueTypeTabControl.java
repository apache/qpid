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

import static org.apache.qpid.management.ui.Constants.QUEUE;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.AttributeData;
import org.apache.qpid.management.ui.views.MBeanView;
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

public class QueueTypeTabControl extends MBeanTypeTabControl
{   
    private HashMap<ManagedBean, Long> _queueDepths = new HashMap<ManagedBean, Long>();
    private HashMap<ManagedBean, Long> _activeConsumerCounts = new HashMap<ManagedBean, Long>();

    
    public QueueTypeTabControl(TabFolder tabFolder)
    {
        super(tabFolder,QUEUE);
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
        _activeConsumerCounts.clear();
        
        List<ManagedBean> items = new ArrayList<ManagedBean>();
        for (ManagedBean mbean : list)
        {         
            AttributeData data = MBeanUtility.getAttributeData(mbean, Constants.ATTRIBUTE_QUEUE_DEPTH);
            _queueDepths.put(mbean, Long.valueOf(data.getValue().toString()));
            data = MBeanUtility.getAttributeData(mbean, Constants.ATTRIBUTE_QUEUE_CONSUMERCOUNT);
            _activeConsumerCounts.put(mbean, Long.valueOf(data.getValue().toString()));
            
            items.add(mbean);
        }

        return items;
    }
    
    @Override
    protected void createTable(Composite tableComposite)
    {
        _table = new Table (tableComposite, SWT.SINGLE | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _table.setLinesVisible (true);
        _table.setHeaderVisible (true);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        _table.setLayoutData(data);
        
        _tableViewer = new TableViewer(_table);
        final TableSorter tableSorter = new TableSorter();
        
        String[] titles = { "Name", "QueueDepth", "Active Consumer Count"};
        int[] bounds = { 250, 175, 175};
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
        
        @SuppressWarnings("unchecked")
        public Object[] getElements(Object parent)
        {
            return ((List<ManagedBean>) parent).toArray();
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
            ManagedBean mbean = (ManagedBean) element;
            
            switch (columnIndex)
            {
                case 0 : // name column 
                    return mbean.getName();
                case 1 : // queue depth column 
                    return getQueueDepthString(mbean, _queueDepths.get(mbean));
                case 2 : // consumer count column
                    return String.valueOf(_activeConsumerCounts.get(mbean));
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
    
    private String getQueueDepthString(ManagedBean mbean, Long value)
    {
        if (mbean.getVersion() == 1)  //mbean is v1 and returns KB
        {           
            Double mb = 1024.0;
            
            if(value > mb) //MB
            {
                return String.format("%.3f", (Double)(value / mb)) + " MB";
            }
            else //KB
            {
                return value + " KB";
            }
        }
        else  //mbean is v2+ and returns Bytes
        {
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
                    break;
                case 2: //active consumer count
                    comparison = _activeConsumerCounts.get(mbean1).compareTo(_activeConsumerCounts.get(mbean2));
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

}
