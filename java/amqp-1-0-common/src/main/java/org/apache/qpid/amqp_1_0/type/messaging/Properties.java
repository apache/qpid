
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


package org.apache.qpid.amqp_1_0.type.messaging;


import org.apache.qpid.amqp_1_0.messaging.SectionEncoder;


import java.util.Date;



import org.apache.qpid.amqp_1_0.type.*;

public class Properties
  implements Section
  {


    private Object _messageId;

    private Binary _userId;

    private String _to;

    private String _subject;

    private String _replyTo;

    private Object _correlationId;

    private Symbol _contentType;

    private Symbol _contentEncoding;

    private Date _absoluteExpiryTime;

    private Date _creationTime;

    private String _groupId;

    private UnsignedInteger _groupSequence;

    private String _replyToGroupId;

    public Object getMessageId()
    {
        return _messageId;
    }

    public void setMessageId(Object messageId)
    {
        _messageId = messageId;
    }

    public Binary getUserId()
    {
        return _userId;
    }

    public void setUserId(Binary userId)
    {
        _userId = userId;
    }

    public String getTo()
    {
        return _to;
    }

    public void setTo(String to)
    {
        _to = to;
    }

    public String getSubject()
    {
        return _subject;
    }

    public void setSubject(String subject)
    {
        _subject = subject;
    }

    public String getReplyTo()
    {
        return _replyTo;
    }

    public void setReplyTo(String replyTo)
    {
        _replyTo = replyTo;
    }

    public Object getCorrelationId()
    {
        return _correlationId;
    }

    public void setCorrelationId(Object correlationId)
    {
        _correlationId = correlationId;
    }

    public Symbol getContentType()
    {
        return _contentType;
    }

    public void setContentType(Symbol contentType)
    {
        _contentType = contentType;
    }

    public Symbol getContentEncoding()
    {
        return _contentEncoding;
    }

    public void setContentEncoding(Symbol contentEncoding)
    {
        _contentEncoding = contentEncoding;
    }

    public Date getAbsoluteExpiryTime()
    {
        return _absoluteExpiryTime;
    }

    public void setAbsoluteExpiryTime(Date absoluteExpiryTime)
    {
        _absoluteExpiryTime = absoluteExpiryTime;
    }

    public Date getCreationTime()
    {
        return _creationTime;
    }

    public void setCreationTime(Date creationTime)
    {
        _creationTime = creationTime;
    }

    public String getGroupId()
    {
        return _groupId;
    }

    public void setGroupId(String groupId)
    {
        _groupId = groupId;
    }

    public UnsignedInteger getGroupSequence()
    {
        return _groupSequence;
    }

    public void setGroupSequence(UnsignedInteger groupSequence)
    {
        _groupSequence = groupSequence;
    }

    public String getReplyToGroupId()
    {
        return _replyToGroupId;
    }

    public void setReplyToGroupId(String replyToGroupId)
    {
        _replyToGroupId = replyToGroupId;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("Properties{");
        final int origLength = builder.length();

        if(_messageId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("messageId=").append(_messageId);
        }

        if(_userId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("userId=").append(_userId);
        }

        if(_to != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("to=").append(_to);
        }

        if(_subject != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("subject=").append(_subject);
        }

        if(_replyTo != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("replyTo=").append(_replyTo);
        }

        if(_correlationId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("correlationId=").append(_correlationId);
        }

        if(_contentType != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("contentType=").append(_contentType);
        }

        if(_contentEncoding != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("contentEncoding=").append(_contentEncoding);
        }

        if(_absoluteExpiryTime != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("absoluteExpiryTime=").append(_absoluteExpiryTime);
        }

        if(_creationTime != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("creationTime=").append(_creationTime);
        }

        if(_groupId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("groupId=").append(_groupId);
        }

        if(_groupSequence != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("groupSequence=").append(_groupSequence);
        }

        if(_replyToGroupId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("replyToGroupId=").append(_replyToGroupId);
        }

        builder.append('}');
        return builder.toString();
    }


      public Binary encode(final SectionEncoder encoder)
      {
        encoder.reset();
        encoder.encodeObject(this);
        return encoder.getEncoding();
      }


  }
