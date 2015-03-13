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
        "qpid/common/util",
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
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, event, json, util) {

        var addExchange = {};

        var node = construct.create("div", null, win.body(), "last");

        var convertToExchange = function convertToExchange(formValues)
            {
                var newExchange = {};
                newExchange.name = formValues.name;
                for(var propName in formValues)
                {
                    if(formValues.hasOwnProperty(propName))
                    {
                        if(propName === "durable")
                        {
                            if (formValues.durable[0] && formValues.durable[0] == "durable") {
                                newExchange.durable = true;
                            }
                        } else {
                            if(formValues[ propName ] !== "") {
                                newExchange[ propName ] = formValues[propName];
                            }
                        }

                    }
                }

                return newExchange;
            };


        xhr.get({url: "addExchange.html",
                 sync: true,
                 load:  function(data) {
                            var theForm;
                            node.innerHTML = data;
                            addExchange.dialogNode = dom.byId("addExchange");
                            parser.instantiate([addExchange.dialogNode]);

                            theForm = registry.byId("formAddExchange");
                            array.forEach(theForm.getDescendants(), function(widget)
                                {
                                    if(widget.name === "type") {
                                        widget.on("change", function(isChecked) {

                                            var obj = registry.byId(widget.id + ":fields");
                                            if(obj) {
                                                if(isChecked) {
                                                    obj.domNode.style.display = "block";
                                                    obj.resize();
                                                } else {
                                                    obj.domNode.style.display = "none";
                                                    obj.resize();
                                                }
                                            }
                                        })
                                    }

                                });

                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newExchange = convertToExchange(theForm.getValues());
                                    var that = this;
                                    util.post("api/latest/exchange/" + encodeURIComponent(addExchange.vhostnode)
                                            + "/" + encodeURIComponent(addExchange.vhost),
                                            newExchange, function(x){ registry.byId("addExchange").hide(); });
                                    return false;


                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }

                            });
                        }});

        addExchange.show = function(data) {
                            addExchange.vhost = data.virtualhost;
                            addExchange.vhostnode = data.virtualhostnode;
                            registry.byId("formAddExchange").reset();
                            registry.byId("addExchange").show();
                        };

        return addExchange;
    });