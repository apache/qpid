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
        "dijit/registry",
        "dijit/Dialog",
        "dijit/form/Button",
        "dijit/form/FilteringSelect",
        "qpid/common/properties",
        "dojo/text!addVirtualHostNode.html",
        "dijit/form/Form",
        "dijit/form/CheckBox",
        "dijit/form/RadioButton",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (xhr, event, lang, array, dom, domConstruct, json, parser, Memory, registry, Dialog, Button, FilteringSelect, properties, template)
  {

    var addVirtualHostNode =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode);
        this.typeFieldsContainer = dom.byId("addVirtualHostNode.typeFields");
        this.dialog = registry.byId("addVirtualHostNode");
        this.saveButton = registry.byId("addVirtualHostNode.saveButton");
        this.cancelButton = registry.byId("addVirtualHostNode.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.saveButton.on("click", function(e){that._save(e);});
        this.form = registry.byId("addVirtualHostNode.form");
        this.type = registry.byId("addVirtualHostNode.type");
        this.type.set("disabled", true);
        xhr.get({sync: properties.useSyncGet, handleAs: "json", url: "api/latest/broker?depth=0", load: function(data){that._onBrokerData(data[0]) }});
        this.form.on("submit", function(e){that._submit();} );
      },
      _onBrokerData: function(brokerData)
      {
          var that=this;
          this.supportedVirtualHostNodeTypes = brokerData.supportedVirtualHostNodeTypes;
          this.supportedVirtualHostStoreTypes = brokerData.supportedVirtualHostStoreTypes;
          var typesData = [];
          for (var i =0 ; i < this.supportedVirtualHostNodeTypes.length; i++)
          {
              var type = this.supportedVirtualHostNodeTypes[i];
              typesData.push( {id: type, name: type} );
          }
          var typesStore = new Memory({ data: typesData });
          this.type.set("store", typesStore);
          this.type.set("disabled", false);
          this.type.on("change", function(type){that._typeChanged(type);});
      },
      show: function()
      {
        this.form.reset();
        this.type.set("value", null);
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
      _typeChanged: function(type)
      {
          var widgets = registry.findWidgets(this.typeFieldsContainer);
          array.forEach(widgets, function(item) { item.destroyRecursive();});
          domConstruct.empty(this.typeFieldsContainer);

          if (type)
          {
            var that = this;
            require(["qpid/management/virtualhostnode/" + type.toLowerCase() + "/add"],
              function(TypeUI)
              {
                  try
                  {
                      TypeUI.show({containerNode:that.typeFieldsContainer, parent: that});
                  }
                  catch(e)
                  {
                      console.warn(e);
                  }
              }
            );
          }
      },
      _cancel: function(e)
      {
          this.dialog.hide();
      },
      _save: function(e)
      {
        event.stop(e);
        this._submit();
      },
      _submit: function()
      {
        if(this.form.validate())
        {
          var success = false,failureReason=null;
          var data = this._getValues();
          xhr.put({
              url: "api/latest/virtualhostnode/" + encodeURIComponent(data.name),
              sync: true,
              handleAs: "json",
              headers: { "Content-Type": "application/json"},
              putData: json.stringify(data),
              load: function(x) {success = true; },
              error: function(error) {success = false; failureReason = error;}
          });

          if(success === true)
          {
              this.dialog.hide();
          }
          else
          {
              alert("Error:" + failureReason);
          }
        }
        else
        {
            alert('Form contains invalid data.  Please correct first');
        }
      },
      _getValues: function()
      {
        var values = {};
        var formWidgets = this.form.getChildren();
        for(var i in formWidgets)
        {
            var widget = formWidgets[i];
            var value = widget.value;
            var propName = widget.name;
            if (propName)
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
        return values;
      }
    };

    addVirtualHostNode.init();

    return addVirtualHostNode;
  }
);
