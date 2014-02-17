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
        "dojo/_base/array",
        "dojo/_base/event",
        "dojo/_base/lang",
        "dojo/_base/window",
        "dojo/dom",
        "dojo/dom-construct",
        "dijit/registry",
        "dojo/parser",
        'dojo/json',
        "dojo/query",
        "dojo/text!virtualhost/bdb_ha/edit.html",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/FilteringSelect",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
    function (xhr, array, event, lang, win, dom, domConstruct, registry, parser, json, query, template)
    {
        var widgets = [{
                          name: "storePath",
                          defaultValue: "",
                          editable: false
                       },
                       {
                           name: "name",
                           defaultValue: "",
                           editable: false
                       },
                       {
                           name: "groupName",
                           defaultValue: "",
                           editable: false
                       },
                       {
                           name: "hostPort",
                           defaultValue: "",
                           editable: false
                       },
                       {
                           name: "helperHostPort",
                           defaultValue: "",
                           editable: false
                       },
                       {
                           name: "durability",
                           defaultValue: "",
                           editable: false
                       },
                       {
                           name: "coalescingSync",
                           defaultValue: true,
                           defaultChecked: true,
                           editable: false
                       },
                       {
                           name: "designatedPrimary",
                           defaultValue: true,
                           defaultChecked: true,
                           editable: true
                       },
                       {
                           name: "priority",
                           defaultValue: 1,
                           editable: true
                       },
                       {
                           name: "quorumOverride",
                           defaultValue: 0,
                           editable: true
                       }];

        var bdbHaNodeEditor =
        {
            _init: function()
            {
                this.containerNode = domConstruct.create("div", {innerHTML: template});
                parser.parse(this.containerNode);
                this.dialog = registry.byId("editNodeDialog")
                this.saveButton = registry.byId("editNode.saveButton");
                this.cancelButton = registry.byId("editNode.cancelButton");
                this.cancelButton.on("click", function(e){bdbHaNodeEditor._cancel(e);});
                this.saveButton.on("click", function(e){bdbHaNodeEditor._save(e);});
                for(var i = 0; i < widgets.length; i++)
                {
                    var widgetItem = widgets[i];
                    this[widgetItem.name] = registry.byNode(query(".editNode-" + widgetItem.name, this.dialog.containerNode)[0]);
                }
                this.form = registry.byId("editNodeForm");
            },
            show: function(virtualHost, nodeName)
            {
                this.query = "rest/replicationnode/" + encodeURIComponent(virtualHost) + "/" + encodeURIComponent(nodeName);
                if (virtualHost && nodeName)
                {
                    xhr.get({url: this.query,
                      sync: true,
                      handleAs: "json",
                      load:  function(data) {
                          var node = data[0];
                          for(var i = 0; i < widgets.length; i++)
                          {
                              var widgetItem = widgets[i];
                              bdbHaNodeEditor[widgetItem.name].set("value", node[widgetItem.name]);
                              bdbHaNodeEditor[widgetItem.name].set("disabled", !widgetItem.editable);
                          }
                      }});
                }
                else
                {
                    for(var i = 0; i < nonEditableWidgetNumber; i++)
                    {
                        var widgetItem = widgets[i];
                        bdbHaNodeEditor[widgetItem.name].set("value", widgetItem.defaultValue);
                        bdbHaNodeEditor[widgetItem.name].set("disabled", false);
                        if (widgetItem.hasOwnProperty("defaultChecked"))
                        {
                            bdbHaNodeEditor[widgetName].set("checked", widgetItem.defaultChecked);
                        }
                    }
                }
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
            _cancel: function(e)
            {
                this.dialog.hide();
            },
            _save: function(e)
            {
                event.stop(e);
                if(this.form.validate())
                {
                    var data = {};
                    for(var i = 0; i < widgets.length; i++)
                    {
                        var widgetItem = widgets[i];
                        var widget = this[widgetItem.name];
                        if (!widget.get("disabled"))
                        {
                            data[widgetItem.name] = widgetItem.hasOwnProperty("defaultChecked")? widget.get("checked"):widget.get("value");
                        }
                    }
                    xhr.put({
                        url: this.query,
                        sync: true,
                        handleAs: "json",
                        headers: { "Content-Type": "application/json"},
                        putData: json.stringify(data),
                        load: function(x) {bdbHaNodeEditor.success = true; },
                        error: function(error) {bdbHaNodeEditor.success = false; bdbHaNodeEditor.failureReason = error;}
                    });

                  if(this.success === true)
                  {
                      this.dialog.hide();
                  }
                  else
                  {
                      alert("Error:" + this.failureReason);
                  }
              }
              else
              {
                  alert('Form contains invalid data.  Please correct first');
              }
            }
        };

        bdbHaNodeEditor._init();

        return bdbHaNodeEditor;
    });