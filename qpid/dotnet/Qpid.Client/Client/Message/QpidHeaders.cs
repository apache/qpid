using System;
using System.Collections;
using System.Text;
using Qpid.Framing;
using Qpid.Messaging;

namespace Qpid.Client.Message
{
    internal class QpidHeaders : IHeaders
    {
        public const char BOOLEAN_PROPERTY_PREFIX = 'B';
        public const char BYTE_PROPERTY_PREFIX = 'b';
        public const char SHORT_PROPERTY_PREFIX = 's';
        public const char INT_PROPERTY_PREFIX = 'i';
        public const char LONG_PROPERTY_PREFIX = 'l';
        public const char FLOAT_PROPERTY_PREFIX = 'f';
        public const char DOUBLE_PROPERTY_PREFIX = 'd';
        public const char STRING_PROPERTY_PREFIX = 'S';

        AbstractQmsMessage _message;
        
        public QpidHeaders(AbstractQmsMessage message)
        {
            _message = message;
        }

        public bool Contains(string name)
        {
            CheckPropertyName(name);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return false;
            }
            else
            {
                // TODO: fix this
                return _message.ContentHeaderProperties.Headers.Contains(STRING_PROPERTY_PREFIX + name);
            }
        }

        public void Clear()
        {
            if (_message.ContentHeaderProperties.Headers != null)
            {
                _message.ContentHeaderProperties.Headers.Clear();
            }
        }

        public string this[string name]
        {
            get 
            { 
                return GetString(name);
            }
            set
            {
                SetString(name, value);
            }
        }

        public bool GetBoolean(string name)
        {
            CheckPropertyName(name);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return false;
            }
            else
            {
                object b = _message.ContentHeaderProperties.Headers[BOOLEAN_PROPERTY_PREFIX + name];

                if (b == null)
                {
                    return false;
                }
                else
                {
                    return (bool)b;
                }
            }
        }

        public void SetBoolean(string name, bool b)
        {
            CheckPropertyName(name);
            _message.ContentHeaderProperties.Headers[BOOLEAN_PROPERTY_PREFIX + name] = b;
        }

        public byte GetByte(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object b = _message.ContentHeaderProperties.Headers[BYTE_PROPERTY_PREFIX + propertyName];
                if (b == null)
                {
                    return 0;
                }
                else
                {
                    return (byte)b;
                }
            }
        }

        public void SetByte(string propertyName, byte b)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[BYTE_PROPERTY_PREFIX + propertyName] = b;
        }

        public short GetShort(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object s = _message.ContentHeaderProperties.Headers[SHORT_PROPERTY_PREFIX + propertyName];
                if (s == null)
                {
                    return 0;
                }
                else
                {
                    return (short)s;
                }
            }
        }

        public void SetShort(string propertyName, short i)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[SHORT_PROPERTY_PREFIX + propertyName] = i;
        }

        public int GetInt(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object i = _message.ContentHeaderProperties.Headers[INT_PROPERTY_PREFIX + propertyName];
                if (i == null)
                {
                    return 0;
                }
                else
                {
                    return (int)i;
                }
            }
        }

        public void SetInt(string propertyName, int i)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[INT_PROPERTY_PREFIX + propertyName] = i;
        }

        public long GetLong(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object l = _message.ContentHeaderProperties.Headers[LONG_PROPERTY_PREFIX + propertyName];
                if (l == null)
                {
                    // temp - the spec says do this but this throws a NumberFormatException
                    //return Long.valueOf(null).longValue();
                    return 0;
                }
                else
                {
                    return (long)l;
                }
            }
        }

        public void SetLong(string propertyName, long l)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[LONG_PROPERTY_PREFIX + propertyName] = l;
        }

        public float GetFloat(String propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object f = _message.ContentHeaderProperties.Headers[FLOAT_PROPERTY_PREFIX + propertyName];
                if (f == null)
                {
                    return 0;
                }
                else
                {
                    return (float)f;
                }
            }
        }

        public void SetFloat(string propertyName, float f)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[FLOAT_PROPERTY_PREFIX + propertyName] = f;
        }

        public double GetDouble(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object d = _message.ContentHeaderProperties.Headers[DOUBLE_PROPERTY_PREFIX + propertyName];
                if (d == null)
                {
                    return 0;
                }
                else
                {
                    return (double)d;
                }
            }
        }

        public void SetDouble(string propertyName, double v)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[DOUBLE_PROPERTY_PREFIX + propertyName] = v;
        }

        public string GetString(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return null;
            }
            else
            {
                return (string)_message.ContentHeaderProperties.Headers[STRING_PROPERTY_PREFIX + propertyName];
            }
        }

        public void SetString(string propertyName, string value)
        {
            CheckPropertyName(propertyName);
            CreatePropertyMapIfRequired();
            propertyName = STRING_PROPERTY_PREFIX + propertyName;
            _message.ContentHeaderProperties.Headers[propertyName] = value;
        }

        private void CheckPropertyName(string propertyName)
        {
            if (propertyName == null)
            {
                throw new ArgumentException("Property name must not be null");
            }
            else if ("".Equals(propertyName))
            {
                throw new ArgumentException("Property name must not be the empty string");
            }

            if (_message.ContentHeaderProperties.Headers == null)
            {
                _message.ContentHeaderProperties.Headers = new FieldTable();
            }
        }

        private void CreatePropertyMapIfRequired()
        {
            if (_message.ContentHeaderProperties.Headers == null)
            {
                _message.ContentHeaderProperties.Headers = new FieldTable();
            }
        }

        public override string ToString()
        {
            StringBuilder buf = new StringBuilder("{");
            int i = 0;
            foreach (DictionaryEntry entry in _message.ContentHeaderProperties.Headers)
            {
                ++i;
                if (i > 1)
                {
                    buf.Append(", ");
                }
                string propertyName = (string)entry.Key;
                if (propertyName == null)
                {
                    buf.Append("\nInternal error: Property with NULL key defined");
                }
                else
                {
                    buf.Append(propertyName.Substring(1));

                    buf.Append(" : ");

                    char typeIdentifier = propertyName[0];
                    buf.Append(typeIdentifierToName(typeIdentifier));
                    buf.Append(" = ").Append(entry.Value);
                }
            }
            buf.Append("}");
            return buf.ToString();
        }

        private static string typeIdentifierToName(char typeIdentifier)
        {
            switch (typeIdentifier)
            {
                case BOOLEAN_PROPERTY_PREFIX:
                    return "boolean";
                case BYTE_PROPERTY_PREFIX:
                    return "byte";
                case SHORT_PROPERTY_PREFIX:
                    return "short";
                case INT_PROPERTY_PREFIX:
                    return "int";
                case LONG_PROPERTY_PREFIX:
                    return "long";
                case FLOAT_PROPERTY_PREFIX:
                    return "float";
                case DOUBLE_PROPERTY_PREFIX:
                    return "double";
                case STRING_PROPERTY_PREFIX:
                    return "string";
                default:
                    return "unknown ( '" + typeIdentifier + "')";
            }
        }

    }
}