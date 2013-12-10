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
        "dojo/_base/json",
        "dojo/string",
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
    function (xhr, dom, construct, win, registry, parser, array, event, json, string, Memory, FilteringSelect, connect, domStyle) {

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
                                title: "Virtual Host Attributes",
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
                                    if (formValues.configPath == "" && formValues["type"] == "")
                                    {
                                        alert("Please specify either configuration file or type for the virtual host");
                                        return false;
                                    }
                                    if (formValues.configPath != "" && formValues["type"] != "")
                                    {
                                        alert("Either configuration file or type have to be specified!");
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

        addVirtualHost.selectVhostType = function(type) {
            if(type && string.trim(type) != "") {
                require(["qpid/management/virtualhost/"+type.toLowerCase()+"/addVirtualHost"],
                function(vhostType)
                {
                    vhostType.show();
                });
            }
        }

        addVirtualHost.show = function() {
            var that = this;
            dom.byId("addVirtualHost.typeSpecificDiv").innerHTML = "";
            registry.byId("formAddVirtualHost").reset();
            dojo.byId("formAddVirtualHost.id").value="";

            if (!that.hasOwnProperty("typeChooser"))
            {
                xhr.get({
                    sync: true,
                    url: "rest/helper?action=ListVirtualHostTypes",
                    handleAs: "json"
                }).then(
                   function(data) {
                       var vhostTypes =  data;
                       var vhostTypesData = [];
                       for (var i =0 ; i < vhostTypes.length; i++)
                       {
                           vhostTypesData[i]= {id: vhostTypes[i], name: vhostTypes[i]};
                       }
                       var typesStore = new Memory({ data: vhostTypesData });
                       var typesDiv = dom.byId("addVirtualHost.selectType");
                       var input = construct.create("input", {id: "addType", required: false}, typesDiv);
                       that.typeChooser = new FilteringSelect({ id: "addVirtualHost.type",
                                                                 name: "type",
                                                                 store: typesStore,
                                                                 searchAttr: "name",
                                                                 required: false,
                                                                 onChange: that.selectVhostType }, input);
               });
            }


            registry.byId("addVirtualHost").show();

        }
        return addVirtualHost;
    });
