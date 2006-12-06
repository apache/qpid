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
package org.apache.qpid.management.ui.actions;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ApplicationWorkbenchAdvisor;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.exceptions.InfoRequiredException;
import org.apache.qpid.management.ui.views.NavigationView;
import org.apache.qpid.management.ui.views.ViewUtility;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

public class AddServer/* extends Action*/ implements IWorkbenchWindowActionDelegate 
{
	private IWorkbenchWindow _window;
    private static final String[] _connectionTypes ={"RMI"};
    private static final String[] _domains ={"org.apache.qpid"};
	
    public AddServer()
    {
        
    }
    
    /*
	public AddServer(IWorkbenchWindow window)//, String label)
	{
		_window = window;
        //setText(label);
        // The id is used to refer to the action in a menu or toolbar
		setId(ICommandIds.CMD_ADD_SERVER);
        // Associate the action with a pre-defined command, to allow key bindings.
		setActionDefinitionId(ICommandIds.CMD_ADD_SERVER);
		//setImageDescriptor(org.apache.qpid.management.ui.Activator.getImageDescriptor("/icons/add.gif"));
	}
	*/
    
    public void run(IAction action)
    {
        if(_window != null)
        {   
            try
            {
                // TODO
                //_window.getActivePage().showView(NavigationView.ID, Integer.toString(0), IWorkbenchPage.VIEW_ACTIVATE);
                //_window.getActivePage().showView(MBeanView.ID, Integer.toString(0), IWorkbenchPage.VIEW_ACTIVATE);
            }
            catch (Exception ex)
            {
                
            }
            createWidgets();
        }
    }
    
    /**
     * Selection in the workbench has been changed. We 
     * can change the state of the 'real' action here
     * if we want, but this can only happen after 
     * the delegate has been created.
     * @see IWorkbenchWindowActionDelegate#selectionChanged
     */
    public void selectionChanged(IAction action, ISelection selection) {
    }

    /**
     * We can use this method to dispose of any system
     * resources we previously allocated.
     * @see IWorkbenchWindowActionDelegate#dispose
     */
    public void dispose() {
    }

    /**
     * We will cache window object in order to
     * be able to provide parent shell for the message dialog.
     * @see IWorkbenchWindowActionDelegate#init
     */
    public void init(IWorkbenchWindow window) {
        this._window = window;
    }
    
    
    /*
	public void run()
    {
		if(_window != null)
        {	
            createWidgets();
		}
	}
    */
    private void createWidgets()
    {
        Display display = Display.getCurrent();
        final Shell shell = new Shell(display, SWT.BORDER | SWT.CLOSE);
        shell.setText(Constants.ACTION_ADDSERVER);
        shell.setLayout(new GridLayout());
        
        int x = display.getBounds().width;
        int y = display.getBounds().height;
        shell.setBounds(x/4, y/4, 425, 250);

        Composite composite = new Composite(shell, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 10;
        layout.marginHeight = 20;
        layout.marginWidth = 20;
        composite.setLayout(layout);
        
        Label name = new Label(composite, SWT.NONE);
        name.setText("Connection Type");
        GridData layoutData = new GridData(SWT.TRAIL, SWT.TOP, false, false);
        name.setLayoutData(layoutData);
        
        final Combo comboTransport = new Combo(composite, SWT.READ_ONLY);
        comboTransport.setItems(_connectionTypes);
        comboTransport.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        comboTransport.select(0);
        
        Label host = new Label(composite, SWT.NONE);
        host.setText("Host");
        host.setLayoutData(new GridData(SWT.TRAIL, SWT.TOP, false, false));
        
        final Text textHost = new Text(composite, SWT.BORDER);
        textHost.setText("");
        textHost.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        textHost.setFocus();
        textHost.addVerifyListener(new VerifyListener(){
                public void verifyText(VerifyEvent event)
                {
                    if (!(Character.isLetterOrDigit(event.character) ||
                          (event.character == '.') ||
                          (event.character == '\b') ))
                    {
                        event.doit = false;
                    }
                }
            });
        
        
        Label port = new Label(composite, SWT.NONE);
        port.setText("Port");
        port.setLayoutData(new GridData(SWT.TRAIL, SWT.TOP, false, false));
        
        final Text textPort = new Text(composite, SWT.BORDER);
        textPort.setText("");
        textPort.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        textPort.addVerifyListener(new VerifyListener(){
                public void verifyText(VerifyEvent event)
                {
                    if (textPort.getText().length() == 4)
                        event.doit = false;                        
                    else if (!(Character.isDigit(event.character) ||
                         (event.character == '\b')))
                    {
                        event.doit = false;
                    }
                }
            });
        
        
        Label domain = new Label(composite, SWT.NONE);
        domain.setText("Domain");
        domain.setLayoutData(new GridData(SWT.TRAIL, SWT.TOP, false, false));
        
        final Combo comboDomain = new Combo(composite, SWT.DROP_DOWN | SWT.READ_ONLY);
        comboDomain.setItems(_domains);
        comboDomain.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        comboDomain.select(0);
        
        Composite buttonsComposite  = new Composite(composite, SWT.NONE);
        buttonsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        buttonsComposite.setLayout(new GridLayout(2, true));
        
        
        final Button connectButton = new Button(buttonsComposite, SWT.PUSH | SWT.CENTER);       
        connectButton.setText(Constants.BUTTON_CONNECT);
        GridData gridData = new GridData (SWT.TRAIL, SWT.BOTTOM, true, true);
        gridData.widthHint = 100;
        connectButton.setLayoutData(gridData);
        connectButton.setFont(ApplicationRegistry.getFont(Constants.FONT_BUTTON));
        connectButton.addSelectionListener(new SelectionAdapter(){
                public void widgetSelected(SelectionEvent event)
                {
                    String transport = comboTransport.getText();
                    String host = textHost.getText();
                    String port = textPort.getText();
                    String domain = comboDomain.getText();
                    
                    NavigationView view = (NavigationView)_window.getActivePage().findView(NavigationView.ID);
                    try
                    {
                        view.addNewServer(transport, host, port, domain);
                        
                        if (!connectButton.getShell().isDisposed())
                            connectButton.getShell().dispose();
                    }
                    catch(InfoRequiredException ex)
                    {
                        ViewUtility.popupInfoMessage("New connection", ex.getMessage());
                    }
                    catch(Exception ex)
                    {
                        IStatus status = new Status(IStatus.ERROR, ApplicationWorkbenchAdvisor.PERSPECTIVE_ID,
                                                    IStatus.OK, ex.getMessage(), ex.getCause()); 
                        ErrorDialog.openError(shell, "Error", "Server could not be added", status);
                    }                                        
                }
            });
        
        final Button cancelButton = new Button(buttonsComposite, SWT.PUSH);
        cancelButton.setText(Constants.BUTTON_CANCEL);
        gridData = new GridData (SWT.LEAD, SWT.BOTTOM, true, true);
        gridData.widthHint = 100;
        cancelButton.setLayoutData(gridData);
        cancelButton.setFont(ApplicationRegistry.getFont(Constants.FONT_BUTTON));
        cancelButton.addSelectionListener(new SelectionAdapter(){
                public void widgetSelected(SelectionEvent event)
                {
                    shell.dispose();
                }
            });
        
        shell.open();
        _window.getShell().setEnabled(false);
        while (!shell.isDisposed())
        {   
            if (!display.readAndDispatch())
            {
                display.sleep();
            }
        }
        
        //If you create it, you dispose it.
        shell.dispose();
        
        // enable the main shell
        _window.getShell().setEnabled(true);
        _window.getShell().open();
    }

}
