package org.apache.qpid.management.ui.views;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ServerRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;

public class MBeanTypeTabControl
{
    private FormToolkit  _toolkit;
    private Form _form;
    private TabFolder _tabFolder;
    private Composite _composite;
    private Label _labelName;
    private Label _labelDesc;
    private List _list;
    private Button _button;
    
    private String _type = null;
    
    public MBeanTypeTabControl(TabFolder tabFolder)
    {
        _tabFolder = tabFolder;
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createForm(_tabFolder);
        createWidgets();
    }
    
    public Control getControl()
    {
        return _form;
    }
    
    private void createWidgets()
    {
        _form.getBody().setLayout(new GridLayout());
        _composite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        _composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        _composite.setLayout(new GridLayout(2, true));
        
        _labelName = _toolkit.createLabel(_composite, "Type:", SWT.NONE);
        GridData gridData = new GridData(SWT.CENTER, SWT.TOP, true, false, 2, 1);
        _labelName.setLayoutData(gridData);
        _labelName.setFont(ApplicationRegistry.getFont(Constants.FONT_BOLD));
        /*
        _labelDesc = _toolkit.createLabel(_composite, " ", SWT.NONE);
        _labelDesc.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false, 2, 1));
        _labelDesc.setFont(ApplicationRegistry.getFont(Constants.FONT_ITALIC));
        
        _button = _toolkit.createButton(_composite, "<- Add to Navigation", SWT.PUSH);
        gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        _button.setLayoutData(gridData);
        */
        _list = new List(_composite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        _list.setLayoutData(gridData);
    }
    
    public void refresh(String typeName) throws Exception
    {
        _type = typeName;
        setHeader();
        populateList();
        _form.layout();
    }
    
    private void setHeader()
    {
        _labelName.setText("Type: " + _type);        
        //_labelDesc.setText("Select the " + _type + " to add in the Navigation View");
    }
    
    private void populateList() throws Exception
    {
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(MBeanView.getServer());
        String[] items = null;
        if (_type.equals(Constants.QUEUE))
        {
            items = serverRegistry.getQueueNames();
        }
        else if (_type.equals(Constants.EXCHANGE))
        {
            items = serverRegistry.getExchangeNames();
        }
        else if (_type.equals(Constants.CONNECTION))
        {
            items = serverRegistry.getConnectionNames();
        }
        else
        {
            throw new Exception("Unknown mbean type " + _type);
        }
        
        _list.setItems(items);
    }
}
