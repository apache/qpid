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

import static org.apache.qpid.management.ui.Constants.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ManagedServer;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.exceptions.InfoRequiredException;
import org.apache.qpid.management.ui.jmx.JMXServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.part.ViewPart;

/**
 * Navigation View for navigating the managed servers and managed beans on
 * those servers
 * @author Bhupendra Bhardwaj
 */
public class NavigationView extends ViewPart
{
    public static final String ID = "org.apache.qpid.management.ui.navigationView";
    public static final String INI_FILENAME = System.getProperty("user.home") + File.separator + "qpidManagementConsole.ini";

    private static final String INI_SERVERS = "Servers";
    private static final String INI_QUEUES = QUEUE + "s";
    private static final String INI_CONNECTIONS = CONNECTION + "s";
    private static final String INI_EXCHANGES = EXCHANGE + "s";

    private TreeViewer _treeViewer = null;
    private TreeObject _serversRootNode = null;

    private PreferenceStore _preferences;
    // Map of connected servers
    private HashMap<ManagedServer, TreeObject> _managedServerMap = new HashMap<ManagedServer, TreeObject>();

    private void createTreeViewer(Composite parent)
    {
        _treeViewer = new TreeViewer(parent);
        _treeViewer.setContentProvider(new ContentProviderImpl());
        _treeViewer.setLabelProvider(new LabelProviderImpl());
        _treeViewer.setSorter(new ViewerSorterImpl());

        // layout the tree viewer below the label field, to cover the area
        GridData layoutData = new GridData();
        layoutData = new GridData();
        layoutData.grabExcessHorizontalSpace = true;
        layoutData.grabExcessVerticalSpace = true;
        layoutData.horizontalAlignment = GridData.FILL;
        layoutData.verticalAlignment = GridData.FILL;
        _treeViewer.getControl().setLayoutData(layoutData);
        _treeViewer.setUseHashlookup(true);

        createListeners();
    }

    /**
     * Creates listeners for the JFace treeviewer
     */
    private void createListeners()
    {
        _treeViewer.addDoubleClickListener(new IDoubleClickListener()
            {
                public void doubleClick(DoubleClickEvent event)
                {
                    IStructuredSelection ss = (IStructuredSelection) event.getSelection();
                    if ((ss == null) || (ss.getFirstElement() == null))
                    {
                        return;
                    }

                    boolean state = _treeViewer.getExpandedState(ss.getFirstElement());
                    _treeViewer.setExpandedState(ss.getFirstElement(), !state);
                }
            });

        _treeViewer.addTreeListener(new ITreeViewerListener()
            {
                public void treeExpanded(TreeExpansionEvent event)
                {
                    getSite().getShell().getDisplay().asyncExec(
                            new Runnable()
                            {
                                public void run()
                                {
                                     _treeViewer.refresh();
                                }
                            });
                }

                public void treeCollapsed(TreeExpansionEvent event)
                {
                    getSite().getShell().getDisplay().asyncExec(
                            new Runnable()
                            {
                                public void run()
                                {
                                     _treeViewer.refresh();
                                }
                            });
                }
            });

        // This listener is for popup menu, which pops up if a queue,exchange or connection is selected
        // with right click.
        _treeViewer.getTree().addListener(SWT.MenuDetect, new Listener()
            {
                Display display = getSite().getShell().getDisplay();
                final Shell shell = new Shell(display);

                public void handleEvent(Event event)
                {
                    Tree widget = (Tree) event.widget;
                    TreeItem[] items = widget.getSelection();
                    if (items == null)
                    {
                        return;
                    }

                    // Get the selected node
                    final TreeObject selectedNode = (TreeObject) items[0].getData();
                    final TreeObject parentNode = selectedNode.getParent();

                    // This popup is only for mbeans and only connection,exchange and queue types
                    if ((parentNode == null) || !MBEAN.equals(selectedNode.getType())
                            || !(CONNECTION.equals(parentNode.getName()) || QUEUE.equals(parentNode.getName())
                                || EXCHANGE.equals(parentNode.getName())))
                    {
                        return;
                    }

                    Menu menu = new Menu(shell, SWT.POP_UP);
                    MenuItem item = new MenuItem(menu, SWT.PUSH);
                    // Add the action item, which will remove the node from the tree if selected
                    item.setText(ACTION_REMOVE_MBEANNODE);
                    item.addListener(SWT.Selection, new Listener()
                        {
                            public void handleEvent(Event e)
                            {
                                removeManagedObject(parentNode, (ManagedBean) selectedNode.getManagedObject());
                                _treeViewer.refresh();
                                // set the selection to the parent node
                                _treeViewer.setSelection(new StructuredSelection(parentNode));
                            }
                        });
                    menu.setLocation(event.x, event.y);
                    menu.setVisible(true);
                    while (!menu.isDisposed() && menu.isVisible())
                    {
                        if (!display.readAndDispatch())
                        {
                            display.sleep();
                        }
                    }

                    menu.dispose();
                }
            });
    }

    /**
     * Creates Qpid Server connection using JMX RMI protocol
     * @param server
     * @throws Exception
     */
    private void createRMIServerConnection(ManagedServer server) throws Exception
    {
        // Currently Qpid Management Console only supports JMX MBeanServer
        ServerRegistry serverRegistry = new JMXServerRegistry(server);
        ApplicationRegistry.addServer(server, serverRegistry);
    }

    /**
     * Adds a new server node in the navigation view if server connection is successful.
     * @param transportProtocol
     * @param host
     * @param port
     * @param domain
     * @throws Exception
     */
    public void addNewServer(String transportProtocol, String host, int port, String domain, String user, String pwd)
        throws Exception
    {
        String serverAddress = host + ":" + port;
        String url = null;
        ManagedServer managedServer = new ManagedServer(host, port, domain, user, pwd);

        if ("RMI".equals(transportProtocol))
        {
            url = managedServer.getUrl();
            List<TreeObject> list = _serversRootNode.getChildren();
            for (TreeObject node : list)
            {
                ManagedServer nodeServer = (ManagedServer)node.getManagedObject();
                if (url.equals(nodeServer.getUrl()))
                {
                    // Server is already in the list of added servers, so now connect it.
                    // Set the server node as selected and then connect it.
                    _treeViewer.setSelection(new StructuredSelection(node));
                    reconnect(user, pwd);

                    return;
                }
            }

            // The server is not in the list of already added servers, so now connect and add it.
            managedServer.setName(serverAddress);
            createRMIServerConnection(managedServer);
        }
        else
        {
            throw new InfoRequiredException(transportProtocol + " transport is not supported");
        }

        // Server connection is successful. Now add the server in the tree
        TreeObject serverNode = new TreeObject(serverAddress, NODE_TYPE_SERVER);
        serverNode.setManagedObject(managedServer);
        _serversRootNode.addChild(serverNode);

        // Add server in the connected server map
        _managedServerMap.put(managedServer, serverNode);

        // populate the server tree
        try
        {
            populateServer(serverNode);
        }
        catch (SecurityException ex)
        {
            disconnect(managedServer);
            throw ex;
        }

        // Add the Queue/Exchanges/Connections from config file into the navigation tree
        addConfiguredItems(managedServer);

        _treeViewer.refresh();

        // save server address in file
        addServerInConfigFile(serverAddress);
    }

    /**
     * Create the config file, if it doesn't already exist.
     * Exits the application if the file could not be created.
     */
    private void createConfigFile()
    {
        File file = new File(INI_FILENAME);
        try
        {
            if (!file.exists())
            {
                file.createNewFile();
            }
        }
        catch (IOException ex)
        {
            System.out.println("Could not write to the file " + INI_FILENAME);
            System.out.println(ex);
            System.exit(1);
        }
    }

    /**
     * Server addresses are stored in a file. When user launches the application again, the
     * server addresses are picked up from the file and shown in the navigfation view. This method
     * adds the server address in a file, when a new server is added in the navigation view.
     * @param serverAddress
     */
    private void addServerInConfigFile(String serverAddress)
    {
        // Check if the address already exists
        List<String> list = getServerListFromFile();
        if ((list != null) && list.contains(serverAddress))
        {
            return;
        }

        // Get the existing server list and add to that
        String servers = _preferences.getString(INI_SERVERS);
        String value = (servers.length() != 0) ? (servers + "," + serverAddress) : serverAddress;
        _preferences.putValue(INI_SERVERS, value);
        try
        {
            _preferences.save();
        }
        catch (IOException ex)
        {
            System.err.println("Could not add " + serverAddress + " in " + INI_SERVERS + " (" + INI_FILENAME + ")");
            System.out.println(ex);
        }
    }

    /**
     * Adds the item (Queue/Exchange/Connection) to the config file
     * @param server
     * @param virtualhost
     * @param type - (Queue or Exchange or Connection)
     * @param name - item name
     */
    private void addItemInConfigFile(TreeObject node)
    {
        ManagedBean mbean = (ManagedBean) node.getManagedObject();
        String server = mbean.getServer().getName();
        String virtualhost = mbean.getVirtualHostName();
        String type = node.getParent().getName() + "s";
        String name = node.getName();
        String itemKey = server + "." + virtualhost + "." + type;

        // Check if the item already exists in the config file
        List<String> list = getConfiguredItemsFromFile(itemKey);
        if ((list != null) && list.contains(name))
        {
            return;
        }

        // Add this item to the existing list of items
        String items = _preferences.getString(itemKey);
        String value = (items.length() != 0) ? (items + "," + name) : name;
        _preferences.putValue(itemKey, value);
        try
        {
            _preferences.save();
        }
        catch (IOException ex)
        {
            System.err.println("Could not add " + name + " in " + itemKey + " (" + INI_FILENAME + ")");
            System.out.println(ex);
        }
    }

    private void removeItemFromConfigFile(TreeObject node)
    {
        ManagedBean mbean = (ManagedBean) node.getManagedObject();
        String server = mbean.getServer().getName();
        String vHost = mbean.getVirtualHostName();
        String type = node.getParent().getName() + "s";
        String itemKey = server + "." + vHost + "." + type;

        List<String> list = getConfiguredItemsFromFile(itemKey);
        if (list.contains(node.getName()))
        {
            list.remove(node.getName());
            String value = "";
            for (String item : list)
            {
                value += item + ",";
            }

            value = (value.lastIndexOf(",") != -1) ? value.substring(0, value.lastIndexOf(",")) : value;

            _preferences.putValue(itemKey, value);
            try
            {
                _preferences.save();
            }
            catch (IOException ex)
            {
                System.err.println("Error in updating the config file " + INI_FILENAME);
                System.out.println(ex);
            }
        }
    }

    /**
     * Queries the qpid server for MBeans and populates the navigation view with all MBeans for
     * the given server node.
     * @param serverNode
     */
    private void populateServer(TreeObject serverNode) throws Exception
    {
        ManagedServer server = (ManagedServer) serverNode.getManagedObject();
        String domain = server.getDomain();
        if (!domain.equals(ALL))
        {
            TreeObject domainNode = new TreeObject(domain, NODE_TYPE_DOMAIN);
            domainNode.setParent(serverNode);

            populateDomain(domainNode);
        }
        else
        {
            List<TreeObject> domainList = new ArrayList<TreeObject>();
            List<String> domains = MBeanUtility.getAllDomains(server);

            for (String domainName : domains)
            {
                TreeObject domainNode = new TreeObject(domainName, NODE_TYPE_DOMAIN);
                domainNode.setParent(serverNode);

                domainList.add(domainNode);
                populateDomain(domainNode);
            }
        }
    }

    /**
     * Queries the Qpid Server and populates the given domain node with all MBeans undser that domain.
     * @param domain
     * @throws IOException
     * @throws Exception
     */
    private void populateDomain(TreeObject domain) throws IOException, Exception
    {
        ManagedServer server = (ManagedServer) domain.getParent().getManagedObject();

        // Now populate the mbenas under those types
        List<ManagedBean> mbeans = MBeanUtility.getManagedObjectsForDomain(server, domain.getName());
        for (ManagedBean mbean : mbeans)
        {
            mbean.setServer(server);
            ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(server);
            serverRegistry.addManagedObject(mbean);

            // Add all mbeans other than Connections, Exchanges and Queues. Because these will be added
            // manually by selecting from MBeanView
            if (!(mbean.isConnection() || mbean.isExchange() || mbean.isQueue()))
            {
                addManagedBean(domain, mbean);
            }
        }
        // To make it work with the broker without virtual host implementation.
        // This will add the default nodes to the domain node
        boolean hasVirtualHost = false;
        for (TreeObject child : domain.getChildren())
        {
            if (child.getName().startsWith(VIRTUAL_HOST))
            {
                hasVirtualHost = true;
                break;      
            }
        }
        
        if (!hasVirtualHost){
            addDefaultNodes(domain);
        }
    }

    /**
     * Add these three types - Connection, Exchange, Queue
     * By adding these, these will always be available, even if there are no mbeans under thse types
     * This is required because, the mbeans will be added from mbeanview, by selecting from the list
     * @param parent Node
     */
    private void addDefaultNodes(TreeObject parent)
    {
        TreeObject typeChild = new TreeObject(CONNECTION, NODE_TYPE_MBEANTYPE);
        typeChild.setParent(parent);
        typeChild.setVirtualHost(parent.getVirtualHost());
        typeChild = new TreeObject(EXCHANGE, NODE_TYPE_MBEANTYPE);
        typeChild.setParent(parent);
        typeChild.setVirtualHost(parent.getVirtualHost());
        typeChild = new TreeObject(QUEUE, NODE_TYPE_MBEANTYPE);
        typeChild.setParent(parent);
        typeChild.setVirtualHost(parent.getVirtualHost());
        
        // Add common notification node for virtual host
        TreeObject notificationNode = new TreeObject(NOTIFICATIONS, NOTIFICATIONS);
        notificationNode.setParent(parent);
        notificationNode.setVirtualHost(parent.getVirtualHost());
    }

    /**
     * Checks if a particular mbeantype is already there in the navigation view for a domain.
     * This is used while populating domain with mbeans.
     * @param parent
     * @param typeName
     * @return Node if given mbeantype already exists, otherwise null
     */
    private TreeObject getMBeanTypeNode(TreeObject parent, String typeName)
    {
        List<TreeObject> childNodes = parent.getChildren();
        for (TreeObject child : childNodes)
        {
            if ((NODE_TYPE_MBEANTYPE.equals(child.getType()) || NODE_TYPE_TYPEINSTANCE.equals(child.getType()))
                    && typeName.equals(child.getName()))
            {
                return child;
            }
        }

        return null;
    }

    private boolean doesMBeanNodeAlreadyExist(TreeObject typeNode, String mbeanName)
    {
        List<TreeObject> childNodes = typeNode.getChildren();
        for (TreeObject child : childNodes)
        {
            if (MBEAN.equals(child.getType()) && mbeanName.equals(child.getName()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Adds the given MBean to the given domain node. Creates Notification node for the MBean.
     * sample ObjectNames -
     * org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=localhost
     * org.apache.qpid:type=VirtualHost.Queue,VirtualHost=test,name=ping_1
     * @param domain
     * @param mbean
     * @throws Exception
     */
    private void addManagedBean(TreeObject domain, ManagedBean mbean) // throws Exception
    {
        String name = mbean.getName();
        // Split the mbean type into array of Strings, to create hierarchy
        // eg. type=VirtualHost.VirtualHostManager,VirtualHost=localhost will be:
        // localhost->VirtualHostManager
        // eg. type=org.apache.qpid:type=VirtualHost.Queue,VirtualHost=test,name=ping will be:
        // test->Queue->ping
        String[] types = mbean.getType().split("\\.");
        TreeObject typeNode = null;
        TreeObject parentNode = domain;

        // Run this loop till all nodes(hierarchy) for this mbean are created. This loop only creates
        // all the required parent nodes for the mbean
        for (int i = 0; i < types.length; i++)
        {
            String type = types[i];
            String valueOftype = mbean.getProperty(type);
            // If value is not null, then there will be a parent node for this mbean
            // eg. for type=VirtualHost the value is "test"
            typeNode = getMBeanTypeNode(parentNode, type);

            // create the type node if not already created
            if (typeNode == null)
            {
                // If the ObjectName doesn't have name property, that means there will be only one instance
                // of this mbean for given "type". So there will be no type node created for this mbean.
                if ((name == null) && (i == (types.length - 1)))
                {
                    break;
                }

                // create a node for "type"
                typeNode = createTypeNode(parentNode, type);
                if (!type.equals(VIRTUAL_HOST))
                {
                    typeNode.setVirtualHost(mbean.getVirtualHostName());
                }
            }

            // now type node create becomes the parent node for next node in hierarchy
            parentNode = typeNode;

            /*
             * Now create instances node for this type if value exists.
             */
            if (valueOftype == null)
            {
                // No instance node will be created when value is null (eg type=Queue)
                break;
            }

            // For different virtual hosts, the nodes with given value will be created.
            // eg type=VirtualHost, value=test
            typeNode = getMBeanTypeNode(parentNode, valueOftype);
            if (typeNode == null)
            {
                typeNode = createTypeInstanceNode(parentNode, valueOftype);
                typeNode.setVirtualHost(mbean.getVirtualHostName());

                // Create default nodes for VHost instances
                if (type.equals(VIRTUAL_HOST))
                {
                    addDefaultNodes(typeNode);
                }
            }

            parentNode = typeNode;
        }

        if (typeNode == null)
        {
            typeNode = parentNode;
        }

        // Check if an MBean is already added
        if (doesMBeanNodeAlreadyExist(typeNode, name))
        {
            return;
        }

        // Add the mbean node now
        TreeObject mbeanNode = new TreeObject(mbean);
        mbeanNode.setParent(typeNode);

        // Add the mbean to the config file
        if (mbean.isQueue() || mbean.isExchange() || mbean.isConnection())
        {
            addItemInConfigFile(mbeanNode);
        }

        // Add notification node
        // TODO: show this only if the mbean sends any notification
        //TreeObject notificationNode = new TreeObject(NOTIFICATION, NOTIFICATION);
        //notificationNode.setParent(mbeanNode);
    }

    private TreeObject createTypeNode(TreeObject parent, String name)
    {
        TreeObject typeNode = new TreeObject(name, NODE_TYPE_MBEANTYPE);
        typeNode.setParent(parent);

        return typeNode;
    }

    private TreeObject createTypeInstanceNode(TreeObject parent, String name)
    {
        TreeObject typeNode = new TreeObject(name, NODE_TYPE_TYPEINSTANCE);
        typeNode.setParent(parent);

        return typeNode;
    }

    /**
     * Removes all the child nodes of the given parent node. Used when closing a server.
     * @param parent
     */
    private void removeManagedObject(TreeObject parent)
    {
        List<TreeObject> list = parent.getChildren();
        for (TreeObject child : list)
        {
            removeManagedObject(child);
        }

        list.clear();
    }

    /**
     * Removes the mbean from the tree
     * @param parent
     * @param mbean
     */
    private void removeManagedObject(TreeObject parent, ManagedBean mbean)
    {
        List<TreeObject> list = parent.getChildren();
        TreeObject objectToRemove = null;
        for (TreeObject child : list)
        {
            if (MBEAN.equals(child.getType()))
            {
                String name = (mbean.getName() != null) ? mbean.getName() : mbean.getType();
                if (child.getName().equals(name))
                {
                    objectToRemove = child;

                    break;
                }
            }
            else
            {
                removeManagedObject(child, mbean);
            }
        }

        if (objectToRemove != null)
        {
            list.remove(objectToRemove);
            removeItemFromConfigFile(objectToRemove);
        }

    }

    /**
     * Closes the Qpid server connection
     */
    public void disconnect() throws Exception
    {
        TreeObject selectedNode = getSelectedServerNode();
        ManagedServer managedServer = (ManagedServer) selectedNode.getManagedObject();
        disconnect(managedServer);
    }
    
    private void disconnect(ManagedServer managedServer) throws Exception
    {
        if (!_managedServerMap.containsKey(managedServer))
        {
            return;
        }

        // Close server connection
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(managedServer);
        if (serverRegistry == null) // server connection is already closed
        {
            return;
        }

        serverRegistry.closeServerConnection();
        // Add server to the closed server list and the worker thread will remove the server from required places.
        ApplicationRegistry.serverConnectionClosed(managedServer);
    }

    /**
     * Connects the selected server node
     * @throws Exception
     */
    public void reconnect(String user, String password) throws Exception
    {
        TreeObject selectedNode = getSelectedServerNode();
        ManagedServer managedServer = (ManagedServer) selectedNode.getManagedObject();
        if (_managedServerMap.containsKey(managedServer))
        {
            throw new InfoRequiredException("Server " + managedServer.getName() + " is already connected");
        }

        managedServer.setUser(user);
        managedServer.setPassword(password);
        createRMIServerConnection(managedServer);

        // put the server in the managed server map
        _managedServerMap.put(managedServer, selectedNode);

        try
        {
            // populate the server tree now
            populateServer(selectedNode);
        }
        catch (SecurityException ex)
        {
            disconnect(managedServer);
            throw ex;
        }
        

        // Add the Queue/Exchanges/Connections from config file into the navigation tree
        addConfiguredItems(managedServer);

        _treeViewer.refresh();
    }

    /**
     * Adds the items(queues/exchanges/connectins) from config file to the server tree
     * @param server
     */
    private void addConfiguredItems(ManagedServer server)
    {
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(server);
        List<String> list = serverRegistry.getVirtualHosts();
        for (String virtualHost : list)
        {
            // Add Queues
            String itemKey = server.getName() + "." + virtualHost + "." + INI_QUEUES;
            List<String> items = getConfiguredItemsFromFile(itemKey);
            List<ManagedBean> mbeans = serverRegistry.getQueues(virtualHost);
            addConfiguredItems(items, mbeans);

            // Add Exchanges
            itemKey = server.getName() + "." + virtualHost + "." + INI_EXCHANGES;
            items = getConfiguredItemsFromFile(itemKey);
            mbeans = serverRegistry.getExchanges(virtualHost);
            addConfiguredItems(items, mbeans);

            // Add Connections
            itemKey = server.getName() + "." + virtualHost + "." + INI_CONNECTIONS;
            items = getConfiguredItemsFromFile(itemKey);
            mbeans = serverRegistry.getConnections(virtualHost);
            addConfiguredItems(items, mbeans);
        }
    }

    /**
     * Gets the mbeans corresponding to the items and adds those to the navigation tree
     * @param items
     * @param mbeans
     */
    private void addConfiguredItems(List<String> items, List<ManagedBean> mbeans)
    {
        if ((items == null) || (items.isEmpty() | (mbeans == null)) || mbeans.isEmpty())
        {
            return;
        }

        for (String item : items)
        {
            for (ManagedBean mbean : mbeans)
            {
                if (item.equals(mbean.getName()))
                {
                    addManagedBean(mbean);

                    break;
                }
            }
        }
    }

    /**
     * Closes the Qpid server connection if not already closed and removes the server node from the navigation view and
     * also from the ini file stored in the system.
     * @throws Exception
     */
    public void removeServer() throws Exception
    {
        disconnect();

        // Remove from the Tree
        String serverNodeName = getSelectedServerNode().getName();
        List<TreeObject> list = _serversRootNode.getChildren();
        TreeObject objectToRemove = null;
        for (TreeObject child : list)
        {
            if (child.getName().equals(serverNodeName))
            {
                objectToRemove = child;

                break;
            }
        }

        if (objectToRemove != null)
        {
            list.remove(objectToRemove);
        }

        _treeViewer.refresh();

        // Remove from the ini file
        removeServerFromConfigFile(serverNodeName);
    }

    private void removeServerFromConfigFile(String serverNodeName)
    {
        List<String> serversList = getServerListFromFile();
        serversList.remove(serverNodeName);

        String value = "";
        for (String item : serversList)
        {
            value += item + ",";
        }

        value = (value.lastIndexOf(",") != -1) ? value.substring(0, value.lastIndexOf(",")) : value;

        _preferences.putValue(INI_SERVERS, value);

        try
        {
            _preferences.save();
        }
        catch (IOException ex)
        {
            System.err.println("Error in updating the config file " + INI_FILENAME);
            System.out.println(ex);
        }
    }

    /**
     * @return the server addresses from the ini file
     * @throws Exception
     */
    private List<String> getServerListFromFile()
    {
        return getConfiguredItemsFromFile(INI_SERVERS);
    }

    /**
     * Returns the list of items from the config file.
     * sample ini file:
     * Servers=localhost:8999,127.0.0.1:8999
     * localhost.virtualhost1.Queues=queue1,queue2
     * localhost.virtualhost1.Exchanges=exchange1,exchange2
     * localhost.virtualhost2.Connections=conn1
     * @param key
     * @return
     */
    private List<String> getConfiguredItemsFromFile(String key)
    {
        List<String> list = new ArrayList<String>();
        String items = _preferences.getString(key);
        if (items.length() != 0)
        {
            String[] array = items.split(",");
            for (String item : array)
            {
                list.add(item);
            }
        }

        return list;
    }

    public TreeObject getSelectedServerNode() throws Exception
    {
        IStructuredSelection ss = (IStructuredSelection) _treeViewer.getSelection();
        TreeObject selectedNode = (TreeObject) ss.getFirstElement();
        if (ss.isEmpty() || (selectedNode == null) || (!selectedNode.getType().equals(NODE_TYPE_SERVER)))
        {
            throw new InfoRequiredException("Please select the server");
        }

        return selectedNode;
    }

    /**
     * This is a callback that will allow us to create the viewer and initialize
     * it.
     */
    public void createPartControl(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginHeight = 2;
        gridLayout.marginWidth = 2;
        gridLayout.horizontalSpacing = 0;
        gridLayout.verticalSpacing = 2;
        composite.setLayout(gridLayout);

        createTreeViewer(composite);
        _serversRootNode = new TreeObject(NAVIGATION_ROOT, "ROOT");

        _treeViewer.setInput(_serversRootNode);
        // set viewer as selection event provider for MBeanView
        getSite().setSelectionProvider(_treeViewer);

        // Start worker thread to refresh tree for added or removed objects
        (new Thread(new Worker())).start();

        createConfigFile();
        _preferences = new PreferenceStore(INI_FILENAME);

        try
        {
            _preferences.load();
        }
        catch (IOException ex)
        {
            System.out.println(ex);
        }

        // load the list of servers already added from file
        List<String> serversList = getServerListFromFile();
        if (serversList != null)
        {
            for (String serverAddress : serversList)
            {
                String[] server = serverAddress.split(":");
                ManagedServer managedServer = new ManagedServer(server[0], Integer.parseInt(server[1]), "org.apache.qpid");
                TreeObject serverNode = new TreeObject(serverAddress, NODE_TYPE_SERVER);
                serverNode.setManagedObject(managedServer);
                _serversRootNode.addChild(serverNode);
            }
        }

        _treeViewer.refresh();

    }

    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus()
    { }

    public void refresh()
    {
        _treeViewer.refresh();
    }

    /**
     * Content provider class for the tree viewer
     */
    private class ContentProviderImpl implements ITreeContentProvider
    {
        public Object[] getElements(Object parent)
        {
            return getChildren(parent);
        }

        public Object[] getChildren(final Object parentElement)
        {
            final TreeObject node = (TreeObject) parentElement;

            return node.getChildren().toArray(new TreeObject[0]);
        }

        public Object getParent(final Object element)
        {
            final TreeObject node = (TreeObject) element;

            return node.getParent();
        }

        public boolean hasChildren(final Object element)
        {
            final TreeObject node = (TreeObject) element;

            return !node.getChildren().isEmpty();
        }

        public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput)
        {
            // Do nothing
        }

        public void dispose()
        {
            // Do nothing
        }
    }

    /**
     * Label provider class for the tree viewer
     */
    private class LabelProviderImpl extends LabelProvider implements IFontProvider
    {
        public Image getImage(Object element)
        {
            TreeObject node = (TreeObject) element;
            if (node.getType().equals(NOTIFICATIONS))
            {
                return ApplicationRegistry.getImage(NOTIFICATION_IMAGE);
            }
            else if (!node.getType().equals(MBEAN))
            {
                if (_treeViewer.getExpandedState(node))
                {
                    return ApplicationRegistry.getImage(OPEN_FOLDER_IMAGE);
                }
                else
                {
                    return ApplicationRegistry.getImage(CLOSED_FOLDER_IMAGE);
                }

            }
            else
            {
                return ApplicationRegistry.getImage(MBEAN_IMAGE);
            }
        }

        public String getText(Object element)
        {
            TreeObject node = (TreeObject) element;
            if (node.getType().equals(NODE_TYPE_MBEANTYPE))
            {
                return node.getName() + "s";
            }
            else
            {
                return node.getName();
            }
        }

        public Font getFont(Object element)
        {
            TreeObject node = (TreeObject) element;
            if (node.getType().equals(NODE_TYPE_SERVER))
            {
                if (node.getChildren().isEmpty())
                {
                    return ApplicationRegistry.getFont(FONT_NORMAL);
                }
                else
                {
                    return ApplicationRegistry.getFont(FONT_BOLD);
                }
            }

            return ApplicationRegistry.getFont(FONT_NORMAL);
        }
    } // End of LabelProviderImpl

    private class ViewerSorterImpl extends ViewerSorter
    {
        public int category(Object element)
        {
            TreeObject node = (TreeObject) element;
            if (node.getType().equals(MBEAN))
            {
                return 1;
            }
            if (node.getType().equals(NOTIFICATIONS))
            {
                return 2;
            }
            return 3;
        }
    }

    /**
     * Worker thread, which keeps looking for new ManagedObjects to be added and
     * unregistered objects to be removed from the tree.
     * @author Bhupendra Bhardwaj
     */
    private class Worker implements Runnable
    {
        public void run()
        {
            while (true)
            {
                if (!_managedServerMap.isEmpty())
                {
                    refreshRemovedObjects();
                    refreshClosedServerConnections();
                }

                try
                {
                    Thread.sleep(3000);
                }
                catch (Exception ex)
                { }

            } // end of while loop
        } // end of run method.
    } // end of Worker class

    /**
     * Adds the mbean to the navigation tree
     * @param mbean
     * @throws Exception
     */
    public void addManagedBean(ManagedBean mbean) // throws Exception
    {
        TreeObject treeServerObject = _managedServerMap.get(mbean.getServer());
        List<TreeObject> domains = treeServerObject.getChildren();
        TreeObject domain = null;
        for (TreeObject child : domains)
        {
            if (child.getName().equals(mbean.getDomain()))
            {
                domain = child;

                break;
            }
        }

        addManagedBean(domain, mbean);
        _treeViewer.refresh();
    }

    private void refreshRemovedObjects()
    {
        for (ManagedServer server : _managedServerMap.keySet())
        {
            final ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(server);
            if (serverRegistry == null) // server connection is closed
            {
                continue;
            }

            final List<ManagedBean> removalList = serverRegistry.getObjectsToBeRemoved();
            if (removalList != null)
            {
                Display display = getSite().getShell().getDisplay();
                display.syncExec(new Runnable()
                    {
                        public void run()
                        {
                            for (ManagedBean mbean : removalList)
                            {
                                TreeObject treeServerObject = _managedServerMap.get(mbean.getServer());
                                List<TreeObject> domains = treeServerObject.getChildren();
                                TreeObject domain = null;
                                for (TreeObject child : domains)
                                {
                                    if (child.getName().equals(mbean.getDomain()))
                                    {
                                        domain = child;

                                        break;
                                    }
                                }

                                removeManagedObject(domain, mbean);
                                // serverRegistry.removeManagedObject(mbean);
                            }

                            _treeViewer.refresh();
                        }
                    });
            }
        }
    }

    /**
     * Gets the list of closed server connection from the ApplicationRegistry and then removes
     * the closed server nodes from the navigation view
     */
    private void refreshClosedServerConnections()
    {
        final List<ManagedServer> closedServers = ApplicationRegistry.getClosedServers();
        if (closedServers != null)
        {
            Display display = getSite().getShell().getDisplay();
            display.syncExec(new Runnable()
                {
                    public void run()
                    {
                        for (ManagedServer server : closedServers)
                        {
                            removeManagedObject(_managedServerMap.get(server));
                            _managedServerMap.remove(server);
                            ApplicationRegistry.removeServer(server);
                        }

                        _treeViewer.refresh();
                    }
                });
        }
    }

}
