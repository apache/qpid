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
define(["dojo/_base/declare"], function(declare)
{
    return declare("qpid.common.FormWidgetMixin", null,
    {
        name: "",
        value: "",
        _onChangeActive: false,

        compare: function(val1, val2)
        {
            if(typeof val1 == "number" && typeof val2 == "number")
            {
                return (isNaN(val1) && isNaN(val2)) ? 0 : val1 - val2;
            }
            else if(val1 > val2)
            {
                return 1;
            }
            else if(val1 < val2)
            {
                return -1;
            }
            else
            {
                return 0;
            }
        },
        onChange: function()
        {
        },
        _setValueAttr: function(newValue, priorityChange)
        {
            this._handleOnChange(newValue, priorityChange);
        },
        _handleOnChange: function(newValue, priorityChange)
        {
            this._set("value", newValue);
            if(this._lastValueReported == undefined && (priorityChange === null || !this._onChangeActive))
            {
                this._resetValue = this._lastValueReported = newValue;
            }
            this._pendingOnChange = this._pendingOnChange || (typeof newValue != typeof this._lastValueReported)
             || (this.compare(newValue, this._lastValueReported) != 0);
            if(( priorityChange || priorityChange === undefined) && this._pendingOnChange)
            {
                this._lastValueReported = newValue;
                this._pendingOnChange = false;
                if(this._onChangeActive)
                {
                    if(this._onChangeHandle)
                    {
                        this._onChangeHandle.remove();
                    }
                    this._onChangeHandle = this.defer(function() { this._onChangeHandle = null; this.onChange(newValue); });
                }
            }
        },
        create: function()
        {
         this.inherited(arguments);
         this._onChangeActive = true;
        },
        destroy: function()
        {
            if(this._onChangeHandle)
            {
                this._onChangeHandle.remove();
                this.onChange(this._lastValueReported);
            }
            this.inherited(arguments);
        },
        undo: function()
        {
            this._setValueAttr(this._lastValueReported, false);
        },
        reset: function()
        {
            this._hasBeenBlurred = false;
            this._setValueAttr(this._resetValue, true);
        }
    });
});