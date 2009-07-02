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
package org.apache.qpid.management.ui.views.exchange;

import static org.apache.qpid.management.ui.Constants.CONSOLE_IMAGE;
import static org.apache.qpid.management.ui.Constants.RESULT;

import java.util.Collection;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularDataSupport;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.management.ui.jmx.JMXManagedObject;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.views.AttributesTabControl;
import org.apache.qpid.management.ui.views.MBeanView;
import org.apache.qpid.management.ui.views.NavigationView;
import org.apache.qpid.management.ui.views.NumberVerifyListener;
import org.apache.qpid.management.ui.views.TabControl;
import org.apache.qpid.management.ui.views.ViewUtility;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;


/**
 * Control class for the Queue mbean Operations tab.
 */
public class ExchangeOperationsTabControl extends TabControl
{
    private FormToolkit _toolkit;
    private Form        _form;
    private Table _keysTable = null;
    private TableViewer _keysTableViewer = null;
    private Table _queuesTable = null;
    private TableViewer _queuesTableViewer = null;
    private Composite _paramsComposite = null;
            
    private TabularDataSupport _bindings = null;
    private ManagedExchange _emb;
    
    static final String ROUTING_KEY = ManagedExchange.COMPOSITE_ITEM_NAMES[0];
    static final String QUEUES = ManagedExchange.COMPOSITE_ITEM_NAMES[1];
    
    public ExchangeOperationsTabControl(TabFolder tabFolder, JMXManagedObject mbean, MBeanServerConnection mbsc)
    {
        super(tabFolder);
        _mbean = mbean;
        _emb = (ManagedExchange) MBeanServerInvocationHandler.newProxyInstance(mbsc, 
                                mbean.getObjectName(), ManagedExchange.class, false);
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createForm(_tabFolder);
        _form.getBody().setLayout(new GridLayout());
        createComposites();
        createWidgets();
    }
    
    private void createComposites()
    {
        _paramsComposite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        _paramsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _paramsComposite.setLayout(new GridLayout(2,false));
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
        _keysTable.setFocus();
    }
    
    @Override
    public void refresh(ManagedBean mbean)
    {
        _mbean = mbean;  
        if (_mbean == null)
        {
            _keysTableViewer.setInput(null);
            return;
        }
        
        _bindings = null;
        try
        {
            //gather a list of all keys and queues for display and selection
            _bindings = (TabularDataSupport) _emb.bindings();
        }
        catch (Exception e)
        {
            MBeanUtility.handleException(mbean,e);
        }
        TabControl controller = (TabControl) _paramsComposite.getData(TabControl.CONTROLLER);
        if(controller != null)
        {
            controller.refresh(_mbean);
        }
        
        _form.setVisible(false);
        _keysTableViewer.setInput(_bindings);
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
        Group bindingsGroup = new Group(_paramsComposite, SWT.SHADOW_NONE);
        bindingsGroup.setBackground(_paramsComposite.getBackground());
        bindingsGroup.setText("Bindings");
        bindingsGroup.setLayout(new GridLayout(2,false));
        GridData gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        bindingsGroup.setLayoutData(gridData);
               
        _keysTable = new Table (bindingsGroup, SWT.SINGLE | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _keysTable.setLinesVisible (true);
        _keysTable.setHeaderVisible (true);
        GridData data = new GridData(SWT.LEFT, SWT.TOP, true, true);
        data.heightHint = 325;
        _keysTable.setLayoutData(data);
        
        _keysTableViewer = new TableViewer(_keysTable);
        final TableSorter tableSorter = new TableSorter(ROUTING_KEY);
        
        String[] titles = {"Routing Key"};
        int[] bounds = {130};
        for (int i = 0; i < titles.length; i++) 
        {
            final int index = i;
            final TableColumn column = new TableColumn (_keysTable, SWT.NONE);

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
                    final TableViewer viewer = _keysTableViewer;
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
        
        _keysTableViewer.setContentProvider(new ContentProviderImpl(ROUTING_KEY));
        _keysTableViewer.setLabelProvider(new LabelProviderImpl(ROUTING_KEY));
        _keysTableViewer.setSorter(tableSorter);
        
        
        _queuesTable = new Table (bindingsGroup, SWT.SINGLE | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _queuesTable.setLinesVisible (true);
        _queuesTable.setHeaderVisible (true);
        data = new GridData(SWT.LEFT, SWT.TOP, true, true);
        data.heightHint = 325;
        _queuesTable.setLayoutData(data);
        
        _queuesTableViewer = new TableViewer(_queuesTable);
        final TableSorter queuesTableSorter = new TableSorter(QUEUES);
        
        titles = new String[]{"Queues"};
        bounds = new int[]{130};
        for (int i = 0; i < titles.length; i++) 
        {
            final int index = i;
            final TableColumn column = new TableColumn (_queuesTable, SWT.NONE);

            column.setText(titles[i]);
            column.setWidth(bounds[i]);
            column.setResizable(true);

            //Setting the right sorter
            column.addSelectionListener(new SelectionAdapter() 
            {
                @Override
                public void widgetSelected(SelectionEvent e) 
                {
                	queuesTableSorter.setColumn(index);
                    final TableViewer viewer = _queuesTableViewer;
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
        
        _queuesTableViewer.setContentProvider(new ContentProviderImpl(QUEUES));
        _queuesTableViewer.setLabelProvider(new LabelProviderImpl(QUEUES));
        _queuesTableViewer.setSorter(queuesTableSorter);
        
        _keysTableViewer.addSelectionChangedListener(new ISelectionChangedListener(){
            public void selectionChanged(SelectionChangedEvent evt)
            {
                int selectionIndex = _keysTable.getSelectionIndex();

                if (selectionIndex != -1)
                {
                	final CompositeData selectedMsg = (CompositeData)_keysTable.getItem(selectionIndex).getData();

                	String[] queues = (String[]) selectedMsg.get(QUEUES);
                	_queuesTableViewer.setInput(queues);
                }
                else
                {
                	_queuesTableViewer.setInput(null);
                }
            }
        });
        
        
        Composite rightComposite = _toolkit.createComposite(_paramsComposite);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        rightComposite.setLayoutData(gridData);
        rightComposite.setLayout(new GridLayout());
        
    	Group attributesGroup = new Group(rightComposite, SWT.SHADOW_NONE);
    	attributesGroup.setBackground(_paramsComposite.getBackground());
    	attributesGroup.setText("Attributes");
    	attributesGroup.setLayout(new GridLayout());
    	gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
    	attributesGroup.setLayoutData(gridData);

    	AttributesTabControl attr = new AttributesTabControl(attributesGroup, new int[]{125,175});
    	_paramsComposite.setData(TabControl.CONTROLLER, attr);


        Group createBindingsGroup = new Group(rightComposite, SWT.SHADOW_NONE);
        createBindingsGroup.setBackground(rightComposite.getBackground());
        createBindingsGroup.setText("Create New Binding");
        gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        createBindingsGroup.setLayoutData(gridData);
        createBindingsGroup.setLayout(new GridLayout(2,false));
        
        final Button createBindingButton = _toolkit.createButton(createBindingsGroup, "Create New Binding ...", SWT.PUSH);
        createBindingButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                //TODO
            }
        });
        
    }

    
    /**
     * Content Provider class for the table viewer
     */
    private class ContentProviderImpl implements IStructuredContentProvider
    {
    	String type;
    	
    	public ContentProviderImpl(String type)
    	{
    		this.type = type;
    	}

        public void inputChanged(Viewer v, Object oldInput, Object newInput)
        {
            
        }
        
        public void dispose()
        {
            
        }
        
        public Object[] getElements(Object parent)
        {
        	if(type.equals(ROUTING_KEY))
        	{
        		Collection<Object> rowCollection = ((TabularDataSupport) parent).values();

        		return rowCollection.toArray();
        	}
        	else
        	{
        		//we have the list of queues, return directly
        		return (String[]) parent;
        	}
        }
    }
    
    /**
     * Label Provider class for the routing key table viewer
     */
    private class LabelProviderImpl extends LabelProvider implements ITableLabelProvider
    {
    	String type;
    	
    	public LabelProviderImpl(String type)
    	{
    		this.type = type;
    	}
    	
        @Override
        public String getColumnText(Object element, int columnIndex)
        {
        	if(type.equals(ROUTING_KEY))
        	{
        		switch (columnIndex)
        		{
        			case 0 : // key column 
        				return String.valueOf(((CompositeDataSupport) element).get(ROUTING_KEY));
        			default :
        				return "";
        		}
        	}
        	else
        	{
        		switch (columnIndex)
        		{
        			case 0 : //queue name column 
        				return String.valueOf(element);
        			default :
        				return "";
        		}
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
    public class TableSorter extends ViewerSorter
    {
        private int column;
        private static final int ASCENDING = 0;
        private static final int DESCENDING = 1;

        private int direction = DESCENDING;
        
    	private String type;
    	
        public TableSorter(String type)
        {
        	this.type = type;
            this.column = 0;
            direction = ASCENDING;
        }

        public void setColumn(int column)
        {
            if (column == this.column)
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
            int comparison = 0;
            switch(column)
            {
                case 0:
                	if(type.equals(ROUTING_KEY))
                	{
                        CompositeData msg1 = (CompositeData) e1;
                        CompositeData msg2 = (CompositeData) e2;
                        	
                		comparison = ((String) msg1.get(ROUTING_KEY)).compareTo((String) msg2.get(ROUTING_KEY));
                	}
                	else
                	{
                		comparison = ((String)e1).compareTo((String) e2);
                	}
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
