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
package org.apache.qpid.management.ui.views;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.apache.qpid.management.ui.model.OperationData;
import org.apache.qpid.management.ui.model.ParameterData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;


/**
 * Control class for the MBean operations tab. It creates the required widgets
 * for the selected MBean.
 * @author Bhupendra Bhardwaj
 */
public class OperationTabControl extends TabControl
{
    private int heightForAParameter = 30;
    private int labelNumerator = 30;
    private int valueNumerator = labelNumerator + 20;
    
    private FormToolkit _toolkit;
    private Form        _form;
    private OperationData _opData;
    
    private SelectionListener operationExecutionListener = new OperationExecutionListener(); 
    private SelectionListener refreshListener = new RefreshListener(); 
    private SelectionListener parameterSelectionListener = new ParameterSelectionListener();
    private SelectionListener bolleanSelectionListener = new BooleanSelectionListener();
    private VerifyListener    verifyListener = new VerifyListenerImpl();
    private KeyListener       keyListener = new KeyListenerImpl();
    private KeyListener       headerBindingListener = new HeaderBindingKeyListener();
    
    private Composite _headerComposite = null;
    private Composite _paramsComposite = null;
    private Composite _resultsComposite = null;
    private Button _executionButton = null;
    
    // for customized method in header exchange
    private HashMap<Text, Text> headerBindingHashMap = null;
    private String _virtualHostName = null;
    
    public OperationTabControl(TabFolder tabFolder)
    {
        super(tabFolder);
        _toolkit = new FormToolkit(_tabFolder.getDisplay());
        _form = _toolkit.createForm(_tabFolder);
        _form.getBody().setLayout(new GridLayout());
    }
    
    /**
     * Form area is devided in four parts:
     * Header composite - displays operaiton information
     * Patameters composite - displays parameters if there
     * Button - operation execution button
     * Results composite - displays results for operations, which have 
     *                     no parameters but have some return value
     */
    private void createComposites()
    {
        // 
        _headerComposite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        _headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        
        List<ParameterData> params = _opData.getParameters();
        if (params != null && !params.isEmpty())
        {
            _paramsComposite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
            _paramsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        }
        _executionButton = _toolkit.createButton(_form.getBody(), Constants.BUTTON_EXECUTE, SWT.PUSH | SWT.CENTER);
        _executionButton.setFont(ApplicationRegistry.getFont(Constants.FONT_BUTTON));
        GridData layoutData = new GridData(SWT.CENTER, SWT.TOP, true, false);
        layoutData.verticalIndent = 20;
        _executionButton.setLayoutData(layoutData);
        
        _resultsComposite = _toolkit.createComposite(_form.getBody(), SWT.NONE);
        layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
        layoutData.verticalIndent = 20;
        _resultsComposite.setLayoutData(layoutData);
        _resultsComposite.setLayout(new GridLayout());
    }
    
    /**
     * @see TabControl#getControl()
     */
    public Control getControl()
    {
        return _form;
    }
    
    @Override
    public void refresh(ManagedBean mbean)
    {
        _mbean = mbean;
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(mbean);     
        _opData = serverRegistry.getOperationModel(mbean).getOperations().get(0);
        refresh(_mbean, _opData);
    }
    
    public void refresh(ManagedBean mbean, OperationData opData)
    {
        _mbean = mbean;
        _opData = opData;
        _virtualHostName = _mbean.getProperty(Constants.VIRTUAL_HOST);
        
        // Setting the form to be invisible. Just in case the mbean server connection
        // is done and it takes time in getting the response, then the ui should be blank
        // instead of having half the widgets displayed.
        _form.setVisible(false);
        
        ViewUtility.disposeChildren(_form.getBody());        
        createComposites();
        setHeader();
        createParameterWidgets();
        
        // Set button text and add appropriate listener to button.
        // If there are no parameters and it is info operation, then operation gets executed
        // and result is displayed
        List<ParameterData> params = opData.getParameters();
        if (params != null && !params.isEmpty())
        {            
            setButton(Constants.BUTTON_EXECUTE);
        }
        else if (opData.getImpact() == Constants.OPERATION_IMPACT_ACTION)
        {
            setButton(Constants.BUTTON_EXECUTE);
        }
        else if (opData.getImpact() == Constants.OPERATION_IMPACT_INFO)
        {
            setButton(Constants.BUTTON_REFRESH);
            executeAndShowResults();
        }
        
        _form.setVisible(true);
        _form.layout();
    }
    
    /**
     * populates the header composite, containing the operation name and description.
     */
    private void setHeader()
    {
        _form.setText(ViewUtility.getDisplayText(_opData.getName()));
        _headerComposite.setLayout(new GridLayout(2, false));
        //operation description
        Label label = _toolkit.createLabel(_headerComposite,  Constants.DESCRIPTION + " : ");
        label.setFont(ApplicationRegistry.getFont(Constants.FONT_BOLD));
        label.setLayoutData(new GridData(SWT.LEAD, SWT.TOP, false, false));
        
        label = _toolkit.createLabel(_headerComposite,  _opData.getDescription());
        label.setFont(ApplicationRegistry.getFont(Constants.FONT_NORMAL));
        label.setLayoutData(new GridData(SWT.LEAD, SWT.TOP, true, false));
        
        _headerComposite.layout();
    }
    
    /**
     * Creates the widgets for operation parameters if there are any
     */
    private void createParameterWidgets()
    {
        List<ParameterData> params = _opData.getParameters();
        if (params == null || params.isEmpty())
        {
            return;
        }
        
        // Customised parameter widgets        
        if (_mbean.isExchange() &&
            Constants.EXCHANGE_TYPE_VALUES[2].equals(_mbean.getProperty(Constants.EXCHANGE_TYPE)) &&
            _opData.getName().equalsIgnoreCase(Constants.OPERATION_CREATE_BINDING))
        {                                  
            customCreateNewBinding(); 
            return;
        }
        // end of Customised parameter widgets       
        
        _paramsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _paramsComposite.setLayout(new FormLayout());
        for (ParameterData param : params)
        {            
            boolean valueInCombo = false;
            Label label = _toolkit.createLabel(_paramsComposite, ViewUtility.getDisplayText(param.getName()));
            FormData formData = new FormData();
            formData.top = new FormAttachment(0, params.indexOf(param) * heightForAParameter + 2);
            formData.right = new FormAttachment(labelNumerator);
            label.setLayoutData(formData);
            label.setToolTipText(param.getDescription());
            
            formData = new FormData();
            formData.top = new FormAttachment(0, params.indexOf(param) * heightForAParameter);
            formData.left = new FormAttachment(label, 5);
            formData.right = new FormAttachment(valueNumerator);
            if (param.getName().equals(Constants.QUEUE))
            {
                Combo combo = new Combo(_paramsComposite, SWT.READ_ONLY | SWT.DROP_DOWN);
                String[] items = ApplicationRegistry.getServerRegistry(_mbean).getQueueNames(_virtualHostName);
                combo.setItems(items);
                combo.add("Select Queue", 0); 
                combo.select(0);
                combo.setLayoutData(formData);
                combo.setData(param);
                combo.addSelectionListener(parameterSelectionListener);
                valueInCombo = true;
            }
            else if (param.getName().equals(Constants.EXCHANGE))
            {
                Combo combo = new Combo(_paramsComposite, SWT.READ_ONLY | SWT.DROP_DOWN);
                String[] items = ApplicationRegistry.getServerRegistry(_mbean).getExchangeNames(_virtualHostName);
                combo.setItems(items);
                combo.add("Select Exchange", 0);
                combo.select(0);
                combo.setLayoutData(formData);
                combo.setData(param);
                combo.addSelectionListener(parameterSelectionListener);
                valueInCombo = true;
            }
            else if (param.getName().equals(Constants.EXCHANGE_TYPE))
            {
                Combo combo = new Combo(_paramsComposite, SWT.READ_ONLY | SWT.DROP_DOWN);
                combo.setItems(Constants.EXCHANGE_TYPE_VALUES);
                combo.add("Select Exchange Type", 0);
                combo.select(0);
                combo.setLayoutData(formData);
                combo.setData(param);
                combo.addSelectionListener(parameterSelectionListener);
                valueInCombo = true;                
            }
            else if (param.isBoolean())
            {
                Combo combo = new Combo(_paramsComposite, SWT.READ_ONLY | SWT.DROP_DOWN);
                combo.setItems(Constants.BOOLEAN_TYPE_VALUES);
                combo.select(0);
                param.setValueFromString(combo.getItem(0));
                combo.setLayoutData(formData);
                combo.setData(param);
                combo.addSelectionListener(bolleanSelectionListener);
                valueInCombo = true;                
            }
            else
            {
                Text text = _toolkit.createText(_paramsComposite, "", SWT.NONE);
                formData = new FormData();
                formData.top = new FormAttachment(0, params.indexOf(param) * heightForAParameter);
                formData.left = new FormAttachment(label, 5);
                formData.right = new FormAttachment(valueNumerator);
                text.setLayoutData(formData);
                text.addKeyListener(keyListener);
                text.addVerifyListener(verifyListener);
                text.setData(param);
            }
            
            // parameter type (int, String etc)
            if (valueInCombo)
                label = _toolkit.createLabel(_paramsComposite, "");
            else
            {
                String str = param.getType() ;
                if (param.getType().lastIndexOf(".") != -1)
                    str = param.getType().substring(1 + param.getType().lastIndexOf("."));
                
                label = _toolkit.createLabel(_paramsComposite, "(" + str + ")");
            }
            formData = new FormData();
            formData.top = new FormAttachment(0, params.indexOf(param) * heightForAParameter);
            formData.left = new FormAttachment(valueNumerator, 5);
            label.setLayoutData(formData);
        }
    }
    
    /**
     * Creates customized dispaly for a method "CreateNewBinding" for Headers exchange
     *
     */
    private void customCreateNewBinding()
    {
        headerBindingHashMap = new HashMap<Text, Text>();
 
        _paramsComposite.setLayout(new GridLayout());
        _paramsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true));
        final ScrolledComposite scrolledComposite = new ScrolledComposite(_paramsComposite, SWT.BORDER | SWT.V_SCROLL);
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);   
        GridData layoutData = new GridData(SWT.FILL, SWT.TOP, true, true);
        scrolledComposite.setLayoutData(layoutData);
        scrolledComposite.setLayout(new GridLayout());
        
        final Composite composite = _toolkit.createComposite(scrolledComposite, SWT.NONE);
        scrolledComposite.setContent(composite);
        layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);            
        layoutData.verticalIndent = 20;
        composite.setLayoutData(layoutData);
        composite.setLayout(new FormLayout());
        
        List<ParameterData> params = _opData.getParameters();
        ParameterData param = params.get(0);
        // Queue selection widget
        Label label = _toolkit.createLabel(composite, ViewUtility.getDisplayText(param.getName()));
        FormData formData = new FormData();
        formData.top = new FormAttachment(0, 2);
        formData.right = new FormAttachment(labelNumerator);
        label.setLayoutData(formData);
        label.setToolTipText(param.getDescription());
        
        formData = new FormData();
        formData.top = new FormAttachment(0);
        formData.left = new FormAttachment(label, 5);
        formData.right = new FormAttachment(valueNumerator);

        Combo combo = new Combo(composite, SWT.READ_ONLY | SWT.DROP_DOWN);        
        String[] items = ApplicationRegistry.getServerRegistry(_mbean).getQueueNames(_virtualHostName);
        combo.setItems(items);
        combo.add("Select Queue", 0); 
        combo.select(0);
        combo.setLayoutData(formData);
        combo.setData(param);
        combo.addSelectionListener(parameterSelectionListener);

        // Binding creation widgets
        createARowForCreatingHeadersBinding(composite, 1);
        createARowForCreatingHeadersBinding(composite, 2);
        createARowForCreatingHeadersBinding(composite, 3);
        createARowForCreatingHeadersBinding(composite, 4);
        createARowForCreatingHeadersBinding(composite, 5);
        createARowForCreatingHeadersBinding(composite, 6);
        createARowForCreatingHeadersBinding(composite, 7);
        createARowForCreatingHeadersBinding(composite, 8);
        
        final Button addMoreButton = _toolkit.createButton(composite, "Add More", SWT.PUSH);
        formData = new FormData();
        formData.top = new FormAttachment(0, heightForAParameter);
        formData.left = new FormAttachment(70, 5);
        addMoreButton.setLayoutData(formData);
        addMoreButton.setData("rowCount", 8);
        addMoreButton.addSelectionListener(new SelectionAdapter()
            {
                public void widgetSelected(SelectionEvent e)
                {
                    int count = Integer.parseInt(addMoreButton.getData("rowCount").toString());
                    createARowForCreatingHeadersBinding(composite, ++count);
                    addMoreButton.setData("rowCount", count);
                    scrolledComposite.setMinSize(composite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
                    composite.layout();
                    _form.layout();
                }
            });
          
        scrolledComposite.setMinSize(composite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        composite.layout();
    }
    
    /**
     * Adds a row for adding a binding for Headers Exchange. Used by the method, which creates the customized
     * layout and widgest for Header's exchange method createNewBinding.
     * @param parent composite
     * @param rowCount - row number
     */
    private void createARowForCreatingHeadersBinding(Composite parent, int rowCount)
    {  
        Label key = _toolkit.createLabel(parent, "Name");
        FormData formData = new FormData();
        formData.top = new FormAttachment(0, rowCount * heightForAParameter + 2);
        formData.right = new FormAttachment(15);
        key.setLayoutData(formData);
        
        Text keyText = _toolkit.createText(parent, "", SWT.NONE);
        formData = new FormData();
        formData.top = new FormAttachment(0, rowCount * heightForAParameter);
        formData.left = new FormAttachment(key, 5);
        formData.right = new FormAttachment(40);
        keyText.setLayoutData(formData);
        keyText.addKeyListener(headerBindingListener);
        
        Label value = _toolkit.createLabel(parent, "Value");
        formData = new FormData();
        formData.top = new FormAttachment(0, rowCount * heightForAParameter + 2);
        formData.right = new FormAttachment(45);
        value.setLayoutData(formData);
        
        Text valueText = _toolkit.createText(parent, "", SWT.NONE);
        formData = new FormData();
        formData.top = new FormAttachment(0, rowCount * heightForAParameter);
        formData.left = new FormAttachment(value, 5);
        formData.right = new FormAttachment(70);
        valueText.setLayoutData(formData);
        valueText.addKeyListener(headerBindingListener);
        
        // Add these to the map, to retrieve the values while setting the parameter value
        headerBindingHashMap.put(keyText, valueText);
    }
    
    /**
     * Sets text and listener for the operation execution button
     * @param text
     */
    private void setButton(String text)
    {
        _executionButton.setText(text);
        _executionButton.removeSelectionListener(refreshListener);
        _executionButton.removeSelectionListener(operationExecutionListener);
        
        if (Constants.BUTTON_EXECUTE.equals(text))
        {
            _executionButton.addSelectionListener(operationExecutionListener);    
        }
        else
        {
            _executionButton.addSelectionListener(refreshListener);
        }
    }   

    /**
     * displays the operation result in a pop-up window
     * @param result
     */
    private void populateResults(Object result)
    {
        Display display = Display.getCurrent();
        int width = 600;
        int height = 400;
        Shell shell = ViewUtility.createPopupShell(Constants.RESULT, width, height);
        ViewUtility.populateCompositeWithData(_toolkit, shell, result);
        
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        shell.dispose();
    }
    
    /**
     * Clears the parameter values of the operation
     */
    private void clearParameters()
    {
        List<ParameterData> params = _opData.getParameters();
        if (params != null && !params.isEmpty())
        {
            for (ParameterData param : params)
            {
                param.setDefaultValue();
            }
        }
    }
    
    /**
     * Clears the values entered by the user from parameter value widgets
     * @param control
     */
    private void clearParameterValues(Composite control)
    {
        if (control == null || (control.isDisposed()))
            return;
        
        Control[] controls = control.getChildren();
        if (controls == null || controls.length == 0)
            return;
        
        for (int i = 0; i < controls.length; i++)
        {
            if (controls[i] instanceof Combo)
                ((Combo)controls[i]).select(0);
            else if (controls[i] instanceof Text)
                ((Text)controls[i]).setText("");
            else if (controls[i] instanceof Composite)
                clearParameterValues((Composite)controls[i]);
        }
    }
    
    /**
     * Listener class for operation execution events
     */
    private class OperationExecutionListener extends SelectionAdapter
    {
        public void widgetSelected(SelectionEvent e)
        {
            List<ParameterData> params = _opData.getParameters();
            if (params != null && !params.isEmpty())
            {
                for (ParameterData param : params)
                { 
                    if (param.getValue() == null || param.getValue().toString().length() == 0)
                    {
                        // Customized check, because for this parameter null is allowed
                        if (param.getName().equals(Constants.QUEUE_OWNER) &&
                            _opData.getName().equals(Constants.OPERATION_CREATE_QUEUE))
                        {
                            continue;
                        }
                        // End of custom code
                        
                        ViewUtility.popupInfoMessage(_form.getText(),
                                "Please select the " + ViewUtility.getDisplayText(param.getName()));
                        
                        return;
                    }
                }
            }
            
            if (_opData.getImpact() == Constants.OPERATION_IMPACT_ACTION)
            {
                String bean = _mbean.getName() == null ? _mbean.getType() : _mbean.getName();
                int response = ViewUtility.popupConfirmationMessage(bean, 
                        "Do you want to " + _form.getText()+ " ?");
                if (response == SWT.YES)
                {
                    executeAndShowResults();
                }            
            }
            else
            {
                executeAndShowResults();
            }
            clearParameters();
            clearParameterValues(_paramsComposite);
        }
    }
    
    // Listener for the "Refresh" execution button
    private class RefreshListener extends SelectionAdapter
    {
        public void widgetSelected(SelectionEvent e)
        {
            executeAndShowResults();
        }
    }
    
    /**
     * Executres the operation, gets the result from server and displays to the user
     */
    private void executeAndShowResults()
    {
        Object result = null;
        try
        {
            result = MBeanUtility.execute(_mbean, _opData);     
        }
        catch(Exception ex)
        {
            MBeanUtility.handleException(_mbean, ex);
            return;
        }
        
        // Some mbeans have only "type" and no "name".
        String title = _mbean.getType();
        if (_mbean.getName() != null && _mbean.getName().length() != 0)
        {
            title = _mbean.getName();
        }
        
        if (_opData.getReturnType().equals("void") || _opData.getReturnType().equals("java.lang.Void"))
        {
            ViewUtility.popupInfoMessage(title, "Operation successful");
        }
        else if (_opData.getParameters() != null && !_opData.getParameters().isEmpty())
        {
            populateResults(result);
        }
        else
        {
            ViewUtility.disposeChildren(_resultsComposite);
            ViewUtility.populateCompositeWithData(_toolkit, _resultsComposite, result);
            _resultsComposite.layout();
            _form.layout();
        }

    }
    
    /**
     * Listener class for the operation parameters widget
     */
    private class ParameterSelectionListener extends SelectionAdapter
    {
        public void widgetSelected(SelectionEvent e)
        {
            Combo combo = (Combo)e.widget;
            ParameterData parameter = (ParameterData)combo.getData();
            if (combo.getSelectionIndex() > 0)
            {
                String item = combo.getItem(combo.getSelectionIndex());                
                parameter.setValueFromString(item);
            }
            else
            {
                parameter.setValue(null);
            }
        }
    }
    
    /**
     * Listener class for boolean parameter widgets
     */
    private class BooleanSelectionListener extends SelectionAdapter
    {
        public void widgetSelected(SelectionEvent e)
        {
            Combo combo = (Combo)e.widget;
            ParameterData parameter = (ParameterData)combo.getData();
            String item = combo.getItem(combo.getSelectionIndex());                
            parameter.setValueFromString(item);
        }
    }
    
    /**
     * Listener class for the operation parameter value widget (Text field)
     */
    private class KeyListenerImpl extends KeyAdapter
    {
        public void keyReleased(KeyEvent e) 
        {
            if (!(e.widget instanceof Text))
                return;
            
            Text text = (Text)e.widget;
            // Get the parameters widget and assign the text to the parameter
            String strValue = text.getText();
            ParameterData parameter = (ParameterData)text.getData();
            parameter.setValueFromString(strValue);
        }
    }
    
    /**
     * Listener class for HeaderExchange's new binding widgets. Used when the new bindings are 
     * being created for Header's Exchange
     */
    private class HeaderBindingKeyListener extends KeyAdapter
    {
        public void keyReleased(KeyEvent e) 
        {
            ParameterData param = _opData.getParameters().get(1);
            StringBuffer paramValue = new StringBuffer();
            for (Entry<Text, Text> entry : headerBindingHashMap.entrySet())
            {
                
                Text nameText = entry.getKey();
                String name = nameText.getText();
                Text valueText = entry.getValue();
                String value = valueText.getText();
                if ((name != null) && (name.length() != 0) && (value != null) && (value.length() != 0))
                {
                    if (paramValue.length() != 0)
                    {
                        paramValue.append(",");
                    }
                    paramValue.append(name + "=" + value);
                }
            }
            
            param.setValue(paramValue.toString());
        }
    }
    
    /**
     * Listener class for verifying the user input with parameter type
     */
    private class VerifyListenerImpl implements VerifyListener
    {
        public void verifyText(VerifyEvent event)
        {
            Text text = (Text)event.widget;
            String string = event.text;
            char [] chars = new char [string.length ()];
            string.getChars (0, chars.length, chars, 0);
            
            ParameterData parameter = (ParameterData)text.getData();
            String type = parameter.getType();
            if (type.equals("int") || type.equals("java.lang.Integer") ||
                type.equals("long") || type.equals("java.lang.Long"))
            {
                for (int i=0; i<chars.length; i++)
                {
                    if (!('0' <= chars [i] && chars [i] <= '9'))
                    {
                        event.doit = false;
                        return;
                    }
                }
                
            }
        }
    }
    
}
