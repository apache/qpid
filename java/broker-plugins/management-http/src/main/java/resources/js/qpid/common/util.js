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
        "dojo/json",
        "dojo/_base/lang",
        "dojo/dom-construct",
        "dojo/dom-geometry",
        "dojo/window",
        "dijit/TitlePane",
        "dijit/Dialog",
        "dijit/form/Form",
        "dijit/form/Button",
        "dijit/form/RadioButton",
        "dijit/form/CheckBox",
        "dojox/layout/TableContainer",
        "dijit/layout/ContentPane",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"
        ],
       function (xhr, event, json, lang, dom, geometry, win) {
           var util = {};
           if (Array.isArray) {
               util.isArray = function (object) {
                   return Array.isArray(object);
               };
           } else {
               util.isArray = function (object) {
                   return object instanceof Array;
               };
           }

           util.flattenStatistics = function (data) {
               var attrName, stats, propName, theList;
               for(attrName in data) {
                   if(data.hasOwnProperty(attrName)) {
                       if(attrName == "statistics") {
                           stats = data.statistics;
                           for(propName in stats) {
                               if(stats.hasOwnProperty( propName )) {
                                   data[ propName ] = stats[ propName ];
                               }
                           }
                       } else if(data[ attrName ] instanceof Array) {
                           theList = data[ attrName ];

                           for(var i=0; i < theList.length; i++) {
                               util.flattenStatistics( theList[i] );
                           }
                       }
                   }
               }
           };

           util.isReservedExchangeName = function(exchangeName)
           {
               return exchangeName == null || exchangeName == "" || "<<default>>" == exchangeName || exchangeName.indexOf("amq.") == 0 || exchangeName.indexOf("qpid.") == 0;
           };

           util.deleteGridSelections = function(updater, grid, url, confirmationMessageStart, idParam)
           {
               var data = grid.selection.getSelected();
               var success = false;
               if(data.length)
               {
                   var confirmationMessage = null;
                   if (data.length == 1)
                   {
                       confirmationMessage = confirmationMessageStart + " '" + data[0].name + "'?";
                   }
                   else
                   {
                       var names = '';
                       for(var i = 0; i<data.length; i++)
                       {
                           if (names)
                           {
                               names += ', ';
                           }
                           names += "\""+ data[i].name + "\"";
                       }
                       confirmationMessage = confirmationMessageStart + "s " + names + "?";
                   }
                   if(confirm(confirmationMessage))
                   {
                       var i, queryParam;
                       for(i = 0; i<data.length; i++)
                       {
                           if(queryParam)
                           {
                               queryParam += "&";
                           }
                           else
                           {
                               queryParam = "?";
                           }
                           queryParam += ( idParam || "id" ) + "=" + encodeURIComponent(data[i].id);
                       }
                       var query = url + queryParam;
                       var failureReason = "";
                       xhr.del({url: query, sync: true, handleAs: "json"}).then(
                           function(data)
                           {
                               success = true;
                               grid.selection.deselectAll();
                               if (updater)
                               {
                                 updater.update();
                               }
                           },
                           function(error) {success = false; failureReason = error;});
                       if(!success )
                       {
                           alert("Error:" + failureReason);
                       }
                   }
               }
               return success;
           }

           util.isProviderManagingUsers = function(type)
           {
               return (type === "PlainPasswordFile" || type === "Base64MD5PasswordFile" || type === "SCRAM-SHA-1" || type === "SCRAM-SHA-256");
           };

           util.showSetAttributesDialog = function(attributeWidgetFactories, data, putURL, dialogTitle, appendNameToUrl)
           {
              var layout = new dojox.layout.TableContainer({
                   cols: 1,
                   "labelWidth": "300",
                   showLabels: true,
                   orientation: "horiz",
                   customClass: "formLabel"
              });
              var submitButton = new dijit.form.Button({label: "Submit", type: "submit"});
              var form = new dijit.form.Form();

              var dialogContent = dom.create("div");
              var dialogContentArea = dom.create("div", {"style": {width: 600}});
              var dialogActionBar = dom.create("div", { "class": "dijitDialogPaneActionBar"} );
              dialogContent.appendChild(dialogContentArea);
              dialogContent.appendChild(dialogActionBar);
              dialogContentArea.appendChild(layout.domNode)
              dialogActionBar.appendChild(submitButton.domNode);
              form.domNode.appendChild(dialogContent);

              var widgets = {};
              var requiredFor ={};
              var groups = {};
              for(var i in attributeWidgetFactories)
              {
                 var attributeWidgetFactory = attributeWidgetFactories[i];
                 var widget = attributeWidgetFactory.createWidget(data);
                 var name = attributeWidgetFactory.name ? attributeWidgetFactory.name : widget.name;
                 widgets[name] = widget;
                 var dotPos = name.indexOf(".");
                 if (dotPos == -1)
                 {
                   if (widget instanceof dijit.layout.ContentPane)
                   {
                     dialogContentArea.appendChild(widget.domNode);
                   }
                   else
                   {
                     layout.addChild(widget);
                   }
                 }
                 else
                 {
                   var groupName = name.substring(0, dotPos);
                   var groupFieldContainer = null;
                   if (groups.hasOwnProperty(groupName))
                   {
                       groupFieldContainer = groups[groupName];
                   }
                   else
                   {
                     groupFieldContainer = new dojox.layout.TableContainer({
                           cols: 1,
                           "labelWidth": "290",
                           showLabels: true,
                           orientation: "horiz",
                           customClass: "formLabel"
                      });
                     groups[groupName] = groupFieldContainer;
                     var groupTitle = attributeWidgetFactory.groupName ? attributeWidgetFactory.groupName :
                                         groupName.charAt(0).toUpperCase() + groupName.slice(1);
                     var panel = new dijit.TitlePane({title: groupTitle, content: groupFieldContainer.domNode});
                     dialogContentArea.appendChild(dom.create("br"));
                     dialogContentArea.appendChild(panel.domNode);
                   }
                   groupFieldContainer.addChild(widget);
                 }
                 if (attributeWidgetFactory.hasOwnProperty("requiredFor") && !data[name])
                 {
                   requiredFor[attributeWidgetFactory.requiredFor] = widget;
                 }
              }

              // add onchange handler to set required property for dependent widget
              for(var widgetName in requiredFor)
              {
                var dependent = requiredFor[widgetName];
                var widget = widgets[widgetName];
                if (widget)
                {
                  widget.dependent = dependent;
                  widget.on("change", function(newValue){
                    this.dependent.set("required", newValue != "");
                  });
                }
              }
              var setAttributesDialog = new dijit.Dialog({
                 title: dialogTitle,
                 content: form
             });
             form.on("submit", function(e)
             {
               event.stop(e);
               try
               {
                 if(form.validate())
                 {
                   var values = {};
                   var formWidgets = form.getDescendants();
                   for(var i in formWidgets)
                   {
                       var widget = formWidgets[i];
                       var value = widget.value;
                       var propName = widget.name;
                       if (propName && !widget.disabled){
                           if ((widget instanceof dijit.form.CheckBox || widget instanceof dijit.form.RadioButton)) {
                               if (widget.checked != widget.initialValue) {
                                   values[ propName ] = widget.checked;
                               }
                           } else if (value != widget.initialValue) {
                               values[ propName ] = value ? value: null;
                           }
                       }
                   }

                     var that = this;
                     var url = putURL;
                     if (appendNameToUrl){
                       url = url + "/" + encodeURIComponent(values["name"]);
                     }
                     xhr.put({url: url , sync: true, handleAs: "json",
                              headers: { "Content-Type": "application/json"},
                              putData: json.stringify(values),
                              load: function(x) {that.success = true; },
                              error: function(error) {that.success = false; that.failureReason = error;}});
                     if(this.success === true)
                     {
                         setAttributesDialog.destroy();
                     }
                     else
                     {
                         alert("Error:" + this.failureReason);
                     }
                     return false;
                 }
                 else
                 {
                     alert('Form contains invalid data.  Please correct first');
                     return false;
                 }
               }
               catch(e)
               {
                  alert("Unexpected exception:" + e.message);
                  return false;
               }
             });
             form.connectChildren(true);
             setAttributesDialog.startup();
             var formWidgets = form.getDescendants();
             var aproximateHeight = 0;
             for(var i in formWidgets){
                 var widget = formWidgets[i];
                 var propName = widget.name;
                 if (propName) {
                     if ((widget instanceof dijit.form.CheckBox || widget instanceof dijit.form.RadioButton)) {
                         widget.initialValue = widget.checked;
                     } else {
                         widget.initialValue = widget.value;
                     }
                     aproximateHeight += 30;
                 }
             }
             var viewport = win.getBox();
             var maxHeight = Math.max(Math.floor(viewport.h * 0.6), 100);
             dialogContentArea.style.overflow= "auto";
             dialogContentArea.style.height = Math.min(aproximateHeight, maxHeight ) + "px";
             setAttributesDialog.on("hide", function(e){setAttributesDialog.destroy();});
             setAttributesDialog.show();
           };

           util.xhrErrorHandler = function(error)
           {
             if (error)
             {
               if (error.hasOwnProperty("status"))
               {
                 if (error.status == 401)
                 {
                   dojo.byId("statusMessage").innerHTML = "401 - Authentication required.";
                 }
                 else if (error.status == 403)
                 {
                   dojo.byId("statusMessage").innerHTML = "403 - Access denied.";
                 }
                 else
                 {
                   dojo.byId("statusMessage").innerHTML = "HTTP status code: " + error.status;
                 }
               }
               else
               {
                 dojo.byId("statusMessage").innerHTML = "";
               }
               if (error.hasOwnProperty("message"))
               {
                 dojo.byId("errorDetailsMessage").innerHTML = error.message;
                 dojo.byId("errorDetails").style.display = "block";
               }
               else
               {
                 dojo.byId("errorDetails").style.display = "none";
               }
               var dialog = dijit.byId("errorDialog");
               if (!dialog.open)
               {
                 dialog.show();
               }
             }
           };

           util.errorHandler = function errorHandler(error)
           {
               if(error.status == 401)
               {
                   alert("Authentication Failed");
               }
               else if(error.status == 403)
               {
                   alert("Access Denied");
               }
               else
               {
                   alert(error);
               }
           }

           util.sendRequest = function (url, method, attributes, sync)
           {
               var success = false;
               var failureReason = "";
               var syncRequired = sync == undefined ? true : sync;
               if (method == "POST" || method == "PUT")
               {
                 xhr.put({
                   url: url,
                   sync: syncRequired,
                   handleAs: "json",
                   headers: { "Content-Type": "application/json"},
                   putData: json.stringify(attributes),
                   load: function(x) {success = true; },
                   error: function(error) {success = false; failureReason = error;}
                 });
               }
               else if (method == "DELETE")
               {
                 xhr.del({url: url, sync: syncRequired, handleAs: "json"}).then(
                       function(data) { success = true; },
                       function(error) {success = false; failureReason = error;}
                 );
               }

               if (syncRequired && !success)
               {
                   alert("Error:" + failureReason);
               }
               return success;
           }

           util.equals = function(object1, object2)
           {
             if (object1 && object2)
             {
               if (typeof object1 != typeof object2)
               {
                 return false;
               }
               else
               {
                 if (object1 instanceof Array || typeof object1 == "array")
                 {
                   if (object1.length != object2.length)
                   {
                     return false;
                   }

                   for (var i = 0, l=object1.length; i < l; i++)
                   {
                     var item = object1[i];
                     if (item && (item instanceof Array  || typeof item == "array" || item instanceof Object))
                     {
                       if (!this.equals(item, object2[i]))
                       {
                         return false;
                       }
                     }
                     else if (item != object2[i])
                     {
                         return false;
                     }
                   }

                   return true;
                 }
                 else if (object1 instanceof Object)
                 {
                   for (propName in object1)
                   {
                       if (object1.hasOwnProperty(propName) != object2.hasOwnProperty(propName))
                       {
                           return false;
                       }
                       else if (typeof object1[propName] != typeof object2[propName])
                       {
                           return false;
                       }
                   }

                   for(propName in object2)
                   {
                       var object1Prop = object1[propName];
                       var object2Prop = object2[propName];

                       if (object2.hasOwnProperty(propName) != object1.hasOwnProperty(propName))
                       {
                           return false;
                       }
                       else if (typeof object1Prop != typeof object2Prop)
                       {
                           return false;
                       }

                       if(!object2.hasOwnProperty(propName))
                       {
                         // skip functions
                         continue;
                       }

                       if (object1Prop && (object1Prop instanceof Array || typeof object1Prop == "array" || object1Prop instanceof Object))
                       {
                         if (!this.equals(object1Prop, object2Prop))
                         {
                           return false;
                         }
                       }
                       else if(object1Prop != object2Prop)
                       {
                          return false;
                       }
                   }
                   return true;
                 }
               }
             }
             return object1 === object2;
           }

           return util;
       });
