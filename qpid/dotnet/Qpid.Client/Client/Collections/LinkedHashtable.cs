using System;
using System.Collections;

namespace Qpid.Collections
{
    public class LinkedHashtable : DictionaryBase
    {
        /// <summary>
        /// Maps from key to LinkedDictionaryEntry        
        /// </summary>
        private Hashtable _indexedValues = new Hashtable();

        private LinkedDictionaryEntry _head;

        private LinkedDictionaryEntry _tail;

        public class LinkedDictionaryEntry
        {
            public LinkedDictionaryEntry previous;
            public LinkedDictionaryEntry next;
            public object key;
            public object value;

            public LinkedDictionaryEntry(object key, object value)
            {
                this.key = key;
                this.value = value;
            }
        }

        public object this[object index]
        {
            get
            {
                return ((LinkedDictionaryEntry)_indexedValues[index]).value;
            }

            set
            {
                Dictionary[index] = value;
            }
        }
        
        protected override void OnInsertComplete(object key, object value)
        {
            LinkedDictionaryEntry de = new LinkedDictionaryEntry(key, value);            
            if (_head == null)
            {
                _head = de;
                _tail = de;
            }
            else
            {
                _tail.next = de;
                de.previous = _tail;
                _tail = de;
            }
            _indexedValues[key] = de;
        }

        protected override void OnSetComplete(object key, object oldValue, object newValue)
        {
            if (oldValue == null)
            {
                OnInsertComplete(key, newValue);
            }        
        }

        protected override void OnRemoveComplete(object key, object value)
        {
            LinkedDictionaryEntry de = (LinkedDictionaryEntry)_indexedValues[key];
            LinkedDictionaryEntry prev = de.previous;
            if (prev == null)
            {
                _head = de.next;
            }
            else
            {
                prev.next = de.next;
            }

            LinkedDictionaryEntry next = de.next;
            if (next == null)
            {
                _tail = de;
            }
            else
            {
                next.previous = de.previous;
            }
        }

        public ICollection Values
        {
            get
            {
                return InnerHashtable.Values;
            }
        }

        public bool Contains(object key)
        {
            return InnerHashtable.Contains(key);
        }

        public void Remove(object key)
        {
            Dictionary.Remove(key);
        }
        
        public LinkedDictionaryEntry Head
        {
            get
            {
                return _head;
            }
        }

        public LinkedDictionaryEntry Tail
        {
            get
            {
                return _tail;
            }
        }

        private class LHTEnumerator : IEnumerator
        {
            private LinkedHashtable _container;

            private LinkedDictionaryEntry _current;

            /// <summary>
            /// Set once we have navigated off the end of the collection
            /// </summary>
            private bool _needsReset = false;

            public LHTEnumerator(LinkedHashtable container)
            {
                _container = container;                
            }

            public object Current
            {
                get
                {
                    if (_current == null)
                    {
                        throw new Exception("Iterator before first element");
                    }
                    else
                    {
                        return _current;
                    }
                }
            }

            public bool MoveNext()
            {
                if (_needsReset)
                {
                    return false;
                }
                else if (_current == null)
                {
                    _current = _container.Head;                    
                }
                else
                {
                    _current = _current.next;
                }
                _needsReset = (_current == null);
                return !_needsReset;                
            }

            public void Reset()
            {
                _current = null;
                _needsReset = false;
            }
        }

        public new IEnumerator GetEnumerator()
        {
            return new LHTEnumerator(this);
        }

        public void MoveToHead(object key)
        {
            LinkedDictionaryEntry de = (LinkedDictionaryEntry)_indexedValues[key];
            if (de == null)
            {
                throw new ArgumentException("Key " + key + " not found");
            }
            // if the head is the element then there is nothing to do
            if (_head == de)
            {
                return;
            }
            de.previous.next = de.next;
            if (de.next != null)
            {
                de.next.previous = de.previous;
            }
            else
            {
                _tail = de.previous;
            }
            de.next = _head;
            _head = de;
            de.previous = null;
        }
    }
}
