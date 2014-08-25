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
        "dojo/_base/event",
        "dojo/_base/lang",
        "dojo/_base/array",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/json",
        "dojo/parser",
        "dojo/store/Memory",
        "dojox/lang/functional/object",
        "dijit/registry",
        "dijit/Dialog",
        "dijit/form/Button",
        "dijit/form/FilteringSelect",
        "qpid/common/properties",
        "qpid/common/util",
        "qpid/common/metadata",
        "dojo/text!addVirtualHostNodeAndVirtualHost.html",
        "dijit/form/Form",
        "dijit/form/CheckBox",
        "dijit/form/RadioButton",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (xhr, event, lang, array, dom, domConstruct, json, parser, Memory, fobject, registry, Dialog, Button, FilteringSelect, properties, util, metadata, template)
  {

    var addVirtualHostNodeAndVirtualHost =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode);

        var virtualHostNodeName = registry.byId("addVirtualHostNode.nodeName");
        virtualHostNodeName.set("regExpGen", util.nameOrContextVarRegexp);

        this.dialog = registry.byId("addVirtualHostNodeAndVirtualHost");
        this.addButton = registry.byId("addVirtualHostNodeAndVirtualHost.addButton");
        this.cancelButton = registry.byId("addVirtualHostNodeAndVirtualHost.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.addButton.on("click", function(e){that._add(e);});

        this.virtualHostNodeTypeFieldsContainer = dom.byId("addVirtualHostNode.typeFields");
        this.virtualHostNodeForm = registry.byId("addVirtualHostNode.form");
        this.virtualHostNodeType = registry.byId("addVirtualHostNode.type");
        this.virtualHostNodeType.set("disabled", true);

        this.virtualHostTypeFieldsContainer = dom.byId("addVirtualHost.typeFields");
        this.virtualHostForm = registry.byId("addVirtualHost.form");
        this.virtualHostType = registry.byId("addVirtualHost.type");
        this.virtualHostType.set("disabled", true);

        this.supportedVirtualHostNodeTypes = metadata.getTypesForCategory("VirtualHostNode");
        this.supportedVirtualHostNodeTypes.sort();
        this.supportedVirtualHostTypes = metadata.getTypesForCategory("VirtualHost");
        this.supportedVirtualHostTypes.sort();

        //VH Type BDB_HA_REPLICA is not user creatable. This is only needed until we have model meta data available.
        this.supportedVirtualHostTypes = array.filter(this.supportedVirtualHostTypes, function(item){
            return item != "BDB_HA_REPLICA" && item != "BDB_HA";
        });

        var virtualHostNodeTypeStore = util.makeTypeStore(this.supportedVirtualHostNodeTypes);
        this.virtualHostNodeType.set("store", virtualHostNodeTypeStore);
        this.virtualHostNodeType.set("disabled", false);
        this.virtualHostNodeType.on("change", function(type){that._vhnTypeChanged(type, that.virtualHostNodeTypeFieldsContainer, "qpid/management/virtualhostnode/");});

        this.virtualHostTypeStore = util.makeTypeStore(this.supportedVirtualHostTypes);
        this.virtualHostType.set("store", this.virtualHostTypeStore);
        this.virtualHostType.set("disabled", false);
        this.virtualHostType.on("change", function(type){that._vhTypeChanged(type, that.virtualHostTypeFieldsContainer, "qpid/management/virtualhost/");});

      },
      show: function()
      {
        this.virtualHostNodeForm.reset();
        this.virtualHostNodeType.set("value", null);

        this.virtualHostForm.reset();
        this.virtualHostType.set("value", null);

        this.dialog.show();
      },
      destroy: function()
      {
        if (this.dialog)
        {
            this.dialog.destroyRecursive();
            this.dialog = null;
        }

        if (this.containerNode)
        {
            domConstruct.destroy(this.containerNode);
            this.containerNode = null;
        }
      },
      _vhnTypeChanged: function (type, typeFieldsContainer, urlStem)
      {
        this._processDropDownsForBdbHa(type);
        this._processDropDownsForJson(type);

        this._typeChanged(type, typeFieldsContainer, urlStem, "VirtualHostNode");
      },
      _vhTypeChanged: function (type, typeFieldsContainer, urlStem)
      {
        this._typeChanged(type, typeFieldsContainer, urlStem, "VirtualHost");
      },
      _typeChanged: function (type, typeFieldsContainer, urlStem, category)
      {
          var widgets = registry.findWidgets(typeFieldsContainer);
          array.forEach(widgets, function(item) { item.destroyRecursive();});
          domConstruct.empty(typeFieldsContainer);

          if (type)
          {
            var that = this;
            require([urlStem + type.toLowerCase() + "/add"],
              function(typeUI)
              {
                  try
                  {
                      typeUI.show({containerNode:typeFieldsContainer, parent: that});

                      util.applyMetadataToWidgets(typeFieldsContainer,category, type);
                  }
                  catch(e)
                  {
                      console.warn(e);
                  }
              }
            );
          }
      },
      _processDropDownsForBdbHa: function (type)
      {
        if (type == "BDB_HA")
        {
          this.virtualHostType.set("disabled", true);
          if (!this.virtualHostTypeStore.get("BDB_HA"))
          {
            this.virtualHostTypeStore.add({id: "BDB_HA", name: "BDB_HA"});
          }
          this.virtualHostType.set("value", "BDB_HA");
        }
        else
        {
          if (this.virtualHostTypeStore.get("BDB_HA"))
          {
            this.virtualHostTypeStore.remove("BDB_HA");
          }
          this.virtualHostType.set("value", "");

          this.virtualHostType.set("disabled", false);
        }
      },
      _processDropDownsForJson: function (type)
      {
        if (type == "JSON")
        {
          if (this.virtualHostType.value == "ProvidedStore")
          {
            this.virtualHostType.set("value", "");
          }

          if (this.virtualHostTypeStore.get("ProvidedStore"))
          {
            this.virtualHostTypeStore.remove("ProvidedStore");
          }
        }
        else
        {
          if (!this.virtualHostTypeStore.get("ProvidedStore"))
          {
            this.virtualHostTypeStore.add({id: "ProvidedStore", name: "ProvidedStore"});
          }
        }
      },
      _cancel: function(e)
      {
          this.dialog.hide();
      },
      _add: function(e)
      {
        event.stop(e);
        this._submit();
      },
      _submit: function()
      {
        if(this.virtualHostNodeForm.validate() && this.virtualHostForm.validate())
        {
          var success = false,failureReason=null;

          var virtualHostNodeData = this._getValues(this.virtualHostNodeForm);
          var virtualHostData = this._getValues(this.virtualHostForm);

          //Default the VH name to be the same as the VHN name.
          virtualHostData["name"] = virtualHostNodeData["name"];

          var encodedVirtualHostNodeName = encodeURIComponent(virtualHostNodeData.name);
          xhr.put({
              url: "api/latest/virtualhostnode/" + encodedVirtualHostNodeName,
              sync: true,
              handleAs: "json",
              headers: { "Content-Type": "application/json"},
              putData: json.stringify(virtualHostNodeData),
              load: function(x) {success = true; },
              error: function(error) {success = false; failureReason = error;}
          });

          if(success === true && virtualHostNodeData["type"] != "BDB_HA")
          {
              var encodedVirtualHostName = encodeURIComponent(virtualHostData.name);
              xhr.put({
                  url: "api/latest/virtualhost/" + encodedVirtualHostNodeName + "/" + encodedVirtualHostName,
                  sync: true,
                  handleAs: "json",
                  headers: { "Content-Type": "application/json"},
                  putData: json.stringify(virtualHostData),
                  load: function (x) {
                      success = true;
                  },
                  error: function (error) {
                      success = false;
                      failureReason = error;
                  }
              });
          }

          if (success == true)
          {
              this.dialog.hide();
          }
          else
          {
              // What if VHN creation was successful but VH was not
              alert("Error:" + failureReason);
          }
        }
        else
        {
            alert('Form contains invalid data. Please correct first');
        }
      },
      _getValues: function (form)
      {
        var values = {};
        var contextMap = {};

        var formWidgets = form.getChildren();
        for(var i in formWidgets)
        {
            var widget = formWidgets[i];
            var value = widget.value;
            var propName = widget.name;
            if (propName && (widget.required || value ))
            {
                if (widget.contextvar)
                {
                    contextMap [propName] = String(value); // Broker requires that context values are Strings
                }
                else
                {
                    if (widget instanceof dijit.form.CheckBox)
                    {
                        values[ propName ] = widget.checked;
                    }
                    else if (widget instanceof dijit.form.RadioButton && value)
                    {
                        var currentValue = values[propName];
                        if (currentValue)
                        {
                            if (lang.isArray(currentValue))
                            {
                                currentValue.push(value)
                            }
                            else
                            {
                                values[ propName ] = [currentValue, value];
                            }
                        }
                        else
                        {
                            values[ propName ] = value;
                        }
                    }
                    else
                    {
                        values[ propName ] = value ? value: null;
                    }

                }
            }
        }

        // One or more context variables were defined on form
        if (fobject.keys(contextMap).length > 0)
        {
            values ["context"] = contextMap;
        }
        return values;
      }
    };

    addVirtualHostNodeAndVirtualHost.init();

    return addVirtualHostNodeAndVirtualHost;
  }
);
