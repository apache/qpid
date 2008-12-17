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

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.qpid.management.ui.ApplicationWorkbenchAdvisor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;

/**
 * Utility Class for displaying OpenMbean data types by creating required SWT widgets
 * @author Bhupendra Bhardwaj
 */
public class ViewUtility
{
    public static final String OP_NAME     = "operation_name";
    public static final String OP_PARAMS   = "parameters";
    public static final String PARAMS_TEXT = "text";

    public static final String FIRST = "First";
    public static final String LAST  = "Last";
    public static final String NEXT  = "Next";
    public static final String PREV  = "Previous";
    public static final String INDEX = "Index";
    
    private static final Comparator tabularDataComparator = new TabularDataComparator();
    
    private static List<String> SUPPORTED_ARRAY_DATATYPES = new ArrayList<String>();
    static
    {
        SUPPORTED_ARRAY_DATATYPES.add("java.lang.String");
        SUPPORTED_ARRAY_DATATYPES.add("java.lang.Boolean");
        SUPPORTED_ARRAY_DATATYPES.add("java.lang.Character");
        SUPPORTED_ARRAY_DATATYPES.add("java.lang.Integer");
        SUPPORTED_ARRAY_DATATYPES.add("java.lang.Long");
        SUPPORTED_ARRAY_DATATYPES.add("java.lang.Double");
        SUPPORTED_ARRAY_DATATYPES.add("java.util.Date");
    }
    
    /**
     * Populates the composite with given openmbean data type (TabularType or CompositeType)
     * @param toolkit
     * @param parent composite
     * @param data open mbean data type(either composite type or tabular data type)
     */
    public static void populateCompositeWithData(FormToolkit toolkit, Composite parent, Object data)
    {
        if (data instanceof TabularDataSupport)
        {
            ViewUtility.createTabularDataHolder(toolkit, parent, (TabularDataSupport)data);
        }
        else if (data instanceof CompositeDataSupport)
        {
            ViewUtility.populateCompositeWithCompositeData(toolkit, parent, (CompositeDataSupport)data);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void createTabularDataHolder(FormToolkit toolkit, Composite parent, TabularDataSupport tabularData)
    {
        Composite composite = toolkit.createComposite(parent, SWT.BORDER);
        GridLayout layout = new GridLayout(4, true);
        layout.horizontalSpacing = 0;
        layout.marginWidth = 0;
        layout.marginHeight = 10;
        layout.verticalSpacing = 10;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Set entrySet = tabularData.entrySet();
        ArrayList<Map.Entry> list = new ArrayList<Map.Entry>(entrySet);
        if (list.size() == 0)
        {
            Text text = toolkit.createText(composite, " No records ", SWT.CENTER | SWT.SINGLE | SWT.READ_ONLY);
            GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
            text.setLayoutData(layoutData);
            return;
        }  
        
        Collections.sort(list, tabularDataComparator);
     
        // Attach the tabular record to be retrieved and shown later when record is traversed
        // using first/next/previous/last buttons
        composite.setData(list);
        
        // Create button and the composite for CompositeData
        Composite compositeDataHolder = createCompositeDataHolder(toolkit, composite,
                                        tabularData.getTabularType().getRowType());

        // display the first record
        CompositeData data = (CompositeData)(list.get(0)).getValue();
        composite.setData(INDEX, 0);
        populateCompositeWithCompositeData(toolkit, compositeDataHolder, data);
        enableOrDisableTraversalButtons(composite);
    }

    private static void enableOrDisableTraversalButtons(Composite composite)
    {
        int index = (Integer)composite.getData(INDEX);
        int size = ((List)composite.getData()).size();

        ((Button)composite.getData(FIRST)).setEnabled(true);
        ((Button)composite.getData(PREV)).setEnabled(true);
        ((Button)composite.getData(NEXT)).setEnabled(true);
        ((Button)composite.getData(LAST)).setEnabled(true);

        if (index == 0)
        {
            ((Button)composite.getData(FIRST)).setEnabled(false);
            ((Button)composite.getData(PREV)).setEnabled(false);
        }
        if (index == size -1)
        {
            ((Button)composite.getData(NEXT)).setEnabled(false);
            ((Button)composite.getData(LAST)).setEnabled(false);
        }
    }

    /**
     * Sets up the given composite for holding a CompositeData. Create traversal buttons, label etc and
     * creates a child Composite, which should be populated with the CompositeData
     * @param toolkit
     * @param dataHolder
     * @param compositeType
     * @return
     */
    private static Composite createCompositeDataHolder(final FormToolkit toolkit, final Composite dataHolder, CompositeType compositeType)
    {        
        String desc = compositeType.getDescription();
        Label description = toolkit.createLabel(dataHolder, desc, SWT.CENTER);
        description.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false, 4, 1));       
        // TODO nameLabel.setFont(font);
        description.setText(desc);

        // Add traversal buttons
        final Button firstRecordButton = toolkit.createButton(dataHolder, FIRST, SWT.PUSH);
        GridData layoutData = new GridData (GridData.HORIZONTAL_ALIGN_END);
        layoutData.widthHint = 80;
        firstRecordButton.setLayoutData(layoutData);

        final Button nextRecordButton = toolkit.createButton(dataHolder, NEXT, SWT.PUSH);
        layoutData = new GridData (GridData.HORIZONTAL_ALIGN_END);
        layoutData.widthHint = 80;
        nextRecordButton.setLayoutData(layoutData);

        final Button previousRecordButton = toolkit.createButton(dataHolder, PREV, SWT.PUSH);
        layoutData = new GridData (GridData.HORIZONTAL_ALIGN_BEGINNING);
        layoutData.widthHint = 80;
        previousRecordButton.setLayoutData(layoutData);

        final Button lastRecordButton = toolkit.createButton(dataHolder, LAST, SWT.PUSH);
        layoutData = new GridData (GridData.HORIZONTAL_ALIGN_BEGINNING);
        layoutData.widthHint = 80;
        lastRecordButton.setLayoutData(layoutData);
        
        // Now create the composite, which will hold the CompositeData
        final Composite composite = toolkit.createComposite(dataHolder, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.horizontalSpacing = layout.verticalSpacing = 0;
        layout.marginHeight = layout.marginWidth = 0;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));

        // Add button references.  These references will be used when buttons
        // are to enabled or disabled based of record index. e.g. for first record
        // First and Previous buttons will be disabled.
        dataHolder.setData(FIRST, firstRecordButton);
        dataHolder.setData(NEXT, nextRecordButton);
        dataHolder.setData(PREV, previousRecordButton);
        dataHolder.setData(LAST, lastRecordButton);

        // Listener for the traversal buttons. When a button is clicked the respective
        // CompositeData will be populated in the composite
        SelectionListener listener = new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                if (!(e.widget instanceof Button))
                    return;

                Button traverseButton =(Button)e.widget; 
                // Get the CompositeData respective to the button selected
                CompositeData data = getCompositeData(dataHolder, traverseButton.getText());
                populateCompositeWithCompositeData(toolkit, composite, data);
                enableOrDisableTraversalButtons(dataHolder);   
            }
        };

        firstRecordButton.addSelectionListener(listener);    
        nextRecordButton.addSelectionListener(listener);
        previousRecordButton.addSelectionListener(listener);
        lastRecordButton.addSelectionListener(listener);

        return composite;
    }
    
    /**
     * The CompositeData is set as data with the Composite and using the index, this method will
     * return the corresponding CompositeData
     * @param compositeHolder
     * @param dataIndex
     * @return the CompositeData respective to the index
     */
    private static CompositeData getCompositeData(Composite compositeHolder, String dataIndex)
    {
        List objectData = (List)compositeHolder.getData();
        if (objectData == null || objectData.isEmpty())
        {
            //          TODO
        }

        // Get the index of record to be shown.
        int index = 0;
        if (compositeHolder.getData(INDEX) != null)
        {
            index = (Integer)compositeHolder.getData(INDEX);
        }

        if (FIRST.equals(dataIndex))
        {
            index = 0;
        }
        else if (NEXT.equals(dataIndex))
        {
            index = index + 1;
        }
        else if (PREV.equals(dataIndex))
        {
            index = (index != 0) ? (index = index - 1) : index;
        }
        else if (LAST.equals(dataIndex))
        {
            index = objectData.size() -1;
        }

        // Set the index being shown.
        compositeHolder.setData(INDEX, index);

        return (CompositeData)((Map.Entry)objectData.get(index)).getValue();
    }

    /**
     * Populates the given composite with the CompositeData. Creates required widgets to hold the data types
     * @param toolkit
     * @param parent
     * @param data CompositeData
     */
    @SuppressWarnings("unchecked")
    private static void populateCompositeWithCompositeData(FormToolkit toolkit, Composite parent, CompositeData data)
    {
        Control[] oldControls = parent.getChildren();       
        for (int i = 0; i < oldControls.length; i++)
        {
            oldControls[i].dispose();
        }
        
        Composite compositeHolder = toolkit.createComposite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(4, false);
        layout.horizontalSpacing = 10;
        compositeHolder.setLayout(layout);
        compositeHolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));       
        
        
        // ItemNames in composite data
        List<String> itemNames = new ArrayList<String>(data.getCompositeType().keySet());

        for (String itemName : itemNames)
        {
            OpenType itemType = data.getCompositeType().getType(itemName);
            Label keyLabel = toolkit.createLabel(compositeHolder, itemName, SWT.TRAIL);
            GridData layoutData = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
            layoutData.minimumWidth = 70;
            keyLabel.setLayoutData(layoutData);

            if (itemType.isArray())
            {
                OpenType type = ((ArrayType)itemType).getElementOpenType();
                //  If Byte array and mimetype is text, convert to text string
                if (type.getClassName().equals(Byte.class.getName()))
                {
                    String mimeType = null; 
                    String encoding = null;
                    if (data.containsKey("MimeType"))
                    {
                        mimeType = (String)data.get("MimeType");
                    }
                    if (data.containsKey("Encoding"))
                    {
                        encoding = (String)data.get("Encoding");
                    }
                    
                    if (encoding == null || encoding.length() == 0)
                    {
                        encoding = Charset.defaultCharset().name();
                    }

                    if ("text/plain".equals(mimeType))
                    {
                        convertByteArray(toolkit, compositeHolder, data, itemName, encoding);
                    }
                    else
                    {
                        setNotSupportedDataType(toolkit, compositeHolder);
                    }                        
                }
                // If array of any other supported type, show as a list of String array
                else if (SUPPORTED_ARRAY_DATATYPES.contains(type.getClassName()))
                {
                    convertArrayItemForDisplay(compositeHolder, data, itemName);
                }
                else
                {
                    setNotSupportedDataType(toolkit, compositeHolder);
                }
            }
            else if (itemType instanceof TabularType)
            {
                Composite composite = toolkit.createComposite(compositeHolder, SWT.NONE);
                composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
                layout = new GridLayout();
                layout.marginHeight = 0;
                layout.marginWidth = 0;
                composite.setLayout(layout);
                createTabularDataHolder(toolkit, composite, (TabularDataSupport)data.get(itemName));
            }
            else
            {
                Text valueText = toolkit.createText(compositeHolder, String.valueOf(data.get(itemName)), SWT.READ_ONLY | SWT.BORDER);
                valueText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
            }
        }   
        
        // layout the composite after creating new widgets.
        parent.layout();
    } //end of method
  
    
    private static void convertByteArray(FormToolkit toolkit, Composite compositeHolder, CompositeData data, String itemName, String encoding)
    {
        Byte[] arrayItems = (Byte[])data.get(itemName);
        byte[] byteArray = new byte[arrayItems.length];

        for (int i = 0; i < arrayItems.length; i++)
        {
            byteArray[i] = arrayItems[i];
        }
        try
        {
            String textMessage = new String(byteArray, encoding);

            Text valueText = toolkit.createText(compositeHolder, textMessage, SWT.READ_ONLY | SWT.BORDER |
                    SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
            GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
            gridData.heightHint = 300;
            valueText.setLayoutData(gridData);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private static Shell getShell()
    {
        Shell shell = Display.getCurrent().getActiveShell();

        // Under linux GTK getActiveShell returns null so we need to make a new shell for display.
        // Under windows this is fine.
        if (shell == null)
        {
            // This occurs under linux gtk
            shell = new Shell(Display.getCurrent(), SWT.BORDER | SWT.CLOSE | SWT.MIN | SWT.MAX);
        }

        return shell;
    }

    private static int showBox(String title, String message, int icon)
    {
        MessageBox messageBox = new MessageBox(getShell(), icon);
        messageBox.setMessage(message);
        messageBox.setText(title);

        return messageBox.open();
    }

    public static int popupInfoMessage(String title, String message)
    {
        return showBox(title, message, SWT.ICON_INFORMATION | SWT.OK);
    }
    
    public static int popupErrorMessage(String title, String message)
    {
        return showBox(title, message, SWT.ICON_ERROR | SWT.OK);
    }

    public static int popupConfirmationMessage(String title, String message)
    {
        return showBox(title, message,SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
    }

    
    public static Shell createPopupShell(String title, int width, int height)
    {
        Display display = Display.getCurrent();
        Shell shell = new Shell(display, SWT.BORDER | SWT.CLOSE | SWT.MIN |SWT.MAX);
        shell.setText(title);
        shell.setLayout(new GridLayout());       
        int x = display.getBounds().width;
        int y = display.getBounds().height;
        shell.setBounds(x/4, y/4, width, height); 
        
        return shell;
    }
    
    /**
     * Creates a List widget for displaying array of strings
     * @param compositeHolder
     * @param data - containing the array item value
     * @param itemName - item name
     */
    private static void convertArrayItemForDisplay(Composite compositeHolder, CompositeData data, String itemName)
    {
        Object[] arrayItems = (Object[])data.get(itemName);
        String[] items = new String[arrayItems.length];
        for (int i = 0; i < arrayItems.length; i++)
        {
            items[i] = String.valueOf(arrayItems[i]);
        }
        org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List(compositeHolder,
                SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        list.setItems(items);
        //list.setBackground(compositeHolder.getBackground());
        list.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
    }
    
    private static void setNotSupportedDataType(FormToolkit toolkit, Composite compositeHolder)
    {
        Text valueText = toolkit.createText(compositeHolder, "--- Content can not be displayed ---", SWT.READ_ONLY);
        valueText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
    }
    
    /**
     * Converts the input string to displayable format by converting some character case or inserting space
     * @param input
     * @return formatted string
     */
    public static String getDisplayText(String input)
    {
        StringBuffer result = new StringBuffer(input);
        if (Character.isLowerCase(result.charAt(0)))
        {
            result.setCharAt(0, Character.toUpperCase(result.charAt(0)));
        }
        for (int i = 1; i < input.length(); i++)
        {
            if (Character.isUpperCase(result.charAt(i)) && !Character.isWhitespace(result.charAt(i - 1))
                                                        && Character.isLowerCase(result.charAt(i - 1)))
            {
                result.insert(i, " ");
                i++;
            }
            else if (Character.isLowerCase(result.charAt(i)) && Character.isWhitespace(result.charAt(i - 1)))
            {
                result.setCharAt(i, Character.toUpperCase(result.charAt(i)));
            }
                
        }
        
        return result.toString();
    }
    
    /**
     * Disposes the children of given Composite if not null and not already disposed
     * @param parent composite
     */
    public static void disposeChildren(Composite parent)
    {
        if (parent == null || parent.isDisposed())
            return;
        
        Control[] oldControls = parent.getChildren();        
        for (int i = 0; i < oldControls.length; i++)
        {
            oldControls[i].dispose();
        }
    }       
    
    public static char[] getHash(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        byte[] data = text.getBytes("utf-8");

        MessageDigest md = MessageDigest.getInstance("MD5");

        for (byte b : data)
        {
            md.update(b);
        }

        byte[] digest = md.digest();

        char[] hash = new char[digest.length ];

        int index = 0;
        for (byte b : digest)
        {            
            hash[index++] = (char) b;
        }

        return hash;
    }
    
    private static class TabularDataComparator implements java.util.Comparator<Map.Entry>
    {
        public int compare(Map.Entry data1, Map.Entry data2)
        {
            if (data1.getKey() instanceof List)
            {
                Object obj1 = ((List)data1.getKey()).get(0);                
                Object obj2 = ((List)data2.getKey()).get(0);
                String str1 = obj1.toString();
                String str2 = obj2.toString();
                if (obj1 instanceof String)
                {
                    return str1.compareTo(str2);
                }
                
                try
                {
                    return Long.valueOf(str1).compareTo(Long.valueOf(str2));
                }
                catch (Exception ex)
                {
                    return -1;
                }
            }
           
            return -1;
        }
    }
}
