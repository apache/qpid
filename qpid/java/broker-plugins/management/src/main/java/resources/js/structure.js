/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

if (Array.isArray)
{
    isArray = function (object)
              {
                  return Array.isArray(object);
              };
}
else
{
    isArray = function (object)
              {
                  return object instanceof Array;
              };
}

function BrokerTreeModel()
{
    this.query = "/rest/structure";

    this.onChildrenChange = function(parent, children)
    {
          // fired when the set of children for an object change
    };

    this.onChange = function(object)
    {
          // fired when the properties of an object change
    };

    this.onDelete =  function(object)
    {
          // fired when an object is deleted
    };

}



BrokerTreeModel.prototype.buildModel = function(data)
{
    this.model = data;

};

BrokerTreeModel.prototype.updateModel = function(data)
{
    var thisObj = this;

    function checkForChanges(oldData, data)
    {
        if(oldData.name != data.name)
        {
            thisObj.onChange(data);
        }

        var childChanges = false;
        // Iterate over old childTypes, check all are in new
        for(var propName in oldData)
        {
            var oldChildren = oldData[ propName ];
            if(isArray(oldChildren))
            {

                var newChildren = data[ propName ];

                if(!(newChildren && isArray(newChildren)))
                {
                    childChanges = true;
                }
                else
                {
                    var subChanges = false;
                    // iterate over elements in array, make sure in both, in which case recurse
                    for(var i = 0; i < oldChildren.length; i++)
                    {
                        var matched = false;
                        for(var j = 0; j < newChildren.length; j++)
                        {
                            if(oldChildren[i].id == newChildren[j].id)
                            {
                                checkForChanges(oldChildren[i], newChildren[j]);
                                matched = true;
                                break;
                            }
                        }
                        if(!matched)
                        {
                            subChanges = true;
                        }
                    }
                    if(subChanges == true || oldChildren.length != newChildren.length)
                    {
                        thisObj.onChildrenChange({ id: data.id+propName, _dummyChild: propName, data: data }, newChildren);
                    }
                }
            }
        }

        for(var propName in data)
        {
            var prop = data[ propName ];
            if(isArray(prop))
            {
                if(!(oldData[ propName ] && isArray(oldData[propName])))
                {
                    childChanges = true;
                }
            }
        }

        if(childChanges)
        {
            var children;
            thisObj.getChildren(data, function(theChildren) { children = theChildren });
            thisObj.onChildrenChange(data, children);
        }
    }

    var oldData = this.model;
    this.model = data;

    checkForChanges(oldData, data);
};


BrokerTreeModel.prototype.fetchItemByIdentity = function(id)
{
    function fetchItem(id, data)
    {
        if(data.id == id)
        {
            return data;
        }
        else if(id.indexOf(data.id) == 0)
        {
            return { id: id, _dummyChild: id.substring(id.length), data: data };
        }
        else
        {
            for(var propName in data)
            {
                var prop = data[ propName ];
                if(isArray(prop))
                {
                    for(var i = 0 ; i < prop.length; i++)
                    {
                        var item = fetchItem(id, prop[i]);
                        if( item )
                        {
                            return item;
                        }
                    }
                }
            }
            return null;
        }
    }

    return fetchItem(id, this.model);
};

BrokerTreeModel.prototype.getChildren = function(parentItem, onComplete)
{
    if(parentItem._dummyChild)
    {
        onComplete(parentItem.data[ parentItem._dummyChild ]);
    }
    else
    {
        var children = [];
        for(var propName in parentItem)
        {
            var prop = parentItem[ propName ];

            if(isArray(prop))
            {
                children.push({ id: parentItem.id+propName, _dummyChild: propName, data: parentItem });
            }
        }
        onComplete( children );
    }
};

BrokerTreeModel.prototype.getIdentity = function (item)
{
    return item.id;
};

BrokerTreeModel.prototype.getLabel = function (item)
{
    if(item)
    {
        if(item._dummyChild)
        {
            return item._dummyChild;
        }
        else
        {
            return item.name;
        }
    }
};

BrokerTreeModel.prototype.getRoot = function (onItem)
{
    onItem( this.model );
};

BrokerTreeModel.prototype.mayHaveChildren = function (item)
{
    if(item._dummyChild)
    {
        return true;
    }
    else
    {
        for(var propName in item)
        {
            var prop = item[ propName ];
            if(isArray(prop))
            {
                return true;
            }
        }
        return false;
    }
};

require(["dojo/io-query", "dojo/domReady!"],
    function(ioQuery)
    {

        BrokerTreeModel.prototype.relocate = function (item)
        {

            function findItemDetails(item, details, type, object)
            {
                if(item.id == object.id)
                {
                    details.type = type;
                    details[ type ] = object.name;
                }
                else
                {
                    details[ type ] = object.name;

                    // iterate over children
                    for(var propName in object)
                    {
                        var prop = object[ propName ];
                        if(isArray(prop))
                        {
                            for(var i = 0 ; i < prop.length; i++)
                            {
                                findItemDetails(item, details, propName.substring(0, propName.length-1),prop[i])

                                if(details.type)
                                {
                                    break;
                                }
                            }
                        }
                        if(details.type)
                        {
                            break;
                        }
                    }



                    if(!details.type)
                    {
                        details[ type ] = null;
                    }
                }
            }

            var details = new Object();

            findItemDetails(item, details, "broker", this.model);

            var uri;


            if(details.type == "virtualhost")
            {
                uri = "/vhost?" + ioQuery.objectToQuery({ vhost: details.virtualhost });
            }
            else if(details.type == "exchange")
            {
                uri = "/exchange?" + ioQuery.objectToQuery({ vhost: details.virtualhost , exchange: details.exchange });
            }
            else if(details.type == "queue")
            {
                uri = "/queue?" + ioQuery.objectToQuery({ vhost: details.virtualhost , queue: details.queue});
            }
            else if(details.type == "connection")
            {
                uri = "/connection?" + ioQuery.objectToQuery({ vhost: details.virtualhost , connection: details.connection});
            }
            else if(details.type == 'port')
            {
                uri = "/port?" + ioQuery.objectToQuery({ port: details.port});
            }

            if(uri)
            {
                window.location = uri;
            }
        };
    });

var structure;

require(["dojo/_base/xhr", "dojo/domReady!"],
	     function(xhr)
	     {


             BrokerTreeModel.prototype.update = function()
             {
                 var thisObj = this;

                 xhr.get({url: this.query, handleAs: "json"})
                     .then(function(data)
                           {
                               if(thisObj.model)
                               {
                                   thisObj.updateModel(data);
                               }
                               else
                               {
                                   thisObj.buildModel(data);
                               }
                           });

             }

             structure = new BrokerTreeModel();
             structure.update();

             require(["dijit/Tree"], function(Tree) {
                 tree = new Tree({ model: structure }, "structure");
                 tree.on("dblclick",
                         function(object)
                         {
                             if(!object._dummyChild)
                             {
                                structure.relocate(object);
                             }

                         }, true);
                 tree.startup();
             });

             updateList.push(structure);
         });