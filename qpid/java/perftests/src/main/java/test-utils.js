var QPID;
if (!QPID) {
    QPID = {};
}
(function () {
    'use strict';

    if (typeof QPID.times !== 'function') {
        QPID.times = function (multiplicity, template, timeIndexName)
        {
            var retVal = new Array();
            for (var i = 0; i < multiplicity; i++)
            {
                var templateName = template._name;
                var teamplateAsString = JSON.stringify(template);
                if (timeIndexName)
                {
                    teamplateAsString = teamplateAsString.replace(new RegExp(timeIndexName, "g"), i);
                }
                var expandedObject = JSON.parse(teamplateAsString);
                if (!(timeIndexName))
                {
                  expandedObject._name = templateName + "_" + i;
                }
                retVal[i] = expandedObject;
            }
            return retVal;
        }
    }

    if (typeof QPID.iterations !== 'function') {
        QPID.iterations = function (values, template)
        {
            var retVal = new Array()

            var iterationNumber = 0;

            for (variableName in values)
            {
                var variableValues = values[variableName]
                for (i in variableValues)
                {
                    var variableValue = variableValues[i]
                    var templateTestString = JSON.stringify(template)
                    var actualString = templateTestString.replace(new RegExp(variableName, "g"), variableValue)
                    var iteration = JSON.parse(actualString)
                    iteration._iterationNumber = iterationNumber
                    retVal[iterationNumber] = iteration
                    iterationNumber++
                }
            }

            return retVal
        }
    }

    if (typeof QPID.transform !== 'function') {

        /**
        * Function to transform JSON using specified transformation function.
        * Any number of transformation function could be passed after the template argument.
        * Each function should return a transformed JSON object.
        * Example
        * var json = transform({"name": "Test1"}, function(json){json.name="Test"; return json;});
        */
        QPID.transform = function (template)
        {
            var json = template;
            for (var i=1, len=arguments.length; i<len; i++)
            {
                json = arguments[i](json);
            }
            return json;
        }
    }

    if (typeof QPID.cloneJSON !== 'function') {
        QPID.cloneJSON = function (json)
        {
            return JSON.parse( JSON.stringify( json ));
        }
    }

}());

