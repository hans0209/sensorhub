/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2016 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor;

import java.util.ArrayList;
import java.util.List;
import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.processing.ProcessConfig;
import org.sensorhub.api.sensor.SensorConfig;


/**
 * <p>
 * Configuration class for SensorGroup modules
 * </p>
 *
 * @author Alex Robin
 * @since Apr 1, 2016
 */
public class SensorSystemConfig extends SensorConfig
{    
   
    public static class SensorMember
    {
        public String name;
        public SensorConfig config;
    }
    
    
    
    @DisplayInfo(label="System Sensors", desc="Configuration of sensor components of this sensor system")
    public List<SensorMember> sensors = new ArrayList<SensorMember>();    
    
    @DisplayInfo(label="System Processes", desc="Configuration of processing components of this sensor system")
    public List<ProcessConfig> processes = new ArrayList<ProcessConfig>();
}
