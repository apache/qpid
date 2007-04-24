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
    
    protected void handleException(Exception ex, String title, String msg)
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
