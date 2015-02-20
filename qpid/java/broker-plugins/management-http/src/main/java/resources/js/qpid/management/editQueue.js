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
        "dojox/html/entities",
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
        "qpid/common/util",
        "dojo/text!editQueue.html",
        "qpid/common/ContextVariablesEditor",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/FilteringSelect",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (xhr, entities, array, event, lang, win, dom, domConstruct, registry, parser, json, query, util, template)
  {
    var fields = ["name",
                  "type",
                  "durable",
                  "messageDurability",
                  "maximumMessageTtl",
                  "minimumMessageTtl",
                  "queueFlowControlSizeBytes",
                  "queueFlowResumeSizeBytes",
                  "alertThresholdQueueDepthMessages",
                  "alertThresholdQueueDepthBytes",
                  "alertThresholdMessageAge",
                  "alertThresholdMessageSize",
                  "alertRepeatGap",
                  "maximumDeliveryAttempts",
                  "priorities",
                  "lvqKey",
                  "sortKey"];

    var numericFieldNames = ["maximumMessageTtl",
                  "minimumMessageTtl",
                  "queueFlowControlSizeBytes",
                  "queueFlowResumeSizeBytes",
                  "alertThresholdQueueDepthMessages",
                  "alertThresholdQueueDepthBytes",
                  "alertThresholdMessageAge",
                  "alertThresholdMessageSize",
                  "alertRepeatGap",
                  "maximumDeliveryAttempts"];

    var queueEditor =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode).then(function(instances){ that._postParse();});
      },
      _postParse: function()
      {
        var that=this;
        this.allFieldsContainer = dom.byId("formEditQueue.allFields");
        this.dialog = registry.byId("editQueue");
        this.saveButton = registry.byId("formEditQueue.saveButton");
        this.cancelButton = registry.byId("formEditQueue.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.saveButton.on("click", function(e){that._save(e);});
        for(var i = 0; i < fields.length; i++)
        {
            var fieldName = fields[i];
            this[fieldName] = registry.byId("formEditQueue." + fieldName);
        }
        this.form = registry.byId("formEditQueue");
        this.form.on("submit", function(){return false;});
        this.typeSelector = registry.byId("formEditQueue.type");
      },
      show: function(hostData)
      {
        var that=this;
        if (!this.context)
        {
         this.context = new qpid.common.ContextVariablesEditor({name: 'context', title: 'Context variables'});
         this.context.placeAt(dom.byId("formEditQueue.context"));
        }
        this.query = "api/latest/queue/" + encodeURIComponent(hostData.nodeName) + "/" + encodeURIComponent(hostData.hostName) + "/" + encodeURIComponent(hostData.queueName);
        this.dialog.set("title", "Edit Queue - " + entities.encode(String(hostData.queueName)));
        xhr.get(
            {
              url: this.query,
              sync: true,
              content: { actuals: true },
              handleAs: "json",
              load: function(data)
              {
                that._show(data[0], hostData);
              }
            }
        );
        var queueType = this.typeSelector.get("value");
        query(".typeSpecificDiv").forEach(function(node, index, arr){
            if (node.id === "formEditQueueType:" + queueType)
            {
                node.style.display = "block";
                util.applyMetadataToWidgets(node, "Queue", queueType);
            }
            else
            {
                node.style.display = "none";
            }
        });
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
              var data = util.getFormWidgetValues(this.form, this.initialData);
              var context = this.context.get("value");
              if (context && !util.equals(context, this.initialData.context))
              {
                data["context"] = context;
              }
              var success = false,failureReason=null;
              xhr.put({
                  url: this.query,
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
                  util.xhrErrorHandler(failureReason);
              }
          }
          else
          {
              alert('Form contains invalid data.  Please correct first');
          }
      },
      _show:function(actualData, effectiveData)
      {

          this.initialData = actualData;
          for(var i = 0; i < fields.length; i++)
          {
            var fieldName = fields[i];
            var widget = this[fieldName];
            widget.reset();

            if (widget instanceof dijit.form.CheckBox)
            {
              widget.set("checked", actualData[fieldName]);
            }
            else
            {
              widget.set("value", actualData[fieldName]);
            }
          }

          var that = this;
          util.applyMetadataToWidgets(that.allFieldsContainer, "Queue", actualData.type);

          this.context.load(this.query, {actualValues:actualData.context, effectiveValues:effectiveData.context});

          // Add regexp to the numeric fields
          for(var i = 0; i < numericFieldNames.length; i++)
          {
            this[numericFieldNames[i]].set("regExpGen", util.numericOrContextVarRegexp);
          }

          this.dialog.startup();
          this.dialog.show();
          if (!this.resizeEventRegistered)
          {
            this.resizeEventRegistered = true;
            util.resizeContentAreaAndRepositionDialog(dom.byId("formEditQueue.contentPane"), this.dialog);
          }
      }
    };

    queueEditor.init();

    return queueEditor;
  }
);
