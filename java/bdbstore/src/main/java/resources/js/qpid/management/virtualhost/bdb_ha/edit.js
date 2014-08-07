/*
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
 */
define(["qpid/common/util", "dijit/registry", "dojo/_base/window", "dojo/domReady!"],
   function (util, registry, win)
   {
       var fieldNames = ["storeUnderfullSize", "storeOverfullSize"];
       return {
           show: function(data)
           {
              util.buildEditUI(data.containerNode, "virtualhost/bdb_ha/edit.html", "editVirtualHost.", fieldNames, data.data);

              registry.byId("editVirtualHost.storeUnderfullSize").set("regExpGen", util.numericOrContextVarRegexp);
              registry.byId("editVirtualHost.storeOverfullSize").set("regExpGen", util.numericOrContextVarRegexp);

              var widget = registry.byId("editVirtualHost.localTransactionSynchronizationPolicy-" + data.data["localTransactionSynchronizationPolicy"]);
              if (widget)
              {
                widget.set("checked", true);
              }
              widget = registry.byId("editVirtualHost.remoteTransactionSynchronizationPolicy-" + data.data["remoteTransactionSynchronizationPolicy"]);
              if (widget)
              {
                widget.set("checked", true);
              }

              var that = this;
              this.permittedNodes = registry.byId("editVirtualHost.permittedNodes");
              this.permittedNodesList = registry.byId("editVirtualHost.permittedNodesList");
              this.permittedNodesList.on("change", function(value){that._changePermittedNodeList(value);});

              // permitted node text field
              this.permittedNode = registry.byId("editVirtualHost.permittedNode");
              this.permittedNode.on("change", function(value){that._changePermittedNode(value);});

              // add and remove buttons & click handlers
              this.permittedNodeAddButton = registry.byId("editVirtualHost.permittedNodeAdd");
              this.permittedNodeAddButton.set("disabled", true);
              this.permittedNodeRemoveButton = registry.byId("editVirtualHost.permittedNodeRemove");
              this.permittedNodeRemoveButton.set("disabled", true);
              this.permittedNodeAddButton.on("click", function(e){that._clickAddPermittedNodeButton(e);});
              this.permittedNodeRemoveButton.on("click", function(e){that._clickRemovePermittedNodeButton(e);});

              var permittedNodes = data.data.permittedNodes;
              for(var i=0; i<permittedNodes.length;i++)
              {
                 var host = permittedNodes[i];
                 var newOption = this._addOption(host);
                 // add new option to list
                 this.permittedNodesList.containerNode.appendChild(newOption);
              }
           },
           _clickAddPermittedNodeButton: function(e)
           {
             // check the text box is valid and not empty
             if(this.permittedNode.isValid() &&
                   this.permittedNode.value &&
                   this.permittedNode.value != "")
             {
               // read value to add from text box
               var newAddress = this.permittedNode.value;

               // clear UI value
               this.permittedNode.set("value", "");
               this.permittedNodeAddButton.set("disabled", true);

               //check entry not already present in list
               var alreadyPresent = false;
               var children = this.permittedNodesList.containerNode.children;
               var i;
               for (i = 0; i < children.length; i++)
                 {
                   var child = children.item(i);
                   if (child.value == newAddress)
                   {
                     alreadyPresent = true;
                     break;
                   }
                 }

                 if (!alreadyPresent)
                 {
                   var newOption = this._addOption(newAddress);

                   // add new option to list
                   this.permittedNodesList.containerNode.appendChild(newOption);
                   this._updatePermittedNodes();
                 }
             }
           },
           _clickRemovePermittedNodeButton: function(e)
           {
             var selectedValues = this.permittedNodesList.get("value");
             var v;
             for (v in selectedValues)
             {
               var children = this.permittedNodesList.containerNode.children;
               var i;
               for (i = 0; i < children.length; i++)
               {
                 var child = children.item(i);
                 if (child.value == selectedValues[v])
                 {
                   this.permittedNodesList.containerNode.removeChild(child);
                 }
               }
             }
             this._updatePermittedNodes();
             this.permittedNodeRemoveButton.set("disabled", true);
           },
           _addOption: function(newAddress)
           {
              // construct new option for list
              var newOption = win.doc.createElement('option');
              newOption.innerHTML = newAddress;
              newOption.value = newAddress;
              return newOption;
           },
           _changePermittedNodeList: function(value)
           {
               var hasSelection = this.permittedNodesList.get("value").length > 0;
               this.permittedNodeRemoveButton.set("disabled", !hasSelection);
           },
           _changePermittedNode: function(value)
           {
               var fieldIsEmpty = (this.permittedNode.get("value") == "");
               this.permittedNodeAddButton.set("disabled", fieldIsEmpty);
               return true;
           },
           _updatePermittedNodes: function()
           {
              var values = [];
              var children = this.permittedNodesList.containerNode.children;
              for (var i = 0; i < children.length; i++)
              {
                 var child = children.item(i);
                 values.push(children.item(i).value);
              }
              this.permittedNodes.set("value", values);
           }
       };
   }
);
