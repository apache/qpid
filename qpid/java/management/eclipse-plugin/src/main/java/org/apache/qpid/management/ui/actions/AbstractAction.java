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

import static org.apache.qpid.management.ui.Constants.ERROR_SERVER_CONNECTION;

import java.io.IOException;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ApplicationWorkbenchAdvisor;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.views.NavigationView;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

public class AbstractAction
{
    private NavigationView _navigationView;
    
    protected IWorkbenchWindow _window;
    
    public static final String SECURITY_FAILURE = "User authentication failed";   
    public static final String SERVER_UNAVAILABLE = "Unable to connect to the specified Qpid JMX server";
    public static final String INVALID_PERSPECTIVE = "Invalid Perspective";
    public static final String CHANGE_PERSPECTIVE = "Please use the Qpid Management Perspective";
      
    /**
     * We will cache window object in order to
     * be able to provide parent shell for the message dialog.
     * @see IWorkbenchWindowActionDelegate#init
     */
    public void init(IWorkbenchWindow window)
    {
        this._window = window;
        if (_window.getShell() != null)
        {
            _window.getShell().setImage(ApplicationRegistry.getImage(Constants.CONSOLE_IMAGE));
        }
    }   

    protected NavigationView getNavigationView()
    {
        if (_navigationView == null)
        {
            _navigationView = (NavigationView)_window.getActivePage().findView(NavigationView.ID);
        }
        
        return _navigationView;
    }
    
    protected void handleException(Throwable ex, String title, String msg)
    {
        //ensure first that the exception is not due to running in the wrong eclipse perspective
        NavigationView view = (NavigationView)_window.getActivePage().findView(NavigationView.ID);
        if (view == null)
        {
            IStatus status = new Status(IStatus.WARNING, ApplicationWorkbenchAdvisor.PERSPECTIVE_ID,
                    IStatus.OK, CHANGE_PERSPECTIVE, null); 
            ErrorDialog.openError(_window.getShell(), "Warning", INVALID_PERSPECTIVE, status);
            return;
        }

        //default title if none given
        if (title == null)
        {
            title = ERROR_SERVER_CONNECTION;
        }

        //determine the error message to display
        if (msg == null)
        {
            if (ex instanceof IOException)
            {
                //IOException, eg when trying to connect to a server/port with no JMX server running
                msg = SERVER_UNAVAILABLE;
                //Display error dialogue and return
                displayErrorDialogue(msg, title);
                return;
            }
            else if (ex instanceof SecurityException)
            {
                //SecurityException when providing incorrect login credentials
                msg = SECURITY_FAILURE;
                //Display error dialogue and return
                displayErrorDialogue(msg, title);
                return;
            }
            else
            {
                //Unknown exception type/reason. 
                msg = ex.getMessage();
            }

            //if msg is still null, try reporting the cause.
            if ((msg == null) && (ex.getCause() != null))
            {
                msg = ex.getCause().getMessage();
            }

            //failing all else, default non-descript error message.
            if (msg == null)
            {
                msg = "An unknown error has occured.";
            }
        }

        //Display error dialogue and print the exception stack trace
        MBeanUtility.printStackTrace(ex);
        displayErrorDialogue(msg, title);
    }
    
    private void displayErrorDialogue(String msg, String title)
    {
        IStatus status = new Status(IStatus.ERROR, ApplicationWorkbenchAdvisor.PERSPECTIVE_ID,
                IStatus.OK, msg, null); 
        ErrorDialog.openError(_window.getShell(), "Error", title, status);      
    }

    /**
     * Selection in the workbench has been changed. We can change the state of the 'real' action here
     * if we want, but this can only happen after  the delegate has been created.
     * @see IWorkbenchWindowActionDelegate#selectionChanged
     */
    public void selectionChanged(IAction action, ISelection selection) {
    }

    /**
     * We can use this method to dispose of any system resources we previously allocated.
     * @see IWorkbenchWindowActionDelegate#dispose
     */
    public void dispose() {
        
    }
}
