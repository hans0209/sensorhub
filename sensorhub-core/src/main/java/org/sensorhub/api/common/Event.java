/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.common;

import java.io.Serializable;


/**
 * <p>
 * Immutable base class for all sensor hub events.
 * All sub-classes should remain immutable.
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Nov 5, 2010
 */
public class Event implements Serializable
{
    private static final long serialVersionUID = 9176636296502966036L;
    
    protected Object source;
    protected long timeStamp;
    protected int code;
    
    
    public Object getSource()
    {
        return source;
    }


    public long getTimeStamp()
    {
        return timeStamp;
    }


    public int getCode()
    {
        return code;
    }    
    
}
