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
define(["dojo/_base/xhr",
        "dojo/query",
        "dojo/io-query",
        "dijit/Tree",
        "qpid/common/util",
        "qpid/common/updater",
        "qpid/management/controller",
        "qpid/management/UserPreferences",
        "dojo/ready",
        "dojo/domReady!"],
       function (xhr, query, ioQuery, Tree, util, updater, controller, UserPreferences, ready) {

           function TreeViewModel(queryString) {
               this.query = queryString;

               this.onChildrenChange = function (parent, children) {
                   // fired when the set of children for an object change
               };

               this.onChange = function (object) {
                   // fired when the properties of an object change
               };

               this.onDelete = function (object) {
                   // fired when an object is deleted
               };
           }


           TreeViewModel.prototype.buildModel = function (data) {
               this.model = data;

           };

           TreeViewModel.prototype.updateModel = function (data) {
               var that = this;

               function checkForChanges(oldData, data) {
                   var propName;
                   if (oldData.name != data.name) {
                       that.onChange(data);
                   }

                   var childChanges = false;
                   // Iterate over old childTypes, check all are in new
                   for (propName in oldData) {
                       if (oldData.hasOwnProperty(propName)) {
                           var oldChildren = oldData[ propName ];
                           if (util.isArray(oldChildren)) {

                               var newChildren = data[ propName ];

                               if (!(newChildren && util.isArray(newChildren))) {
                                   childChanges = true;
                               } else {
                                   var subChanges = false;
                                   // iterate over elements in array, make sure in both, in which case recurse
                                   for (var i = 0; i < oldChildren.length; i++) {
                                       var matched = false;
                                       for (var j = 0; j < newChildren.length; j++) {
                                           if (oldChildren[i].id == newChildren[j].id) {
                                               checkForChanges(oldChildren[i], newChildren[j]);
                                               matched = true;
                                               break;
                                           }
                                       }
                                       if (!matched) {
                                           subChanges = true;
                                       }
                                   }
                                   if (subChanges == true || oldChildren.length != newChildren.length) {
                                       that.onChildrenChange({ id:data.id + propName, _dummyChild:propName, data:data },
                                                             newChildren);
                                   }
                               }
                           }
                       }
                   }

                   for (propName in data) {
                       if (data.hasOwnProperty(propName)) {
                           var prop = data[ propName ];
                           if (util.isArray(prop)) {
                               if (!(oldData[ propName ] && util.isArray(oldData[propName]))) {
                                   childChanges = true;
                               }
                           }
                       }
                   }

                   if (childChanges) {
                       var children = [];
                       that.getChildren(data, function (theChildren) {
                           children = theChildren
                       });
                       that.onChildrenChange(data, children);
                   }
               }

               var oldData = this.model;
               this.model = data;

               checkForChanges(oldData, data);
           };


           TreeViewModel.prototype.fetchItemByIdentity = function (id) {

               function fetchItem(id, data) {
                   var propName;

                   if (data.id == id) {
                       return data;
                   } else if (id.indexOf(data.id) == 0) {
                       return { id:id, _dummyChild:id.substring(id.length), data:data };
                   } else {
                       for (propName in data) {
                           if (data.hasOwnProperty(propName)) {
                               var prop = data[ propName ];
                               if (util.isArray(prop)) {
                                   for (var i = 0; i < prop.length; i++) {
                                       var theItem = fetchItem(id, prop[i]);
                                       if (theItem) {
                                           return theItem;
                                       }
                                   }
                               }
                           }
                       }
                       return null;
                   }
               }

               return fetchItem(id, this.model);
           };

           TreeViewModel.prototype.getChildren = function (parentItem, onComplete) {

               if (parentItem) {
                   if (parentItem._dummyChild) {
                       onComplete(parentItem.data[ parentItem._dummyChild ]);
                   } else {
                       var children = [];
                       for (var propName in parentItem) {
                           if (parentItem.hasOwnProperty(propName)) {
                               var prop = parentItem[ propName ];

                               if (util.isArray(prop)) {
                                   children.push({ id:parentItem.id
                                       + propName, _dummyChild:propName, data:parentItem });
                               }
                           }
                       }
                       onComplete(children);
                   }
               } else {
                   onComplete([]);
               }
           };

           TreeViewModel.prototype.getIdentity = function (theItem) {
               if (theItem) {
                   return theItem.id;
               }

           };

           TreeViewModel.prototype.getLabel = function (theItem) {
               if (theItem) {
                   if (theItem._dummyChild) {
                       return theItem._dummyChild;
                   } else {
                       return theItem.name;
                   }
               } else {
                   return "";
               }
           };

           TreeViewModel.prototype.getRoot = function (onItem) {
               onItem(this.model);
           };

           TreeViewModel.prototype.mayHaveChildren = function (theItem) {
               if (theItem) {
                   if (theItem._dummyChild) {
                       return true;
                   } else {
                       for (var propName in theItem) {
                           if (theItem.hasOwnProperty(propName)) {
                               var prop = theItem[ propName ];
                               if (util.isArray(prop)) {
                                   return true;
                               }
                           }
                       }
                       return false;
                   }
               } else {
                   return false;
               }
           };

           TreeViewModel.prototype.relocate = function (theItem) {

               function findItemDetails(theItem, details, type, object, parent) {
                   if (theItem.id == object.id) {
                       details.type = type;
                       details[ type ] = object.name;
                       details.parent = parent;
                   } else {
                       var parentObject ={
                               type: type,
                               name: object.name
                       };
                       if (parent)
                       {
                           parentObject.parent = parent;
                       }
                       // iterate over children
                       for (var propName in object) {
                           if (object.hasOwnProperty(propName)) {
                               var prop = object[ propName ];
                               if (util.isArray(prop)) {
                                   for (var i = 0; i < prop.length; i++) {
                                       findItemDetails(theItem, details, propName.substring(0, propName.length - 1),
                                                       prop[i], parentObject);

                                       if (details.type) {
                                           break;
                                       }
                                   }
                               }
                               if (details.type) {
                                   break;
                               }
                           }
                       }

                       if (!details.type) {
                           details[ type ] = null;
                       }
                   }
               }

               var details = new Object();
               findItemDetails(theItem, details, "broker", this.model, null);

               if (details.type == "broker") {
                   controller.show("broker", "", null, theItem.id);
               } else if (details.type == "virtualhost") {
                   controller.show("virtualhost", details.virtualhost, details.parent, theItem.id);
               } else if (details.type == "exchange") {
                   controller.show("exchange", details.exchange, details.parent, theItem.id);
               } else if (details.type == "queue") {
                   controller.show("queue", details.queue, details.parent, theItem.id);
               } else if (details.type == "connection") {
                   controller.show("connection", details.connection, details.parent, theItem.id);
               } else if (details.type == 'port') {
                   controller.show("port", details.port, details.parent, theItem.id);
               } else if (details.type == 'authenticationprovider') {
                   controller.show("authenticationprovider", details.authenticationprovider, details.parent, theItem.id);
               } else if (details.type == 'groupprovider') {
                   controller.show("groupprovider", details.groupprovider, details.parent, theItem.id);
               } else if (details.type == 'group') {
                   controller.show("group", details.group, details.parent, theItem.id);
               } else if (details.type == 'keystore') {
                 controller.show("keystore", details.keystore, details.parent, theItem.id);
               } else if (details.type == 'truststore') {
                 controller.show("truststore", details.truststore, details.parent, theItem.id);
               } else if (details.type == 'accesscontrolprovider') {
                 controller.show("accesscontrolprovider", details.accesscontrolprovider, details.parent, theItem.id);
               } else if (details.type == 'plugin') {
                 controller.show("plugin", details.plugin, {type:"broker", name:""}, theItem.id);
               } else if (details.type == "preferencesprovider") {
                 controller.show("preferencesprovider", details.preferencesprovider, details.parent, theItem.id);
               } else if (details.type == "virtualhostnode") {
                 controller.show("virtualhostnode", details.virtualhostnode, details.parent, theItem.id);
               }
           };

           TreeViewModel.prototype.update = function () {
               var thisObj = this;

               xhr.get({url:this.query, sync: true, handleAs:"json"})
                   .then(function (data) {
                             if (thisObj.model) {
                                 thisObj.updateModel(data);
                             }
                             else {
                                 thisObj.buildModel(data);
                             }
                         }, util.xhrErrorHandler);

           };

           query('div[qpid-type="treeView"]').forEach(function(node, index, arr) {
               var treeModel = new TreeViewModel("service/structure");
               treeModel.update();
               var tree = new Tree({ model: treeModel }, node);
               tree.on("dblclick",
                       function (object) {
                           if (object && !object._dummyChild) {
                               treeModel.relocate(object);
                           }

                       }, true);
               tree.startup();
               updater.add( treeModel );

               ready(function() {
                 controller.show("broker","");

                 var tabs = UserPreferences.tabs;
                 if (tabs)
                 {
                   for(var i in tabs)
                   {
                     var tab = tabs[i], modelObject;
                     if (tab.objectType != "broker")
                     {
                       if (tab.objectId)
                       {
                         modelObject = treeModel.fetchItemByIdentity(tab.objectId);
                         if (modelObject)
                         {
                           treeModel.relocate(modelObject);
                         }
                         else
                         {
                           UserPreferences.removeTab(tab);
                         }
                       }
                       else
                       {
                         controller.show(tab.objectType, "");
                       }
                     }
                   }
                 }
             });
           });

           return TreeViewModel;
       });