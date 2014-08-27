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
define(["dojo/_base/xhr",
        "dojo/_base/window",
        "dojo/parser",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/json",
        "dijit/registry",
        "dojo/text!virtualhostnode/bdb_ha/add/newgroup/add.html",
        "qpid/common/util",
        "dijit/form/ValidationTextBox",
        "dijit/form/MultiSelect",
        "dijit/form/Button",
        "dojo/domReady!"],
  function (xhr, win, parser, dom, domConstruct, json, registry, template, util)
  {
    return {
        show: function(data)
        {
          var that=this;

          this.containerNode = domConstruct.create("div", {innerHTML: template}, data.containerNode);
          parser.parse(this.containerNode);

          this.addVirtualHostNodeAddress = registry.byId("addVirtualHostNode.address");
          this.addVirtualHostNodeAddress.set("regExpGen", util.nodeAddressOrContextVarRegexp);

          this.addVirtualHostNodeAddress.on("change", function(address){that._changeAddress(address, that.virtualHostNodeHelperAddress);});
          this.addVirtualHostNodeAddress.on("click", function(e){that._updatePermittedNodesJson();});

          this.virtualHostNodeHelperAddress = registry.byId("addVirtualHostNode.helperAddress");
          this.virtualHostNodeHelperAddress.set("regExpGen", util.nodeAddressOrContextVarRegexp);

          // list objects html node and dojo object
          this.addVirtualHostNodePermittedNodesList = dom.byId("addVirtualHostNode.permittedNodesList");
          this.addVirtualHostNodePermittedNodesListDojo = registry.byId("addVirtualHostNode.permittedNodesList");
          this.addVirtualHostNodePermittedNodesListDojo.on("change", function(value){that._changePermittedNodeList(value);});

          // permitted node text field
          this.addVirtualHostNodePermittedNode = registry.byId("addVirtualHostNode.permittedNode");
          this.addVirtualHostNodePermittedNode.set("regExpGen", util.nodeAddressOrContextVarRegexp);
          this.addVirtualHostNodePermittedNode.on("change", function(value){that._changePermittedNode(value);});

          // add and remove buttons & click handlers
          this.addVirtualHostNodePermittedNodeAddButton = registry.byId("addVirtualHostNode.permittedNodeAdd");
          this.addVirtualHostNodePermittedNodeAddButton.set("disabled", true);
          this.addVirtualHostNodePermittedNodeRemoveButton = registry.byId("addVirtualHostNode.permittedNodeRemove");
          this.addVirtualHostNodePermittedNodeRemoveButton.set("disabled", true);
          this.addVirtualHostNodePermittedNodeAddButton.on("click", function(e){that._clickAddPermittedNodeButton(e);});
          this.addVirtualHostNodePermittedNodeRemoveButton.on("click", function(e){that._clickRemovePermittedNodeButton(e);});

          // This will contain the serialised form that will go to the server
          this.addVirtualHostNodePermittedNodes = registry.byId("addVirtualHostNode.permittedNodes");

          registry.byId("addVirtualHostNode.groupName").set("regExpGen", util.nameOrContextVarRegexp);
        },
        _updatePermittedNodesJson: function ()
        {
          var nodeAddress = this.addVirtualHostNodeAddress.get("value");
          var permittedNodes = [ ];
          if (nodeAddress)
          {
            permittedNodes.push(nodeAddress);
          }
          var children = this.addVirtualHostNodePermittedNodesList.children;
          var i;
          for (i = 0; i < children.length; i++)
          {
            var child = children.item(i);
            if (child.value != nodeAddress)
            {
              permittedNodes.push(child.value);
            }
          }

          this.addVirtualHostNodePermittedNodes.set("value", permittedNodes);
        },
        _changePermittedNodeList: function(value)
        {
          var hasSelection = this.addVirtualHostNodePermittedNodesListDojo.get("value").length > 0;
          this.addVirtualHostNodePermittedNodeRemoveButton.set("disabled", !hasSelection);
        },
        _changePermittedNode: function(value)
        {
          var fieldIsEmpty = (this.addVirtualHostNodePermittedNode.get("value") == "");
          this.addVirtualHostNodePermittedNodeAddButton.set("disabled", fieldIsEmpty);
          return true;
        },
        _changeAddress: function(address, virtualHostNodeHelperAddress)
        {
          virtualHostNodeHelperAddress.set("value", address);
        },
        _clickAddPermittedNodeButton: function(e)
        {
          // check the text box is valid and not empty
          if(this.addVirtualHostNodePermittedNode.isValid() &&
                this.addVirtualHostNodePermittedNode.value &&
                this.addVirtualHostNodePermittedNode.value != "")
          {
            // read value to add from text box
            var newAddress = this.addVirtualHostNodePermittedNode.value;

            // clear UI value
            this.addVirtualHostNodePermittedNode.set("value", "");
            this.addVirtualHostNodePermittedNodeAddButton.set("disabled", true);

            //check entry not already present in list
            var alreadyPresent = false;
            var children = this.addVirtualHostNodePermittedNodesList.children;
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
                // construct new option for list
                var newOption = win.doc.createElement('option');
                newOption.innerHTML = newAddress;
                newOption.value = newAddress;

                // add new option to list
                this.addVirtualHostNodePermittedNodesList.appendChild(newOption);
              }
          }
          this._updatePermittedNodesJson();
        },
        _clickRemovePermittedNodeButton: function(e)
        {
          var selectedValues = this.addVirtualHostNodePermittedNodesListDojo.get("value");
          var v;
          for (v in selectedValues)
          {
            var children = this.addVirtualHostNodePermittedNodesList.children;
            var i;
            for (i = 0; i < children.length; i++)
            {
              var child = children.item(i);
              if (child.value == selectedValues[v])
              {
                this.addVirtualHostNodePermittedNodesList.removeChild(child);
              }
            }
          }
          this.addVirtualHostNodePermittedNodeRemoveButton.set("disabled", true);

          this._updatePermittedNodesJson();

        }
    };
  }
);
