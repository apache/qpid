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

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ApplicationWorkbenchAdvisor;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

public class AbstractAction
{
    protected IWorkbenchWindow _window;
   
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
    
    protected void handleException(Throwable ex, String title, String msg)
    {
        MBeanUtility.printStackTrace(ex);
        if (msg == null)
        {
            msg = ex.getMessage();
        }
        if ((msg == null) && (ex.getCause() != null))
        {
            msg = ex.getCause().getMessage();
        }
        
        if (msg == null)
        {
            msg = ERROR_SERVER_CONNECTION;
        }
        
        if (title == null)
        {
            title = ERROR_SERVER_CONNECTION;
        }
        IStatus status = new Status(IStatus.ERROR, ApplicationWorkbenchAdvisor.PERSPECTIVE_ID,
                                    IStatus.OK, msg, null); 
        ErrorDialog.openError(_window.getShell(), "Error", title, status);
    }
}
