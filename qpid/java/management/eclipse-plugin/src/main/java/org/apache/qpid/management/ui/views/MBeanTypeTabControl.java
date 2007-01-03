package org.apache.qpid.management.ui.views;

import java.util.Arrays;
import java.util.HashMap;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;

public class MBeanTypeTabControl
{
    private FormToolkit  _toolkit = null;
    private Form _form = null;
    private TabFolder _tabFolder = null;
    private Composite _composite = null;
    private Composite _listComposite = null;
    private Label _labelName = null;
    private Label _labelDesc = null;
    private Label _labelList = null;
    private org.eclipse.swt.widgets.List _list = null;
    private Button _refreshButton = null;
    private Button _addButton = null;
    
    private String _type = null;
    
    // maps an mbena name with the mbean object. Required to get mbean object when an mbean
    // is to be added to the navigation view. 
    private HashMap<String, ManagedBean> _objectsMap = new HashMap<String, ManagedBean>();
    
    public MBeanTypeTabControl(TabFolder tabFolder)
    {
        _tabFolder = tabFolder;
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createForm(_tabFolder);
        createWidgets();
        addListeners();
    }
    
    public Control getControl()
    {
        return _form;
    }
    
    private void addListeners()
    {
        _addButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                if (_list.getSelectionCount() == 0)
                    return;
                
                String[] selectedItems = _list.getSelection();
                for (int i = 0; i < selectedItems.length; i++)
                {
                    String name = selectedItems[i];
                    ManagedBean mbean = _objectsMap.get(name);
                    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow(); 
                    NavigationView view = (NavigationView)window.getActivePage().findView(NavigationView.ID);
                    try
                    {
                        view.addManagedBean(mbean);
                    }
                    catch (Exception ex)
                    {
                        MBeanUtility.handleException(mbean, ex);
                    }
                }
            }
        });
        
        _refreshButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                try
                {
                    refresh(_type);
                }
                catch (Exception ex)
                {
                    MBeanUtility.handleException(ex);
                }
            }
        });
    }
    
    private void createWidgets()
    {
        _form.getBody().setLayout(new GridLayout());
        _composite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        _composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, true);
        layout.verticalSpacing = 10;
        layout.horizontalSpacing = 0;
        _composite.setLayout(layout);
        
        _labelName = _toolkit.createLabel(_composite, "Type:", SWT.NONE);
        GridData gridData = new GridData(SWT.CENTER, SWT.TOP, true, false, 2, 1);
        _labelName.setLayoutData(gridData);
        _labelName.setFont(ApplicationRegistry.getFont(Constants.FONT_BOLD));
        
        _labelDesc = _toolkit.createLabel(_composite, " ", SWT.NONE);
        _labelDesc.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false, 2, 1));
        _labelDesc.setFont(ApplicationRegistry.getFont(Constants.FONT_ITALIC));
        
        _refreshButton = _toolkit.createButton(_composite, Constants.BUTTON_REFRESH, SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false, 2, 1);
        gridData.widthHint = 80;
        _refreshButton.setLayoutData(gridData);
        
        _addButton = _toolkit.createButton(_composite, "<- Add to Navigation", SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        _addButton.setLayoutData(gridData);
        
        // Composite to contain the item list 
        _listComposite = _toolkit.createComposite(_composite);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        _listComposite.setLayoutData(gridData);
        _listComposite.setLayout(new GridLayout());
        
        // Label for item name
        _labelList = _toolkit.createLabel(_listComposite, " ", SWT.NONE);
        _labelList.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
        _labelList.setFont(ApplicationRegistry.getFont(Constants.FONT_NORMAL));
        
        _list = new List(_listComposite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        _list.setLayoutData(gridData);
    }
    
    public void refresh(String typeName) throws Exception
    {
        _type = typeName;
        setHeader();
        populateList();
        
        _listComposite.layout();
        _composite.layout();
        _form.layout();
    }
    
    private void setHeader()
    {
        _labelName.setText("Type : " + _type);        
        _labelDesc.setText("Select the " + _type + "(s) to add in the Navigation View");
        _labelList.setText("-- List of " + _type + "s --");
    }
    
    /**
     * populates the map with mbean name and the mbean object.
     * @throws Exception
     */
    private void populateList() throws Exception
    {
        // map should be cleared before populating it with new values
        _objectsMap.clear();
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(MBeanView.getServer());
        String[] items = null;
        java.util.List<ManagedBean> list = null;
        
        // populate the map and list with appropriate mbeans
        if (_type.equals(Constants.QUEUE))
        {
            list = serverRegistry.getQueues();
        }
        else if (_type.equals(Constants.EXCHANGE))
        {
            list = serverRegistry.getExchanges();;
        }
        else if (_type.equals(Constants.CONNECTION))
        {
            list = serverRegistry.getConnections();
        }
        else
        {
            throw new Exception("Unknown mbean type " + _type);
        }
        
        items = getItems(list);
        _list.setItems(items);
    }
    
    // sets the map with appropriate mbean and name
    private String[] getItems(java.util.List<ManagedBean> list)
    {
        String[] items = new String[list.size()];
        int i = 0;
        for (ManagedBean mbean : list)
        {
            items[i++] = mbean.getName();
            _objectsMap.put(mbean.getName(), mbean);
        }
        Arrays.sort(items);
        return items;
    }
}
