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
        "dojo/query",
        'qpid/common/util',
        "qpid/common/ContextVariablesEditor",
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
    function (xhr, dom, construct, win, registry, parser, array, event, json, query, util) {

        var addQueue = {};

        var node = construct.create("div", null, win.body(), "last");

        var typeSpecificFields = { priorities: "priority", lvqKey: "lvq", sortKey: "sorted" };
        var requiredFields = { sorted: "sortKey"};

        var fieldConverters = {
                queueFlowControlSizeBytes:        parseInt,
                queueFlowResumeSizeBytes:         parseInt,
                alertThresholdMessageSize:        parseInt,
                alertThresholdQueueDepthMessages: parseInt,
                alertThresholdQueueDepthBytes:    parseInt,
                maximumDeliveryAttempts:          parseInt,
                alertThresholdMessageAge:         parseInt,
                alertRepeatGap:                   parseInt
            }

        var convertToQueue = function convertToQueue(formValues)
            {
                var newQueue = {};
                newQueue.name = formValues.name;
                for(var propName in formValues)
                {
                    if(formValues.hasOwnProperty(propName))
                    {
                        if(propName === "durable")
                        {
                            if (formValues.durable[0] && formValues.durable[0] == "durable") {
                                newQueue.durable = true;
                            }
                        }
                        else if(propName === "dlqEnabled")
                        {
                            if (formValues.dlqEnabled[0] && formValues.dlqEnabled[0] == "dlqEnabled") {
                                newQueue["x-qpid-dlq-enabled"] = true;
                            }
                        }
                        else if(propName === "messageGroupSharedGroups")
                        {
                            if (formValues.messageGroupSharedGroups[0] && formValues.messageGroupSharedGroups[0] == "messageGroupSharedGroups") {
                                newQueue["messageGroupSharedGroups"] = true;
                            }
                        }
                        else if (!typeSpecificFields.hasOwnProperty(propName) || formValues[ "type" ] === typeSpecificFields[ propName ])
                        {
                            if(formValues[ propName ] !== "") {
                                if (fieldConverters.hasOwnProperty(propName))
                                {
                                    newQueue[ propName ] = fieldConverters[propName](formValues[propName]);
                                }
                                else
                                {
                                    newQueue[ propName ] = formValues[propName];
                                }
                            }
                        }

                    }
                }

                return newQueue;
            };


        xhr.get({url: "addQueue.html",
                 sync: true,
                 load:  function(data) {
                            var theForm;
                            node.innerHTML = data;
                            addQueue.dialogNode = dom.byId("addQueue");
                            parser.instantiate([addQueue.dialogNode]);

                            // for children which have name type, add a function to make all the associated atrributes
                            // visible / invisible as the select is changed
                            theForm = registry.byId("formAddQueue");
                            var typeSelector = registry.byId("formAddQueue.type");
                            typeSelector.on("change", function(value)
                            {
                                query(".typeSpecificDiv").forEach(function(node, index, arr)
                                {
                                    if (node.id === "formAddQueueType:" + value)
                                    {
                                        node.style.display = "block";
                                        util.applyMetadataToWidgets(node, "Queue", value);
                                    }
                                    else
                                    {
                                        node.style.display = "none";
                                    }
                                });
                                for(var requiredField in requiredFields)
                                {
                                    dijit.byId('formAddQueue.' + requiredFields[requiredField]).required = (requiredField == value);
                                }
                            });

                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newQueue = convertToQueue(theForm.getValues());
                                    util.post("api/latest/queue/" + encodeURIComponent(addQueue.vhostnode)
                                            + "/"+encodeURIComponent(addQueue.vhost) + "/" + encodeURIComponent(newQueue.name),
                                            newQueue, function(x){registry.byId("addQueue").hide();});
                                    return false;


                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }

                            });
                        }});

        addQueue.show = function(data) {
                            addQueue.vhost = data.virtualhost;
                            addQueue.vhostnode = data.virtualhostnode;
                            var form = registry.byId("formAddQueue");
                            form.reset();
                            registry.byId("addQueue").show();
                            util.applyMetadataToWidgets(form.domNode, "Queue", "standard");

                            if (!this.context)
                            {
                             this.context = new qpid.common.ContextVariablesEditor({name: 'context', title: 'Context variables'});
                             this.context.placeAt(dom.byId("formAddQueue.context"));
                            }

                            var escapedUrl = "api/latest/virtualhost/" + encodeURIComponent(addQueue.vhostnode) + "/" + encodeURIComponent(addQueue.vhost);
                            this.context.loadInheritedData(escapedUrl);
        };

        return addQueue;
    });
