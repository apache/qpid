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
        "dojo/_base/array",
        "dojo/_base/lang",
        "qpid/common/util",
        "dijit/_Widget",
        "dijit/_TemplatedMixin",
        "dijit/_WidgetsInTemplateMixin",
        "qpid/common/FormWidgetMixin",
        "dojo/text!common/ResourceWidget.html",
        "dojox/html/entities",
        "dojox/form/Uploader",
        "dijit/form/Button",
        "dijit/form/ValidationTextBox",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
function (declare, array, lang, util, _WidgetBase, _TemplatedMixin, _WidgetsInTemplateMixin, FormWidgetMixin, template, entities)
{

  return declare("qpid.common.ResourceWidget", [_WidgetBase, _TemplatedMixin, _WidgetsInTemplateMixin, FormWidgetMixin],
   {
       templateString: template,
       fileReaderSupported: window.FileReader ? true : false,
       displayWarningWhenFileReaderUnsupported: false,
       isDebug: false,

       buildRendering: function()
       {
           //Strip out the apache comment header from the template html as comments unsupported.
           this.templateString = this.templateString.replace(/<!--[\s\S]*?-->/g, "");
           this.inherited(arguments);
       },
       postCreate: function()
       {
           this.inherited(arguments);

           if(this._resetValue === undefined)
           {
               this._lastValueReported = this._resetValue = this.value;
           }

           var that = this;

           if (this.fileReaderSupported)
           {
             this.fileReader= new FileReader();
             this.fileReader.onload = function(evt) {that._uploadFileComplete(evt);};
             this.fileReader.onerror = function(ex) {console.error("Failed to load file for " + this.name, ex);};
             this.uploader.on("change", function(selected){that._fileChanged(selected)});
             this.clearButton.on("click", function(event){that._fileClearButtonClicked(event)});
           }
           else
           {
             // Fall back for IE8/9 which do not support FileReader
             this.uploadFields.style.display = "none";
             if (displayWarningWhenFileReaderUnsupported)
             {
                 this.unsupportedWarning.className = this.unsupportedWarning.className.replace("hidden", "");
             }
           }
           this.resourceLocation.on("blur", function(){that._pathChanged()});
           this._originalValue = arguments.value;
           if (this.placeHolder)
           {
               this.resourceLocation.set("placeHolder", this.placeHolder);
           }
           if (this.promptMessage)
           {
               this.resourceLocation.set("promptMessage", this.promptMessage);
           }
           if (this.title)
           {
               this.resourceLocation.set("title", this.title);
           }
           this.uploadData.style.display = "none";
       },
       startup: function()
       {
           if (this.fileReaderSupported)
           {
               this.uploader.startup();
           }
       },
       _fileChanged: function (evt)
       {
           var file = this.uploader.domNode.children[0].files[0];
           this.selectedFileName = file.name;
           this.selectedFile.innerHTML = file.name;
           this.selectedFileStatus.className = "loadingIcon";
           if (this.isDebug)
           {
               this._log("Beginning to read file " + file.name + " for " + this.name);
           }
           this.fileReader.readAsDataURL(file);
       },
       _uploadFileComplete: function(evt)
       {
           var reader = evt.target;
           var result = reader.result;
           if (this.isDebug)
           {
                this._log(this.name + " file read complete, contents " + result);
           }
           this.set("value", result);
       },
        _fileClearButtonClicked: function(event)
       {
           this.uploader.reset();
           this.set("value", this._resetValue);
       },
       _pathChanged: function()
       {
           var serverPathValue = this.resourceLocation.get("value") || this._resetValue;
           this.set("value", serverPathValue);
       },
       _setValueAttr: function(newValue, priorityChange)
       {
          var isDataUrl = newValue && newValue.indexOf("data:") == 0;
          if (isDataUrl)
          {
            this.uploadData.style.display = "block";
            this.selectedFileStatus.className = "loadedIcon";
            this.selectedFile.innerHTML = this.selectedFileName || "uploaded data";
            this.resourceLocation.set("value", "");
            this.resourceLocation.setDisabled(true);
            this.resourceLocation.set("required", false);
            this.clearButton.setDisabled(false);
            this.selectedFileStatus.className = "loadedIcon";
          }
          else
          {
            this.resourceLocation.set("value", newValue);
            this.selectedFileName = null;
            this.selectedFileStatus.className = "";
            this.selectedFile.innerHTML = "";
            this.resourceLocation.set("required", true);
            this.resourceLocation.setDisabled(false);
            this.clearButton.setDisabled(true);
            this.uploadData.style.display = "none";
          }
          this.inherited(arguments);
       },
       _log: function(message)
       {
            if (this.isDebug)
            {
                console.log(message);
            }
       }
     }
   );
});
