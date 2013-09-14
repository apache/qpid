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
define([
        "dojo/_base/declare",
        "dojo/_base/xhr",
        "dojo/_base/event",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/parser",
        "dojo/query",
        "dojo/json",
        "dojox/html/entities",
        "dijit/registry",
        "qpid/common/TimeZoneSelector",
        "dojo/text!../../showPreferences.html",
        "dijit/Dialog",
        "dijit/form/NumberSpinner",
        "dijit/form/CheckBox",
        "dijit/form/Textarea",
        "dijit/form/FilteringSelect",
        "dijit/form/TextBox",
        "dijit/form/DropDownButton",
        "dijit/form/Button",
        "dijit/form/Form",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
function (declare, xhr, event, dom, domConstruct, parser, query, json, entities, registry, TimeZoneSelector, markup) {

  var preferenceNames = ["timeZone", "updatePeriod", "saveTabs"];

  return declare("qpid.management.Preferences", null, {

    preferencesDialog: null,
    saveButton: null,
    cancelButton: null,

    constructor: function()
    {
      var that = this;

      this.domNode = domConstruct.create("div", {innerHTML: markup});
      parser.parse(this.domNode);

      for(var i=0; i<preferenceNames.length; i++)
      {
        var name = preferenceNames[i];
        this[name] = registry.byNode(query("." + name, this.domNode)[0]);
      }

      this.saveButton = registry.byNode(query(".saveButton", this.domNode)[0]);
      this.cancelButton = registry.byNode(query(".cancelButton", this.domNode)[0]);
      this.theForm = registry.byId("preferencesForm");

      this.preferencesDialog = new dijit.Dialog({
        title:"Preferences",
        style: "width: 600px",
        content: this.domNode
      });

      this.cancelButton.on("click", function(){that.preferencesDialog.hide();});
      this.theForm.on("submit", function(e){
        event.stop(e);
        if(that.theForm.validate()){
          var preferences = {};
          for(var i=0; i<preferenceNames.length; i++)
          {
            var name = preferenceNames[i];
            var preferenceWidget = that[name];
            if (preferenceWidget)
            {
              preferences[name] = preferenceWidget.get("value");
            }
          }
          xhr.post({
                url: "rest/preferences",
                sync: true,
                handleAs: "json",
                headers: { "Content-Type": "application/json"},
                postData: json.stringify(preferences),
                load: function(x) {that.success = true; },
                error: function(error) {that.success = false; that.failureReason = error;}
          });
          if(that.success === true)
          {
            that.preferencesDialog.hide();
          }
          else
          {
            alert("Error:" + that.failureReason);
          }
        }
        return false;
      });
      this.preferencesDialog.startup();
    },

    showDialog: function(){
      var that = this;
      xhr.get({
        url: "rest/preferences",
        sync: true,
        handleAs: "json"
      }).then(
       function(data) {
         for(var preference in data)
         {
           if (that.hasOwnProperty(preference))
           {
             var value = data[preference];
             if (typeof data[preference] == "string")
             {
               value = entities.encode(String(value))
             }
             that[preference].set("value", value);
           }
         }
         that.preferencesDialog.show();
      });
    },

    destroy: function()
    {
      if (this.preferencesDialog)
      {
        this.preferencesDialog.destroyRecursevly();
        this.preferencesDialog = null;
      }
    }
  });
});