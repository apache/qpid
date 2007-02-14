package org.apache.qpid.management.ui.views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.AttributeData;
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

/**
 * Class to create widgets and control display for mbeantype(eg Connection, Queue, Exchange) selection
 * on the navigation view.
 * @author Bhupendra Bhardwaj
 *
 */
public class MBeanTypeTabControl
{
    private FormToolkit  _toolkit = null;
    private Form _form = null;
    private TabFolder _tabFolder = null;
    private Composite _composite = null;
    private Composite _listComposite = null;
    private Composite _buttonsComposite = null;
    private Label _labelName = null;
    private Label _labelDesc = null;
    private Label _labelList = null;
    
    private org.eclipse.swt.widgets.List _list = null;
    private Button _refreshButton = null;
    private Button _addButton = null;
    private Button _sortBySizeButton = null;
    
    private String _type = null;
    
    // maps an mbean name with the mbean object. Required to get mbean object when an mbean
    // is to be added to the navigation view. 
    private HashMap<String, ManagedBean> _objectsMap = new HashMap<String, ManagedBean>();
    // Map required for sorting queues based on attribute values
    private Map<AttributeData, ManagedBean> _queueMap = new LinkedHashMap<AttributeData, ManagedBean>();
    
    private Sorter _sorterByName = new Sorter();
    private ComparatorImpl _sorterByQueueDepth = new ComparatorImpl();
    
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
    
    /**
     * Adds listeners to all the buttons
     */
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
                    String name = selectedItems[i];;
                    if (Constants.QUEUE.equals(_type))
                    {
                        int endIndex = name.lastIndexOf("(");
                        name = name.substring(0, endIndex -1);
                    }
                    // pass the ManagedBean to the navigation view to be added
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
        
        _sortBySizeButton.addSelectionListener(new SelectionAdapter(){
            public void widgetSelected(SelectionEvent e)
            {
                try
                {
                    sortQueueByQueueDepth();
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
        
        _addButton = _toolkit.createButton(_composite, "<- Add to Navigation", SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        _addButton.setLayoutData(gridData);
        
        _refreshButton = _toolkit.createButton(_composite, Constants.BUTTON_REFRESH, SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        gridData.widthHint = 80;
        _refreshButton.setLayoutData(gridData);
        
        // Composite to contain the item list 
        _listComposite = _toolkit.createComposite(_composite);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        _listComposite.setLayoutData(gridData);
        layout = new GridLayout();
        layout.verticalSpacing = 0;
        _listComposite.setLayout(layout);
        
        // Label for item name
        _labelList = _toolkit.createLabel(_listComposite, " ", SWT.NONE);
        _labelList.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
        _labelList.setFont(ApplicationRegistry.getFont(Constants.FONT_NORMAL));
        
        _list = new List(_listComposite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        _list.setLayoutData(gridData);
        
        
        // Composite to contain buttons like - Sort by size
        _buttonsComposite = _toolkit.createComposite(_composite);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        _buttonsComposite.setLayoutData(gridData);
        _buttonsComposite.setLayout(new GridLayout());
        
        _sortBySizeButton = _toolkit.createButton(_buttonsComposite, "Sort by Queue Depth", SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        _sortBySizeButton.setLayoutData(gridData);
        
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
        _queueMap.clear();
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(MBeanView.getServer());
        String[] items = null;
        java.util.List<ManagedBean> list = null;
        
        // populate the map and list with appropriate mbeans
        if (_type.equals(Constants.QUEUE))
        {
            list = serverRegistry.getQueues(MBeanView.getVirtualHost());
            items = getQueueItems(list);
            _sortBySizeButton.setVisible(true);
        }
        else if (_type.equals(Constants.EXCHANGE))
        {
            list = serverRegistry.getExchanges(MBeanView.getVirtualHost());
            items = getItems(list);
            _sortBySizeButton.setVisible(false);
        }
        else if (_type.equals(Constants.CONNECTION))
        {
            list = serverRegistry.getConnections(MBeanView.getVirtualHost());
            items = getItems(list);
            _sortBySizeButton.setVisible(false);
        }
        else
        {
            throw new Exception("Unknown mbean type " + _type);
        }
        
        _list.setItems(items);
            
    }
    
    // sets the map with appropriate mbean and name
    private String[] getItems(java.util.List<ManagedBean> list)
    {
        if (list == null)
            return new String[0];
        
        Collections.sort(list, _sorterByName);
        String[] items = new String[list.size()];
        int i = 0;
        for (ManagedBean mbean : list)
        {
            items[i++] = mbean.getName();
            _objectsMap.put(mbean.getName(), mbean);
        }
        return items;
    }
    
    private String[] getQueueItems(java.util.List<ManagedBean> list) throws Exception
    {
        if (list == null)
            return new String[0];
        
        // Sort the list. It will keep the mbeans in sorted order in the _queueMap, which is required for
        // sorting the queue according to size etc
        Collections.sort(list, _sorterByName);
        String[] items = new String[list.size()];
        int i = 0;
        for (ManagedBean mbean : list)
        {
            AttributeData data = MBeanUtility.getAttributeData(mbean, Constants.ATTRIBUTE_QUEUE_DEPTH);
            String value = data.getValue().toString();
            items[i] = mbean.getName() + " (" + value + " KB)";
            _objectsMap.put(mbean.getName(), mbean);
            _queueMap.put(data, mbean);
            i++;
        }
        return items;
    }
    
    private void sortQueueByQueueDepth() throws Exception
    {
        // Queues are already in the alphabetically sorted order in _queueMap, now sort for queueDepth
        java.util.List<AttributeData> list = new ArrayList<AttributeData>(_queueMap.keySet());
        Collections.sort(list, _sorterByQueueDepth);
        
        String[] items = new String[list.size()];
        int i = 0;
        for (AttributeData data : list)
        {
            ManagedBean mbean = _queueMap.get(data);
            String value = data.getValue().toString();
            items[i++] = mbean.getName() + " (" + value + " KB)";
        }
        _list.setItems(items);
    }
    
    private class ComparatorImpl implements java.util.Comparator<AttributeData>
    {
        public int compare(AttributeData data1, AttributeData data2)
        {
            Integer int1 = Integer.parseInt(data1.getValue().toString());
            Integer int2 = Integer.parseInt(data2.getValue().toString());
            return int1.compareTo(int2) * -1;
        }
    }
    
    private class Sorter implements java.util.Comparator<ManagedBean>
    {
        public int compare(ManagedBean mbean1, ManagedBean mbean2)
        {
            return mbean1.getName().compareTo(mbean2.getName());
        }
    }
}
