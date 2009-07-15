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
package org.apache.qpid.management.ui.views.logging;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularDataSupport;

import static org.apache.qpid.management.ui.Constants.FONT_BOLD;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.common.mbeans.LoggingManagement;
import org.apache.qpid.management.ui.jmx.JMXManagedObject;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.views.TabControl;
import org.apache.qpid.management.ui.views.ViewUtility;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;


/**
 * Control class for the LoggingManagement mbean ConfigFile Options tab.
 */
public class ConfigurationFileTabControl extends TabControl
{
    private FormToolkit _toolkit;
    private ScrolledForm        _form;
    private Table _table = null;
    private TableViewer _tableViewer = null;
    private Composite _headerComposite = null;
    private Composite _paramsComposite = null;
        
    private Label _configFileRootLoggerLevelLabel = null;
    private Label _logWatchIntervalLabel = null;
    private String[] _availableLoggerLevels;
    private TabularDataSupport _configFileLoggerLevels = null;
    private LoggingManagement _lmmb;
    
    static final String LOGGER_NAME = LoggingManagement.COMPOSITE_ITEM_NAMES[0];
    static final String LOGGER_LEVEL = LoggingManagement.COMPOSITE_ITEM_NAMES[1];
    
    public ConfigurationFileTabControl(TabFolder tabFolder, JMXManagedObject mbean, MBeanServerConnection mbsc)
    {
        super(tabFolder);
        _mbean = mbean;
        _lmmb = (LoggingManagement)
                MBeanServerInvocationHandler.newProxyInstance(mbsc, mbean.getObjectName(),
                                                            LoggingManagement.class, false);
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createScrolledForm(_tabFolder);
        _form.getBody().setLayout(new GridLayout());
        createComposites();
        createWidgets();
    }
    
    private void createComposites()
    {

        _headerComposite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        _headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _headerComposite.setLayout(new GridLayout());

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
        _table.setFocus();
    }
    
    @Override
    public void refresh(ManagedBean mbean)
    {
        String configFileRootLoggerLevel = "-";
        try
        {
            configFileRootLoggerLevel = _lmmb.getConfigFileRootLoggerLevel();
        }
        catch (Exception e1)
        {
            MBeanUtility.handleException(_mbean, e1);
        }
        
        int log4jLogWatchInterval = -1;
        try
        {
            log4jLogWatchInterval = _lmmb.getLog4jLogWatchInterval();
        }
        catch (Exception e2)
        {
            MBeanUtility.handleException(_mbean, e2);
        }

        _configFileLoggerLevels = null;
        try
        {
            _configFileLoggerLevels = (TabularDataSupport) _lmmb.viewConfigFileLoggerLevels();
        }
        catch (Exception e3)
        {
            MBeanUtility.handleException(_mbean, e3);
        }
        
        _configFileRootLoggerLevelLabel.setText(String.valueOf(configFileRootLoggerLevel));
        if (log4jLogWatchInterval == 0)
        {
            _logWatchIntervalLabel.setText("Disabled (0 sec)");
        }
        else
        {
            _logWatchIntervalLabel.setText(String.valueOf(log4jLogWatchInterval) + " seconds");
        }
        _tableViewer.setInput(_configFileLoggerLevels);

        layout();
    }
    
    public void layout()
    {
        _form.layout(true);
        _form.getBody().layout(true, true);
    }
    
    private void createWidgets()
    {
        try
        {
            _availableLoggerLevels = _lmmb.getAvailableLoggerLevels();
        }
        catch(Exception e)
        {
            _availableLoggerLevels = new String[]{"ALL","TRACE","DEBUG","INFO","WARN","ERROR","FATAL","OFF"};
        }
        
        Label noteLabel = _toolkit.createLabel(_headerComposite, 
                "NOTE: These options modify the config file. " +
                "Changes take effect if LogWatch is enabled " +
                "or the broker is restarted.");
        GridData gridData = new GridData(SWT.FILL, SWT.FILL, false, true);
        noteLabel.setLayoutData(gridData);
        
        Group configFileLoggerLevelsGroup = new Group(_paramsComposite, SWT.SHADOW_NONE);
        configFileLoggerLevelsGroup.setBackground(_paramsComposite.getBackground());
        configFileLoggerLevelsGroup.setText("Configuration File Logger Levels");
        configFileLoggerLevelsGroup.setLayout(new GridLayout());
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        configFileLoggerLevelsGroup.setLayoutData(gridData);
        
        Composite tableComposite = _toolkit.createComposite(configFileLoggerLevelsGroup);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        gridData.heightHint = 250;
        gridData.minimumHeight = 250;
        tableComposite.setLayoutData(gridData);
        GridLayout gridLayout = new GridLayout();

        tableComposite.setLayout(gridLayout);
        
        _table = new Table (tableComposite, SWT.SINGLE | SWT.SCROLL_LINE | SWT.BORDER | SWT.FULL_SELECTION);
        _table.setLinesVisible (true);
        _table.setHeaderVisible (true);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        _table.setLayoutData(data);
        
        _tableViewer = new TableViewer(_table);
        final LoggingTableSorter tableSorter = new LoggingTableSorter();
        
        String[] titles = { LOGGER_NAME, LOGGER_LEVEL };
        int[] bounds = { 600, 75 };
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
        
        _tableViewer.setContentProvider(new LoggingTableContentProvider());
        _tableViewer.setLabelProvider(new LoggingTableLabelProvider());
        _tableViewer.setSorter(tableSorter);
        _table.setSortColumn(_table.getColumn(0));
        _table.setSortDirection(SWT.UP);
        _table.addMouseListener(new MouseListener()
        {
            public void mouseDoubleClick(MouseEvent event)
            {
                editLoggerLevel(_table.getShell());
            }

            public void mouseDown(MouseEvent e){}
            public void mouseUp(MouseEvent e){}
        });
        
        final Button logLevelEditButton = _toolkit.createButton(configFileLoggerLevelsGroup, "Edit Selected Logger...", SWT.PUSH);
        logLevelEditButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        logLevelEditButton.setEnabled(false);
        logLevelEditButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                editLoggerLevel(logLevelEditButton.getShell());
            }
        });
        
        _tableViewer.addSelectionChangedListener(new ISelectionChangedListener(){
            public void selectionChanged(SelectionChangedEvent evt)
            {
                int selectionIndex = _table.getSelectionIndex();

                if(selectionIndex != -1)
                {
                    logLevelEditButton.setEnabled(true);
                }
                else
                {
                    logLevelEditButton.setEnabled(false);
                }
            }
        });
        
        
        Composite attributesComposite = _toolkit.createComposite(_paramsComposite);
        gridData = new GridData(SWT.LEFT, SWT.FILL, false, true);
        attributesComposite.setLayoutData(gridData);
        gridLayout = new GridLayout(3,false);
        attributesComposite.setLayout(gridLayout);
        
        Group configFileRootLoggerLevelGroup = new Group(attributesComposite, SWT.SHADOW_NONE);
        configFileRootLoggerLevelGroup.setBackground(attributesComposite.getBackground());
        configFileRootLoggerLevelGroup.setText("Config File RootLogger Level");
        gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        configFileRootLoggerLevelGroup.setLayoutData(gridData);
        configFileRootLoggerLevelGroup.setLayout(new GridLayout(2,false));
        
        _configFileRootLoggerLevelLabel = _toolkit.createLabel(configFileRootLoggerLevelGroup, "-");
        _configFileRootLoggerLevelLabel.setFont(ApplicationRegistry.getFont(FONT_BOLD));
        _configFileRootLoggerLevelLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        
        final Button configFileRootLoggerLevelButton = _toolkit.createButton(configFileRootLoggerLevelGroup, "Edit ...", SWT.PUSH);
        configFileRootLoggerLevelButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        configFileRootLoggerLevelButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                editRootLoggerLevel(configFileRootLoggerLevelButton.getShell());
            }
        });
        
        
        Group logWatchIntervalGroup = new Group(attributesComposite, SWT.SHADOW_NONE);
        logWatchIntervalGroup.setBackground(attributesComposite.getBackground());
        logWatchIntervalGroup.setText("LogWatch Interval");
        gridData = new GridData(SWT.LEFT, SWT.FILL, true, false);
        logWatchIntervalGroup.setLayoutData(gridData);
        logWatchIntervalGroup.setLayout(new GridLayout());

        _logWatchIntervalLabel = _toolkit.createLabel(logWatchIntervalGroup, "-");
        _logWatchIntervalLabel.setFont(ApplicationRegistry.getFont(FONT_BOLD));
        _logWatchIntervalLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true));
    }


    
    private void editLoggerLevel(Shell parent)
    {
        int selectionIndex = _table.getSelectionIndex();

        if (selectionIndex != -1)
        {
            final CompositeData selectedLogger = (CompositeData)_table.getItem(selectionIndex).getData();
            String loggerName = selectedLogger.get(LOGGER_NAME).toString();

            final Shell shell = ViewUtility.createModalDialogShell(parent, "Set Config File Logger Level");
 
            Composite loggerComp = _toolkit.createComposite(shell);
            loggerComp.setBackground(shell.getBackground());
            loggerComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            loggerComp.setLayout(new GridLayout(2,false));

            _toolkit.createLabel(loggerComp, "Logger: ").setBackground(shell.getBackground());
            _toolkit.createLabel(loggerComp, loggerName).setBackground(shell.getBackground());

            Composite levelComp = _toolkit.createComposite(shell);
            levelComp.setBackground(shell.getBackground());
            levelComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            levelComp.setLayout(new GridLayout(2,false));
            
            _toolkit.createLabel(levelComp,"Level: ").setBackground(levelComp.getBackground());
            final Combo levelCombo = new Combo (levelComp, SWT.READ_ONLY );
            levelCombo.setItems(_availableLoggerLevels);
            levelCombo.select(0);

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
                    String logger = selectedLogger.get(LOGGER_NAME).toString(); 
                    String level = levelCombo.getItem(levelCombo.getSelectionIndex()).toString();
                    
                    shell.close();

                    try
                    {
                        boolean result = _lmmb.setConfigFileLoggerLevel(logger, level);
                        ViewUtility.operationResultFeedback(result, 
                                "Updated ConfigFile Logger Level", "Failed to update ConfigFile Logger Level");
                    }
                    catch (Exception e4)
                    {
                        MBeanUtility.handleException(_mbean, e4);
                        ViewUtility.operationFailedStatusBarMessage("Error updating ConfigFile Logger Level");
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
            
            shell.pack();
            shell.open();
        }
    }
    
    private void editRootLoggerLevel(Shell parent)
    {
        final Shell shell = ViewUtility.createModalDialogShell(parent, "ConfigFile RootLogger Level");

        Composite levelComp = _toolkit.createComposite(shell);
        levelComp.setBackground(shell.getBackground());
        levelComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        levelComp.setLayout(new GridLayout(2,false));
        
        _toolkit.createLabel(levelComp,"RootLogger level: ").setBackground(levelComp.getBackground());
        final Combo levelCombo = new Combo (levelComp, SWT.READ_ONLY );
        levelCombo.setItems(_availableLoggerLevels);
        levelCombo.select(0);

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
                String selection = levelCombo.getItem(levelCombo.getSelectionIndex()).toString();
                shell.dispose();
                try
                {
                    boolean result = _lmmb.setConfigFileRootLoggerLevel(selection);
                    ViewUtility.operationResultFeedback(result, 
                            "Updated ConfigFile RootLogger Level", "Failed to update ConfigFile RootLogger Level");
                }
                catch (Exception e5)
                {
                    ViewUtility.operationFailedStatusBarMessage("Error updating ConfigFile RootLogger Level");
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
