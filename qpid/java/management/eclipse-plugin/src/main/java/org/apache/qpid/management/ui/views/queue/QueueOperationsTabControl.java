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
package org.apache.qpid.management.ui.views.queue;

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
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.management.ui.jmx.JMXManagedObject;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;


/**
 * Control class for the Queue mbean Operations tab.
 */
public class QueueOperationsTabControl extends TabControl
{
    private FormToolkit _toolkit;
    private Form        _form;
    private Table _table = null;
    private TableViewer _tableViewer = null;
    private Composite _paramsComposite = null;
            
    private TabularDataSupport _messages = null;
    private ManagedQueue _qmb;
    
    static final String MSG_AMQ_ID = ManagedQueue.VIEW_MSGS_COMPOSITE_ITEM_NAMES[0];
    static final String MSG_HEADER = ManagedQueue.VIEW_MSGS_COMPOSITE_ITEM_NAMES[1];
    static final String MSG_SIZE = ManagedQueue.VIEW_MSGS_COMPOSITE_ITEM_NAMES[2];
    static final String MSG_REDELIVERED = ManagedQueue.VIEW_MSGS_COMPOSITE_ITEM_NAMES[3];
    
    public QueueOperationsTabControl(TabFolder tabFolder, JMXManagedObject mbean, MBeanServerConnection mbsc)
    {
        super(tabFolder);
        _mbean = mbean;
        _qmb = (ManagedQueue) MBeanServerInvocationHandler.newProxyInstance(mbsc, 
                                mbean.getObjectName(), ManagedQueue.class, false);
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
        
        _messages = null;
        try
        {
            //gather a list of all messages on the queue for display and selection
            _messages = (TabularDataSupport) _qmb.viewMessages(1,Integer.MAX_VALUE);
            
            //TODO - viewMessages takes int args, limiting number of messages which can be viewed
            //to the first 2^32 messages on the queue at the time of invocation.
            //For consistency with other methods, expand values to Long by introducing a new method.
            //Use AMQ ID or current 'position in queue' numbering scheme ??
        }
        catch (Exception e)
        {
            MBeanUtility.handleException(mbean,e);
        }
        
        _form.setVisible(false);
        _tableViewer.setInput(_messages);
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
        Group viewMessagesGroup = new Group(_paramsComposite, SWT.SHADOW_NONE);
        viewMessagesGroup.setBackground(_paramsComposite.getBackground());
        viewMessagesGroup.setText("View Messages");
        viewMessagesGroup.setLayout(new GridLayout(2,false));
        GridData gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        viewMessagesGroup.setLayoutData(gridData);
               
        _table = new Table (viewMessagesGroup, SWT.SINGLE | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _table.setLinesVisible (true);
        _table.setHeaderVisible (true);
        GridData data = new GridData(SWT.LEFT, SWT.TOP, true, true);
        data.heightHint = 325;
        _table.setLayoutData(data);
        
        _tableViewer = new TableViewer(_table);
        final TableSorter tableSorter = new TableSorter();
        
        String[] titles = {"AMQ ID", "Size(bytes)"};
        int[] bounds = { 110, 110 };
        for (int i = 0; i < titles.length; i++) 
        {
            final int index = i;
            final TableColumn column = new TableColumn (_table, SWT.NONE);

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
        
        Composite rightComposite = _toolkit.createComposite(viewMessagesGroup);
        gridData = new GridData(SWT.LEFT, SWT.FILL, true, false);
        gridData.heightHint = 325;
        rightComposite.setLayoutData(gridData);
        rightComposite.setLayout(new GridLayout(2,false));
        
        final Text headerText = new Text(rightComposite,SWT.WRAP | SWT.BORDER );
        headerText.setText("Select a message to view its header.");
        headerText.setEditable(false);
        data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.widthHint = 440;
        data.heightHint = 300;
        data.horizontalSpan = 2;
        headerText.setLayoutData(data);
        
        Composite redeliveryComposite = _toolkit.createComposite(rightComposite);
        redeliveryComposite.setLayout(new GridLayout(2,false));
        data = new GridData(SWT.LEFT, SWT.FILL, false, false);
        data.widthHint = 150;
        redeliveryComposite.setLayoutData(data);
        
        _toolkit.createLabel(redeliveryComposite, "Redelivered: ");
        final Text redeliveredText = new Text(redeliveryComposite, SWT.BORDER);
        redeliveredText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        redeliveredText.setText("-");
        redeliveredText.setEditable(false);

        final Button viewSelectedMsgButton = _toolkit.createButton(rightComposite, "View Selected Message Contents ...", SWT.PUSH);
        viewSelectedMsgButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        viewSelectedMsgButton.setEnabled(false);
        viewSelectedMsgButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                viewMessageContent();
            }
        });
        
        _table.addMouseListener(new MouseListener()                                              
        {
            // MouseListener implementation
            public void mouseDoubleClick(MouseEvent event)
            {
                viewMessageContent();
            }

            public void mouseDown(MouseEvent e){}
            public void mouseUp(MouseEvent e){}
        });
        
        _tableViewer.addSelectionChangedListener(new ISelectionChangedListener(){
            public void selectionChanged(SelectionChangedEvent evt)
            {
                int selectionIndex = _table.getSelectionIndex();

                if (selectionIndex != -1)
                {
                    viewSelectedMsgButton.setEnabled(true);
                    
                    final CompositeData selectedMsg = (CompositeData)_table.getItem(selectionIndex).getData();
                    
                    Boolean redelivered = (Boolean) selectedMsg.get(MSG_REDELIVERED);
                    redeliveredText.setText(redelivered.toString());
                    
                    String[] msgHeader = (String[]) selectedMsg.get(MSG_HEADER);
                    headerText.setText("");
                    for(String s: msgHeader)
                    {
                        headerText.append(s + "\n");
                    }
                }
                else
                {
                    headerText.setText("Select a message to view its header.");
                    redeliveredText.setText("-");
                    viewSelectedMsgButton.setEnabled(false);
                }
            }
        });
        
        Composite opsComposite = _toolkit.createComposite(_paramsComposite);
        gridData = new GridData(SWT.LEFT, SWT.FILL, false, true);
        opsComposite.setLayoutData(gridData);
        opsComposite.setLayout(new GridLayout(3,false));
        
        Group moveMessagesGroup = new Group(opsComposite, SWT.SHADOW_NONE);
        moveMessagesGroup.setBackground(opsComposite.getBackground());
        moveMessagesGroup.setText("Move Messages");
        gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        moveMessagesGroup.setLayoutData(gridData);
        moveMessagesGroup.setLayout(new GridLayout(2,false));
        
        final Button moveMessagesButton = _toolkit.createButton(moveMessagesGroup, "Move Message(s) ...", SWT.PUSH);
        moveMessagesButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                moveMessages(moveMessagesButton.getShell());
            }
        });
        
        
        Group deleteMessagesGroup = new Group(opsComposite, SWT.SHADOW_NONE);
        deleteMessagesGroup.setBackground(opsComposite.getBackground());
        deleteMessagesGroup.setText("Delete Messages");
        gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        deleteMessagesGroup.setLayoutData(gridData);
        deleteMessagesGroup.setLayout(new GridLayout(2, false));

        final Button deleteFromTopButton = _toolkit.createButton(deleteMessagesGroup, "Delete Message From Top of Queue", SWT.PUSH);
        deleteFromTopButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                int response = ViewUtility.popupOkCancelConfirmationMessage("Delete From Top", 
                        "Delete message from top of queue?");
                if (response == SWT.OK)
                {
                    try
                    {
                        _qmb.deleteMessageFromTop();
                    }
                    catch (Exception e1)
                    {
                        MBeanUtility.handleException(_mbean, e1);
                    }
                    //TODO:display result
                    refresh(_mbean);;
                }
            }
        });
        
        final Button clearQueueButton = _toolkit.createButton(deleteMessagesGroup, "Clear Queue", SWT.PUSH);
        clearQueueButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                int response = ViewUtility.popupOkCancelConfirmationMessage("Clear Queue", 
                        "Clear queue ?");
                if (response == SWT.OK)
                {
                    try
                    {
                        _qmb.clearQueue();
                    }
                    catch (Exception e2)
                    {
                        MBeanUtility.handleException(_mbean, e2);
                    }
                    //TODO:display result
                    refresh(_mbean);;
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
                case 0 : // msg id column 
                    return String.valueOf(((CompositeDataSupport) element).get(MSG_AMQ_ID));
                case 1 : // msg size column 
                    return String.valueOf(((CompositeDataSupport) element).get(MSG_SIZE));
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
    public class TableSorter extends ViewerSorter
    {
        private int column;
        private static final int ASCENDING = 0;
        private static final int DESCENDING = 1;

        private int direction = DESCENDING;

        public TableSorter()
        {
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
            CompositeData msg1 = (CompositeData) e1;
            CompositeData msg2 = (CompositeData) e2;
            
            int comparison = 0;
            switch(column)
            {
                case 0:
                    comparison = ((Long) msg1.get(MSG_AMQ_ID)).compareTo((Long)msg2.get(MSG_AMQ_ID));
                    break;
                case 1:
                    comparison = ((Long) msg1.get(MSG_SIZE)).compareTo((Long)msg2.get(MSG_SIZE));
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
    
    private void viewMessageContent()
    {
        int selectionIndex = _table.getSelectionIndex();

        if (selectionIndex != -1)
        {
            try
            {
                final CompositeData selectedMsg = (CompositeData)_table.getItem(selectionIndex).getData();
                Long msgId = (Long) selectedMsg.get(MSG_AMQ_ID);

                Object result = _qmb.viewMessageContent(msgId);

                populateResults(result);
            }
            catch (Exception e3)
            {
                MBeanUtility.handleException(_mbean, e3);
            }
        }
    }
    
    private void populateResults(Object result)
    {
        Display display = Display.getCurrent();
        int width = 610;
        int height = 400;
        Shell shell = ViewUtility.createPopupShell(RESULT, width, height);
        shell.setImage(ApplicationRegistry.getImage(CONSOLE_IMAGE));
        ViewUtility.populateCompositeWithData(_toolkit, shell, result);
        
        shell.open();
        while (!shell.isDisposed())
        {
            if (!display.readAndDispatch())
            {
                display.sleep();
            }
        }
        shell.dispose();
    }
    
    private void moveMessages(final Shell parent)
    {
        final Shell shell = ViewUtility.createModalDialogShell(parent, "Move Messages");
        
        Composite idComposite = _toolkit.createComposite(shell, SWT.NONE);
        idComposite.setBackground(shell.getBackground());
        idComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        idComposite.setLayout(new GridLayout(4,false));
        
        _toolkit.createLabel(idComposite,"From AMQ ID:").setBackground(shell.getBackground());
        final Text fromText = new Text(idComposite, SWT.BORDER);
        fromText.addVerifyListener(new NumberVerifyListener());
        fromText.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
        
        _toolkit.createLabel(idComposite,"To AMQ ID:").setBackground(shell.getBackground());
        final Text toText = new Text(idComposite, SWT.BORDER);
        toText.addVerifyListener(new NumberVerifyListener());
        toText.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));

        Composite destinationComposite = _toolkit.createComposite(shell, SWT.NONE);
        destinationComposite.setBackground(shell.getBackground());
        destinationComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        destinationComposite.setLayout(new GridLayout(2,false));
        
        _toolkit.createLabel(destinationComposite,"To Queue:").setBackground(shell.getBackground());
        final Combo destinationCombo = new Combo(destinationComposite,SWT.NONE);
        destinationCombo.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        
        Composite okCancelButtonsComp = _toolkit.createComposite(shell);
        okCancelButtonsComp.setBackground(shell.getBackground());
        okCancelButtonsComp.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, true, true));
        okCancelButtonsComp.setLayout(new GridLayout(2,false));
        
        Button okButton = _toolkit.createButton(okCancelButtonsComp, "OK", SWT.PUSH);
        okButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
        Button cancelButton = _toolkit.createButton(okCancelButtonsComp, "Cancel", SWT.PUSH);
        cancelButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
        
        List<String> queueList = ApplicationRegistry.getServerRegistry(_mbean).getQueueNames(_mbean.getVirtualHostName());
        queueList.remove(_mbean.getName());
        
        if(queueList.size() == 0)
        {
            destinationCombo.setItems(new String[]{"No other queues available"});
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
                String from = fromText.getText();
                String to = toText.getText();
                String destQueue = destinationCombo.getItem(destinationCombo.getSelectionIndex()).toString();
                
                if (from == null || from.length() == 0)
                {                            
                    ViewUtility.popupErrorMessage("Move Messages", "Please enter a valid 'from' ID");
                    return;
                }
                if (Long.valueOf(from) == 0)
                {
                    ViewUtility.popupErrorMessage("Move Messages", "Enter a 'from' ID greater than 0");
                    return;
                }
                
                if (to == null || to.length() == 0)
                {                            
                    ViewUtility.popupErrorMessage("Move Messages", "Please enter a valid 'to' ID");
                    return;
                }
                
                if (Long.valueOf(to) == 0)
                {
                    ViewUtility.popupErrorMessage("Move Messages", "Enter a 'to' ID greater than 0");
                    return;
                }
                                
                shell.dispose();

                try
                {
                    _qmb.moveMessages(Long.valueOf(from), Long.valueOf(to), destQueue);
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
