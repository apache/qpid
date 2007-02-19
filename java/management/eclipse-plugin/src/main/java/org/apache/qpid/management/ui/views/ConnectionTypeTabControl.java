package org.apache.qpid.management.ui.views;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.eclipse.swt.widgets.TabFolder;

/**
 * Controller class, which takes care of displaying appropriate information and widgets for Connections.
 * This allows user to select Connections and add those to the navigation view
 * @author Bhupendra Bhardwaj
 */
public class ConnectionTypeTabControl extends MBeanTypeTabControl
{

    public ConnectionTypeTabControl(TabFolder tabFolder)
    {
        super(tabFolder, Constants.CONNECTION);
        createWidgets();
    }
    
    protected void createWidgets()
    {
        createHeaderComposite(getFormComposite());
        createButtonsComposite(getFormComposite());
        createListComposite(getFormComposite());
    }
    
    protected void populateList() throws Exception
    {
        // map should be cleared before populating it with new values
        getMBeansMap().clear();
        
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(MBeanView.getServer());
        java.util.List<ManagedBean> list = serverRegistry.getConnections(MBeanView.getVirtualHost());
        getListWidget().setItems(getItems(list));  
    }
}
