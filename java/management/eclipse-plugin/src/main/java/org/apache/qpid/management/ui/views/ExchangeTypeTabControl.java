package org.apache.qpid.management.ui.views;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.eclipse.swt.widgets.TabFolder;

/**
 * Controller class, which takes care of displaying appropriate information and widgets for Exchanges.
 * This allows user to select Exchanges and add those to the navigation view
 * @author Bhupendra Bhardwaj
 */
public class ExchangeTypeTabControl extends MBeanTypeTabControl
{

    public ExchangeTypeTabControl(TabFolder tabFolder)
    {
        super(tabFolder, Constants.EXCHANGE);
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
        java.util.List<ManagedBean> list = serverRegistry.getExchanges(MBeanView.getVirtualHost());
        getListWidget().setItems(getItems(list));         
    }
}
