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
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/_base/window",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/_base/event",
        'dojo/_base/json',
        "dojo/store/Memory",
        "dijit/form/FilteringSelect",
        "dojo/_base/connect",
        "dojo/dom-style",
        "dijit/form/NumberSpinner", // required by the form
        /* dojox/ validate resources */
        "dojox/validate/us", "dojox/validate/web",
        /* basic dijit classes */
        "dijit/Dialog",
        "dijit/form/CheckBox", "dijit/form/Textarea",
        "dijit/form/FilteringSelect", "dijit/form/TextBox",
        "dijit/form/ValidationTextBox", "dijit/form/DateTextBox",
        "dijit/form/TimeTextBox", "dijit/form/Button",
        "dijit/form/RadioButton", "dijit/form/Form",
        "dijit/form/DateTextBox",
        /* basic dojox classes */
        "dojox/form/BusyButton", "dojox/form/CheckedMultiSelect",
        "dojox/layout/TableContainer",
        "dijit/layout/AccordionContainer",
        "dijit/layout/AccordionPane",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, event, json, Memory, FilteringSelect, connect, domStyle) {

        var addVirtualHost = {};

        var node = construct.create("div", null, win.body(), "last");

        var convertToVirtualHost = function convertToVirtualHost(formValues)
        {
            var newVirtualHost = {};
            var id = dojo.byId("formAddVirtualHost.id").value;
            if (id)
            {
                newVirtualHost.id = id;
            }
            for(var propName in formValues)
            {
                if(formValues.hasOwnProperty(propName))
                {
                    if(formValues[ propName ] !== "") {
                        newVirtualHost[ propName ] = formValues[propName];
                    }

                }
            }

            return newVirtualHost;
        }

        if (!dijit.registry["addVirtualHost"])
        {
            var that = this;
            xhr.get({url: "addVirtualHost.html",
                 sync: true,
                 load:  function(data) {
                            var theForm;
                            node.innerHTML = data;
                            addVirtualHost.dialogNode = dom.byId("addVirtualHost");
                            parser.instantiate([addVirtualHost.dialogNode]);

                            var configPathPane =  new dijit.layout.AccordionPane({
                                title: "Configuration File"
                            }, "addVirtualHost.configPathDiv");

                            var attributesPane =  new dijit.layout.AccordionPane({
                                title: "Store Attributes",
                                selected: true
                            }, "addVirtualHost.attributesDiv");

                            var aContainer = new dijit.layout.AccordionContainer({
                                style: "height: 150px"
                            },
                            "addVirtualHost.variants");

                            aContainer.addChild(attributesPane);
                            aContainer.addChild(configPathPane);
                            aContainer.startup();

                            theForm = registry.byId("formAddVirtualHost");
                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var formValues = theForm.getValues();
                                    if (formValues.configPath == "" && formValues.storeType == "")
                                    {
                                        alert("Please specify either configuration or store type for the virtual host");
                                        return false;
                                    }
                                    if (formValues.configPath != "" && formValues.storeType != "")
                                    {
                                        alert("Either configuration or store type with path have to be specified!");
                                        return false;
                                    }
                                    var newVirtualHost = convertToVirtualHost(formValues);
                                    var that = this;

                                    xhr.put({url: "rest/virtualhost/" + encodeURIComponent(newVirtualHost.name),
                                             sync: true, handleAs: "json",
                                             headers: { "Content-Type": "application/json"},
                                             putData: json.toJson(newVirtualHost),
                                             load: function(x) {that.success = true; },
                                             error: function(error) {that.success = false; that.failureReason = error;}});

                                    if(this.success === true)
                                    {
                                        registry.byId("addVirtualHost").hide();
                                    }
                                    else
                                    {
                                        alert("Error:" + this.failureReason);
                                    }
                                    return false;
                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }
                            });
                        }});
        }

        addVirtualHost.show = function(virtualHostName) {
            var that = this;
            registry.byId("formAddVirtualHost").reset();
            dojo.byId("formAddVirtualHost.id").value="";
            if (!that.hasOwnProperty("storeTypeChooser"))
            {
                xhr.get({
                    sync: true,
                    url: "rest/helper?action=ListMessageStoreTypes",
                    handleAs: "json"
                }).then(
                   function(data) {
                       var storeTypes =  data;
                       var storeTypesData = [];
                       for (var i =0 ; i < storeTypes.length; i++)
                       {
                           storeTypesData[i]= {id: storeTypes[i], name: storeTypes[i]};
                       }
                       var storeTypesStore = new Memory({ data: storeTypesData });
                       var storeTypesDiv = dom.byId("addVirtualHost.selectStoreType");
                       var input = construct.create("input", {id: "addStoreType", required: false}, storeTypesDiv);
                       that.storeTypeChooser = new FilteringSelect({ id: "addVirtualHost.storeType",
                                                                 name: "storeType",
                                                                 store: storeTypesStore,
                                                                 searchAttr: "name", required: false}, input);
               });
            }
            if (virtualHostName)
            {
                xhr.get({
                    url: "rest/virtualhost/" + encodeURIComponent(virtualHostName),
                    handleAs: "json"
                }).then(
                   function(data) {
                       var host = data[0];
                       var nameField = dijit.byId("formAddVirtualHost.name");
                       nameField.set("value", host.name);
                       dojo.byId("formAddVirtualHost.id").value=host.id;
                       var configPath = host.configPath;
                       if (configPath)
                       {
                           var configPathField = dijit.byId("formAddVirtualHost.configPath");
                           configPathField.set("value", host.configPath);
                       }
                       else
                       {
                           that.storeTypeChooser.set("value", host.storeType.toLowerCase());
                           var storePathField = dijit.byId("formAddVirtualHost.storePath");
                           storePathField.set("value", host.storePath);
                       }
                       registry.byId("addVirtualHost").show();
               });
            }
            else
            {
                registry.byId("addVirtualHost").show();
            }
        }
        return addVirtualHost;
    });