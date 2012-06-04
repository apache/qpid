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
        "dijit/form/NumberSpinner", // required by the form
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, event, json) {

        var addQueue = {};

        var node = construct.create("div", null, win.body(), "last");

        var typeSpecificFields = {
                        numPriorities: "priority",
                        lvqKey: "lvq",
                        sortKey: "sorted"
                    };


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
                        } else if (!typeSpecificFields.hasOwnProperty(propName) ||
                                        formValues.type === typeSpecificFields[ propName ]) {
                            if(formValues[ propName ] !== "") {
                                newQueue[ propName ] = formValues[propName];
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

                            // for children which have name type, add a function to make all the associated rows
                            // visible / invisible as the radio button is checked / unchecked

                            theForm = registry.byId("formAddQueue");
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

                                    var newQueue = convertToQueue(theForm.getValues());
                                    var that = this;

                                    xhr.put({url: "/rest/queue/"+addQueue.vhost+"/"+newQueue.name, sync: true, handleAs: "json",
                                             headers: { "Content-Type": "application/json"},
                                             putData: json.toJson(newQueue),
                                             load: function(x) {that.success = true; },
                                             error: function(error) {that.success = false; that.failureReason = error;}});

                                    if(this.success === true)
                                    {
                                        registry.byId("addQueue").hide();
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

        addQueue.show = function(vhost) {
                            addQueue.vhost = vhost;
                            registry.byId("formAddQueue").reset();
                            // hide all non-standard queue rows
                            registry.byId("addQueue").show();
                        };

        return addQueue;
    });