/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.persistence;

import net.opengis.swe.v20.DataBlock;
import org.sensorhub.api.common.Event;
import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.persistence.DataKey;
import org.sensorhub.api.persistence.IBasicStorage;
import org.sensorhub.api.persistence.ITimeSeriesDataStore;
import org.sensorhub.api.persistence.StorageException;
import org.sensorhub.api.sensor.ISensorDataInterface;
import org.sensorhub.api.sensor.ISensorModule;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.api.sensor.SensorEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.SensorHub;
import org.sensorhub.impl.module.AbstractModule;
import org.sensorhub.impl.module.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <p>
 * This class is a simple event-based process that saves data generated by a
 * sensor in the specified basic storage.
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Oct 5, 2013
 */
public class SensorStorageHelper extends AbstractModule<SensorStorageHelperConfig> implements IEventListener
{
    private static final Logger log = LoggerFactory.getLogger(SensorStorageHelper.class);
    
    IBasicStorage<?> storage;
    ISensorModule<?> sensor;


    public SensorStorageHelper()
    {
    }

    
    @Override
    public void start() throws SensorHubException
    {
        // get handle to storage and sensor
        ModuleRegistry moduleReg = SensorHub.getInstance().getModuleRegistry();
        storage = (IBasicStorage<?>)moduleReg.getModuleById(config.storageID);
        sensor = (ISensorModule<?>)moduleReg.getModuleById(config.sensorID);
        
        // register to sensor events
        if (config.selectedOutputs == null || config.selectedOutputs.length == 0)
        {
            for (ISensorDataInterface output: sensor.getAllOutputs().values())
                output.registerListener(this);
        }
        else
        {
            for (String outputName: config.selectedOutputs)
                sensor.getAllOutputs().get(outputName).registerListener(this);
        }
        
        // if storage is empty, initialize it
        if (storage.getLatestDataSourceDescription() == null)
            StorageHelper.configureStorageForSensor(sensor, storage, false);
        
        // get the latest sensor description in case we were down during the last update
        storage.storeDataSourceDescription(sensor.getCurrentSensorDescription());
    }
    
    
    @Override
    public void stop() throws StorageException
    {
        try
        {
            if (config.selectedOutputs == null || config.selectedOutputs.length == 0)
            {
                for (ISensorDataInterface output: sensor.getAllOutputs().values())
                    output.unregisterListener(this);
            }
            else
            {
                for (String outputName: config.selectedOutputs)
                    sensor.getAllOutputs().get(outputName).unregisterListener(this);
            }
        }
        catch (SensorException e)
        {
        }
    }


    @Override
    public void cleanup() throws StorageException
    {
        storage = null;
        sensor = null;
    }
    
    
    @Override
    public void handleEvent(Event e)
    {
        if (isEnabled())
        {
            // new data events
            if (e instanceof SensorDataEvent)
            {
                boolean saveAutoCommitState = storage.isAutoCommit();
                storage.setAutoCommit(false);
                
                // get datastore for output name
                String outputName = ((SensorDataEvent) e).getSource().getName();
                ITimeSeriesDataStore<?> dataStore = storage.getDataStores().get(outputName);
                
                String producer = ((SensorDataEvent) e).getSensorId();
                
                for (DataBlock record: ((SensorDataEvent) e).getRecords())
                {
                    DataKey key = new DataKey(producer, e.getTimeStamp()/1000.);
                    dataStore.store(key, record);
                    if (log.isTraceEnabled())
                    {
                        log.trace("Storing record " + key.timeStamp + " in DB");
                        log.trace("DB size: " + dataStore.getNumRecords());
                    }
                }
                
                storage.commit();
                storage.setAutoCommit(saveAutoCommitState);
            }
            
            else if (e instanceof SensorEvent)
            {
                if (((SensorEvent) e).getType() == SensorEvent.Type.SENSOR_CHANGED)
                {
                    try
                    {
                        // TODO check that description was actually updated?
                        // in the current state, the same description would be added at each restart
                        // should we compare contents? if not, on what time tag can we rely on?
                        // AbstractSensorModule implementation of getLastSensorDescriptionUpdate() is
                        // only useful between restarts since it will be resetted to current time at startup...
                        
                        // TODO to manage this issue, first check that no other description is valid at the same time
                        storage.storeDataSourceDescription(sensor.getCurrentSensorDescription());
                    }
                    catch (StorageException | SensorException ex)
                    {
                        log.error("Error while updating sensor description", ex);
                    }
                }
            }
        }
    }

}
