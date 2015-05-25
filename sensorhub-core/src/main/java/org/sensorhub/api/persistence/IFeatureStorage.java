/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.persistence;

import java.util.Iterator;
import net.opengis.gml.v32.AbstractFeature;


/**
 * <p>
 * Interface for feature data storage implementations. This type of storage
 * provides spatial filtering capabilities.
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Nov 6, 2010
 */
public interface IFeatureStorage
{
    
    
    public int getNumFeatures();
    
    
    /**
     * @param uid unique ID of feature
     * @return Feature object or null if none was found
     */
    public AbstractFeature getFeatureById(String uid);
    
    
    /**
     * Gets features matching the specified filter
     * @param filter filtering parameters
     * @return an iterator over features matching the filter, sorted by ID
     */
    public Iterator<AbstractFeature> getFeatureIterator(IFeatureFilter filter);
}
