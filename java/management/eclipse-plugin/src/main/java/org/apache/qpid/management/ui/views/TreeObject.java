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

import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ManagedObject;

public class TreeObject
{
    private String _name;
    private String _type;
    private String _url;
    private TreeObject _parent;
    private List<TreeObject> _children = new ArrayList<TreeObject>();
    private ManagedObject _object;
    
    public TreeObject(String name, String type)
    {
       this._name = name;
       this._type = type;
    }
    
    public TreeObject(ManagedObject obj)
    {
        _name = obj.getName();
        if (_name == null && (obj instanceof ManagedBean))
        {
            _name = ((ManagedBean)obj).getType();
        }
        this._type = Constants.MBEAN;
        this._object = obj;
    }
    
    public void addChild(TreeObject child)
    {
        _children.add(child);
    }
    
    public void addChildren(List<TreeObject> subList)
    {
        _children.addAll(subList);
    }
    
    public List<TreeObject> getChildren()
    {
        return _children;
    }
    
    public void setChildren(List<TreeObject> children)
    {
        this._children = children;
    }
    
    public void setName(String value)
    {
        _name = value;
    }
    
    public String getName()
    {
        return _name;
    }
    public String getType()
    {
        return _type;
    }

    public String getUrl()
    {
        return _url;
    }

    public void setUrl(String url)
    {
        this._url = url;
    }

    public ManagedObject getManagedObject()
    {
        return _object;
    }

    public void setManagedObject(ManagedObject obj)
    {
        this._object = obj;
    }
    
    public TreeObject getParent()
    {
        return _parent;
    }
    
    public void setParent(TreeObject parent)
    {
        this._parent = parent;
        
        if (parent != null)
        {
            this._url = parent.getUrl();
            parent.addChild(this);
        }
    }
}
