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

import org.apache.qpid.management.ui.ApplicationWorkbenchAdvisor;
import org.apache.qpid.management.ui.exceptions.InfoRequiredException;
import org.apache.qpid.management.ui.views.MBeanView;
import org.apache.qpid.management.ui.views.ViewUtility;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

public class EditAttribute implements IWorkbenchWindowActionDelegate
{
    private IWorkbenchWindow _window;
    
    public void run(IAction action)
    {
        if(_window != null)
        {   
            MBeanView view = (MBeanView)_window.getActivePage().findView(MBeanView.ID);
            try
            {
                view.editAttribute();
            }
            catch(InfoRequiredException ex)
            {
                ViewUtility.popupInfoMessage("Edit Attribute", ex.getMessage());
            }
            catch(Exception ex)
            {
                IStatus status = new Status(IStatus.ERROR, ApplicationWorkbenchAdvisor.PERSPECTIVE_ID,
                                            IStatus.OK, ex.getMessage(), ex.getCause()); 
                ErrorDialog.openError(_window.getShell(), "Error", "Attribute could not be edited", status);
            }
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
}
