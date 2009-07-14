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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;


/**
 * Control class for the Headers Exchange mbean Operations tab.
 */
public class HeadersExchangeOperationsTabControl extends TabControl
{
    private FormToolkit _toolkit;
    private ScrolledForm        _form;
    private Table _bindingNumberTable = null;
    private TableViewer _bindingNumberTableViewer = null;
    private Table _headersTable = null;
    private TableViewer _headersTableViewer = null;
    private Composite _paramsComposite = null;
            
    private TabularDataSupport _bindings = null;
    private ManagedExchange _emb;
    
    static final String BINDING_NUM = ManagedExchange.HEADERS_COMPOSITE_ITEM_NAMES[0];
    static final String QUEUE_NAME = ManagedExchange.HEADERS_COMPOSITE_ITEM_NAMES[1];
    static final String QUEUE_BINDINGS = ManagedExchange.HEADERS_COMPOSITE_ITEM_NAMES[2];
    
    public HeadersExchangeOperationsTabControl(TabFolder tabFolder, JMXManagedObject mbean, MBeanServerConnection mbsc)
    {
        super(tabFolder);
        _mbean = mbean;
        _emb = (ManagedExchange) MBeanServerInvocationHandler.newProxyInstance(mbsc, 
                                mbean.getObjectName(), ManagedExchange.class, false);
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createScrolledForm(_tabFolder);
        _form.getBody().setLayout(new GridLayout());
        createComposites();
        createWidgets();
    }
    
    private void createComposites()
    {
        _paramsComposite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        _paramsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        _paramsComposite.setLayout(new GridLayout());
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
        _bindingNumberTable.setFocus();
    }
    
    @Override
    public void refresh(ManagedBean mbean)
    {

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

        _bindingNumberTableViewer.setInput(_bindings);

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
        GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        bindingsGroup.setLayoutData(gridData);
        
        Composite tablesComposite = _toolkit.createComposite(bindingsGroup);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        gridData.minimumHeight = 250;
        gridData.heightHint = 250;
        tablesComposite.setLayoutData(gridData);
        tablesComposite.setLayout(new GridLayout(2,false));
        
        //table of bindings for the exchange
        _bindingNumberTable = new Table (tablesComposite, SWT.SINGLE | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _bindingNumberTable.setLinesVisible(true);
        _bindingNumberTable.setHeaderVisible(true);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.minimumHeight = 300;
        data.heightHint = 300;
        _bindingNumberTable.setLayoutData(data);
        
        _bindingNumberTableViewer = new TableViewer(_bindingNumberTable);
        final TableSorter tableSorter = new TableSorter(BINDING_NUM);
        
        String[] titles = {"Binding Number", "Queue Name"};
        int[] bounds = {125, 175};
        for (int i = 0; i < titles.length; i++) 
        {
            final int index = i;
            final TableColumn column = new TableColumn (_bindingNumberTable, SWT.NONE);

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
                    final TableViewer viewer = _bindingNumberTableViewer;
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
        
        _bindingNumberTableViewer.setContentProvider(new ContentProviderImpl(BINDING_NUM));
        _bindingNumberTableViewer.setLabelProvider(new LabelProviderImpl(BINDING_NUM));
        _bindingNumberTableViewer.setSorter(tableSorter);
        
        //table of header bindings
        _headersTable = new Table (tablesComposite, SWT.SINGLE | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _headersTable.setLinesVisible (true);
        _headersTable.setHeaderVisible (true);
        data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.minimumHeight = 300;
        data.heightHint = 300;
        _headersTable.setLayoutData(data);
        
        _headersTableViewer = new TableViewer(_headersTable);
        final TableSorter queuesTableSorter = new TableSorter(QUEUE_BINDINGS);
        
        titles = new String[]{"Header Bindings"};
        bounds = new int[]{225};
        for (int i = 0; i < titles.length; i++) 
        {
            final int index = i;
            final TableColumn column = new TableColumn (_headersTable, SWT.NONE);

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
                    final TableViewer viewer = _headersTableViewer;
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
        
        _headersTableViewer.setContentProvider(new ContentProviderImpl(QUEUE_BINDINGS));
        _headersTableViewer.setLabelProvider(new LabelProviderImpl(QUEUE_BINDINGS));
        _headersTableViewer.setSorter(queuesTableSorter);
        _headersTableViewer.setInput(new String[]{"Select a binding to view key-value pairs"});
        
        _bindingNumberTableViewer.addSelectionChangedListener(new ISelectionChangedListener(){
            public void selectionChanged(SelectionChangedEvent evt)
            {
                int selectionIndex = _bindingNumberTable.getSelectionIndex();

                if (selectionIndex != -1)
                {
                	final CompositeData selectedMsg = (CompositeData)_bindingNumberTable.getItem(selectionIndex).getData();

                	String[] bindings = (String[]) selectedMsg.get(QUEUE_BINDINGS);
                	_headersTableViewer.setInput(bindings);
                }
                else
                {
                	_headersTableViewer.setInput(new String[]{"Select a binding to view key-value pairs"});
                }
            }
        });
        
        //Side Buttons
        Composite buttonsComposite = _toolkit.createComposite(bindingsGroup);
        gridData = new GridData(SWT.FILL, SWT.FILL, false, true);
        buttonsComposite.setLayoutData(gridData);
        buttonsComposite.setLayout(new GridLayout());

        final Button createBindingButton = _toolkit.createButton(buttonsComposite, "Create ...", SWT.PUSH);
        createBindingButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                createNewBinding(createBindingButton.getShell());
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
        	if(type.equals(BINDING_NUM))
        	{
        		Collection<Object> rowCollection = ((TabularDataSupport) parent).values();

        		return rowCollection.toArray();
        	}
        	else
        	{
        		//we have the list of bindings, return directly
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
        	if(type.equals(BINDING_NUM)) //binding num and queue name table
        	{
        		switch (columnIndex)
        		{
        			case 0 : // binding number column 
        				return String.valueOf(((CompositeDataSupport) element).get(BINDING_NUM));
                    case 1 : // queue name column 
                        return (String) ((CompositeDataSupport) element).get(QUEUE_NAME);
        			default :
        				return "";
        		}
        	}
        	else //binding key-value pair table
        	{
        		switch (columnIndex)
        		{
        			case 0 : //header binding column 
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

            if(type.equals(BINDING_NUM)) //binding num and queue name table
            {
                CompositeData binding1 = (CompositeData) e1;
                CompositeData binding2 = (CompositeData) e2;
                
                switch(column)
                {
                    case 0: // binding number column 
                        comparison = ((Integer) binding1.get(BINDING_NUM)).compareTo((Integer) binding2.get(BINDING_NUM));
                        break;
                    case 1: // queue name column 
                        comparison = ((String) binding1.get(QUEUE_NAME)).compareTo((String) binding2.get(QUEUE_NAME));
                        break;
                    default:
                        comparison = 0;
                }
            }
            else  //binding key-value pair table
            {
                switch(column)
                {
                    case 0: //header binding column 
                        comparison = ((String)e1).compareTo((String) e2);
                        break;
                    default:
                        comparison = 0;
                }
            }

            // If descending order, flip the direction
            if(direction == DESCENDING)
            {
                comparison = -comparison;
            }
            return comparison;
        }
    }
    
    private void createNewBinding(Shell parent)
    {
        final Shell shell = ViewUtility.createModalDialogShell(parent, "Create New Binding");

        Composite destinationComposite = _toolkit.createComposite(shell, SWT.NONE);
        destinationComposite.setBackground(shell.getBackground());
        destinationComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        destinationComposite.setLayout(new GridLayout(2,false));
        
        _toolkit.createLabel(destinationComposite,"Queue:").setBackground(shell.getBackground());
        final Combo destinationCombo = new Combo(destinationComposite,SWT.NONE | SWT.READ_ONLY);
        destinationCombo.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        
        Composite bindingComposite = _toolkit.createComposite(shell, SWT.NONE);
        bindingComposite.setBackground(shell.getBackground());
        bindingComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        bindingComposite.setLayout(new GridLayout(2,false));
        
        _toolkit.createLabel(bindingComposite,"Binding:").setBackground(shell.getBackground());
        final Text bindingText = new Text(bindingComposite, SWT.BORDER);
        bindingText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Composite okCancelButtonsComp = _toolkit.createComposite(shell);
        okCancelButtonsComp.setBackground(shell.getBackground());
        okCancelButtonsComp.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, true, true));
        okCancelButtonsComp.setLayout(new GridLayout(2,false));
        
        Button okButton = _toolkit.createButton(okCancelButtonsComp, "OK", SWT.PUSH);
        okButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
        Button cancelButton = _toolkit.createButton(okCancelButtonsComp, "Cancel", SWT.PUSH);
        cancelButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
        
        List<String> queueList = ApplicationRegistry.getServerRegistry(_mbean).getQueueNames(_mbean.getVirtualHostName());
        
        if(queueList.size() == 0)
        {
            destinationCombo.setItems(new String[]{"No queues available"});
            okButton.setEnabled(false);
        }
        else
        {
            destinationCombo.setItems(queueList.toArray(new String[0]));
        }
        destinationCombo.select(0);
        
        okButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                String binding = bindingText.getText();
                
                if (binding == null || binding.length() == 0)
                {                            
                    ViewUtility.popupErrorMessage("Create New Binding", "Please enter a valid binding");
                    return;
                }
                
                String destQueue = destinationCombo.getItem(destinationCombo.getSelectionIndex()).toString();
                
                shell.dispose();

                try
                {
                    _emb.createNewBinding(destQueue, binding);
                }
                catch (Exception e4)
                {
                    MBeanUtility.handleException(_mbean, e4);
                }
                //TODO: display result?

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
