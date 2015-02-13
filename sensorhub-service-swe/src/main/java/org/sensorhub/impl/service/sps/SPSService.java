/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sps;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import org.sensorhub.api.common.Event;
import org.sensorhub.api.common.IEventHandler;
import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.module.IModuleStateLoader;
import org.sensorhub.api.module.IModuleStateSaver;
import org.sensorhub.api.module.ModuleEvent;
import org.sensorhub.api.service.IServiceModule;
import org.sensorhub.impl.SensorHub;
import org.sensorhub.impl.common.BasicEventHandler;
import org.sensorhub.impl.service.HttpServer;
import org.sensorhub.impl.service.ogc.OGCServiceConfig.CapabilitiesInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataBlockList;
import org.vast.ows.GetCapabilitiesRequest;
import org.vast.ows.OWSExceptionReport;
import org.vast.ows.OWSRequest;
import org.vast.ows.server.OWSServlet;
import org.vast.ows.sos.SOSException;
import org.vast.ows.sps.*;
import org.vast.ows.sps.StatusReport.TaskStatus;
import org.vast.ows.swe.DescribeSensorRequest;
import org.vast.ows.util.PostRequestFilter;
import org.vast.sensorML.SMLUtils;
import org.vast.xml.DOMHelper;
import org.w3c.dom.Element;


/**
 * <p>
 * Implementation of SensorHub generic SPS service.
 * The service can manage any of the sensors installed on the SensorHub instance
 * and is configured automatically from the information generated by the sensors
 * interfaces.
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Jan 15, 2015
 */
@SuppressWarnings("serial")
public class SPSService extends OWSServlet implements IServiceModule<SPSServiceConfig>, IEventListener
{
    private static final Logger log = LoggerFactory.getLogger(SPSService.class);
    
    SPSServiceConfig config;
    SPSServiceCapabilities capabilitiesCache;
    Map<String, SPSOfferingCapabilities> procedureToOfferingMap;
    Map<String, ISPSConnector> connectors;
    IEventHandler eventHandler;
    ITaskDB taskDB;
    SPSNotificationSystem notifSystem;
    
    
    public SPSService()
    {
        this.eventHandler = new BasicEventHandler();
        this.owsUtils = new SPSUtils();
    }
    
    
    @Override
    public void init(SPSServiceConfig config) throws SensorHubException
    {
        this.config = config;
    }


    @Override
    public void updateConfig(SPSServiceConfig config) throws SensorHubException
    {
        this.config = config;
    }
    
    
    /**
     * Generates the SPSServiceCapabilities object with info obtained from connector
     * @return
     */
    protected SPSServiceCapabilities generateCapabilities()
    {
        connectors.clear();
        procedureToOfferingMap.clear();
        
        // get main capabilities info from config
        CapabilitiesInfo serviceInfo = config.ogcCapabilitiesInfo;
        SPSServiceCapabilities capabilities = new SPSServiceCapabilities();
        capabilities.getIdentification().setTitle(serviceInfo.title);
        capabilities.getIdentification().setDescription(serviceInfo.description);
        capabilities.setFees(serviceInfo.fees);
        capabilities.setAccessConstraints(serviceInfo.accessConstraints);
        capabilities.setServiceProvider(serviceInfo.serviceProvider);
        
        // generate profile list
        /*capabilities.getProfiles().add(SOSServiceCapabilities.PROFILE_RESULT_RETRIEVAL);
        if (config.enableTransactional)
        {
            capabilities.getProfiles().add(SOSServiceCapabilities.PROFILE_RESULT_INSERTION);
            capabilities.getProfiles().add(SOSServiceCapabilities.PROFILE_OBS_INSERTION);
        }*/
        
        // process each provider config
        if (config.connectors != null)
        {
            for (SPSConnectorConfig providerConf: config.connectors)
            {
                try
                {
                    // instantiate provider factories and map them to offering URIs
                    ISPSConnector connector = providerConf.getConnector();
                    if (!connector.isEnabled())
                        continue;
                                    
                    // add offering metadata to capabilities
                    SPSOfferingCapabilities offCaps = connector.generateCapabilities();
                    capabilities.getLayers().add(offCaps);
                    
                    // add connector and offering caps to maps
                    String procedureID = offCaps.getProcedures().get(0);
                    connectors.put(procedureID, connector);
                    procedureToOfferingMap.put(procedureID, offCaps);
                    
                    if (log.isDebugEnabled())
                        log.debug("Offering " + "\"" + offCaps.toString() + "\" generated for procedure " + offCaps.getProcedures().get(0));
                }
                catch (Exception e)
                {
                    log.error("Error while initializing connector " + providerConf.uri, e);
                }
            }
        }
        
        capabilitiesCache = capabilities;
        return capabilities;
    }
    
    
    @Override
    public void start()
    {
        this.connectors = new LinkedHashMap<String, ISPSConnector>();
        this.procedureToOfferingMap = new HashMap<String, SPSOfferingCapabilities>();
        this.taskDB = new InMemoryTaskDB();
        
        // pre-generate capabilities
        this.capabilitiesCache = generateCapabilities();
                
        // subscribe to server lifecycle events
        SensorHub.getInstance().registerListener(this);
        
        // deploy servlet
        deploy();
    }
    
    
    @Override
    public void stop()
    {
        // undeploy servlet
        undeploy();
        
        // unregister ourself
        SensorHub.getInstance().unregisterListener(this);
        
        // clean all connectors
        for (ISPSConnector connector: connectors.values())
            connector.cleanup();
    }
    
    
    protected void deploy()
    {
        if (!HttpServer.getInstance().isEnabled())
            return;
        
        // deploy ourself to HTTP server
        HttpServer.getInstance().deployServlet(config.endPoint, this);
    }
    
    
    protected void undeploy()
    {
        if (!HttpServer.getInstance().isEnabled())
            return;
        
        HttpServer.getInstance().undeployServlet(this);
    }
    
    
    @Override
    public void cleanup() throws SensorHubException
    {
        // TODO unregister listeners
    }
    
    
    @Override
    public void handleEvent(Event e)
    {
        // what's important here is to redeploy if HTTP server is restarted
        if (e instanceof ModuleEvent && e.getSource() == HttpServer.getInstance())
        {
            // start when HTTP server is enabled
            if (((ModuleEvent) e).type == ModuleEvent.Type.ENABLED)
                start();
            
            // stop when HTTP server is disabled
            else if (((ModuleEvent) e).type == ModuleEvent.Type.DISABLED)
                stop();
        }
    }


    @Override
    public SPSServiceConfig getConfiguration()
    {
        return config;
    }


    @Override
    public String getName()
    {
        return config.name;
    }
    
    
    @Override
    public String getLocalID()
    {
        return config.id;
    }
    
    
    @Override
    public boolean isEnabled()
    {
        return config.enabled;
    }
    

    @Override
    public void saveState(IModuleStateSaver saver) throws SensorHubException
    {
    }


    @Override
    public void loadState(IModuleStateLoader loader) throws SensorHubException
    {
    }


    @Override
    public void registerListener(IEventListener listener)
    {
        eventHandler.registerListener(listener);        
    }


    @Override
    public void unregisterListener(IEventListener listener)
    {
        eventHandler.unregisterListener(listener);
    }
    
    
    /////////////////////////////////////////////
    /// methods working with OWSServlet logic ///
    /////////////////////////////////////////////
    
    @Override
    protected OWSRequest parseRequest(HttpServletRequest req, boolean post) throws Exception
    {
        if (post)
        {
            InputStream xmlRequest = new PostRequestFilter(new BufferedInputStream(req.getInputStream()));
            DOMHelper dom = new DOMHelper(xmlRequest, false);
            Element requestElt = owsUtils.skipSoapEnvelope(dom, dom.getBaseElement());
            
            // check to see if it is a tasking request
            String procID = dom.getElementValue(requestElt, "procedure");
            
            // case of tasking request, need to get tasking params for the selected procedure
            if (procID != null)
            {
                SPSOfferingCapabilities offering = procedureToOfferingMap.get(procID);
                if (offering == null)
                    throw new SPSException(SPSException.invalid_param_code, "procedure", procID);           
                DescribeTaskingResponse paramDesc = offering.getParametersDescription();
                
                // use full tasking params or updatable subset
                DataComponent taskingParams;
                if (requestElt.getLocalName().equals("Update"))
                    taskingParams = paramDesc.getUpdatableParameters();
                else
                    taskingParams = paramDesc.getTaskingParameters();
                
                return ((SPSUtils)owsUtils).readSpsRequest(dom, requestElt, taskingParams);
            }
            
            // case of normal request
            else
                return super.parseRequest(req, post);
        }
        else
        {
            return super.parseRequest(req, post);
        }
    }
    
    
    @Override
    protected void handleRequest(OWSRequest request) throws Exception
    {
        if (request instanceof GetCapabilitiesRequest)
            handleRequest((GetCapabilitiesRequest)request);
        else if (request instanceof DescribeSensorRequest)
            handleRequest((DescribeSensorRequest)request);
        else if (request instanceof DescribeTaskingRequest)
            handleRequest((DescribeTaskingRequest)request);
        else if (request instanceof GetStatusRequest)
            handleRequest((GetStatusRequest)request);
        else if (request instanceof GetFeasibilityRequest)
            handleRequest((GetFeasibilityRequest)request);
        else if (request instanceof SubmitRequest)
            handleRequest((SubmitRequest)request);
        else if (request instanceof UpdateRequest)
            handleRequest((UpdateRequest)request);
        else if (request instanceof CancelRequest)
            handleRequest((CancelRequest)request);
        else if (request instanceof ReserveRequest)
            handleRequest((ReserveRequest)request);
        else if (request instanceof ConfirmRequest)
            handleRequest((ConfirmRequest)request);
        else if (request instanceof DescribeResultAccessRequest)
            handleRequest((DescribeResultAccessRequest)request);
    }
    
    
    protected void handleRequest(GetCapabilitiesRequest request) throws Exception
    {
        sendResponse(request, capabilitiesCache);
    }
    
    
    protected void handleRequest(DescribeSensorRequest request) throws Exception
    {
        String procedureID = request.getProcedureID();
        
        OWSExceptionReport report = new OWSExceptionReport();
        ISPSConnector connector = getConnectorByProcedureID(procedureID, report);
        checkQueryProcedureFormat(procedureID, request.getFormat(), report);
        report.process();
        
        // serialize and send SensorML description
        OutputStream os = new BufferedOutputStream(request.getResponseStream());
        new SMLUtils().writeProcess(os, connector.generateSensorMLDescription(Double.NaN), true);
    }
    
    
    protected void handleRequest(DescribeTaskingRequest request) throws Exception
    {
        String procID = request.getProcedureID();
        SPSOfferingCapabilities offering = procedureToOfferingMap.get(procID);
        
        if (offering != null)
            sendResponse(request, offering.getParametersDescription());
        else
            throw new SPSException(SPSException.invalid_param_code, "procedure", procID);
    }
    
    
    protected ITask findTask(String taskID) throws SPSException
    {
        ITask task = taskDB.getTask(taskID);
        
        if (task == null)
            throw new SPSException(SPSException.invalid_param_code, "task", taskID);
        
        return task;
    }
    
    
    protected void handleRequest(GetStatusRequest request) throws Exception
    {
        ITask task = findTask(request.getTaskID());
        StatusReport status = task.getStatusReport();
        
        GetStatusResponse gsResponse = new GetStatusResponse();
        gsResponse.setVersion("2.0.0");
        gsResponse.getReportList().add(status);
        
        sendResponse(request, gsResponse);
    }
    
    
    protected GetFeasibilityResponse handleRequest(GetFeasibilityRequest request) throws Exception
    {               
        /*GetFeasibilityResponse gfResponse = new GetFeasibilityResponse();
        
        // create task in DB
        ITask newTask = taskDB.createNewTask(request);
        String studyId = newTask.getID();
        
        // launch feasibility study
        //FeasibilityResult result = doFeasibilityStudy(request);
        String sensorId = request.getSensorID();
        
        // create response
        GetFeasibilityResponse gfResponse = new GetFeasibilityResponse();
        gfResponse.setVersion("2.0.0");
        FeasibilityReport report = gfResponse.getReport();
        report.setTitle("Automatic Feasibility Results");
        report.setTaskID(studyId);
        report.setSensorID(sensorId);
                
        if (!isFeasible(result))
        {
            report.setRequestStatus(RequestStatus.Rejected);
        }
        else
        {
            report.setRequestStatus(RequestStatus.Accepted);
            report.setPercentCompletion(1.0f);            
        }
        
        report.touch();
        taskDB.updateTaskStatus(report);
        
        return gfResponse;*/  
        throw new SPSException(SPSException.unsupported_op_code, request.getOperation());
    }
    
    
    protected void handleRequest(SubmitRequest request) throws Exception
    {
        // validate task parameters
        request.validate();
        
        // create task in DB
        ITask newTask = taskDB.createNewTask(request);
        final String taskID = newTask.getID();
        
        // send command through connector
        ISPSConnector conn = connectors.get(request.getProcedureID());
        DataBlockList dataBlockList = (DataBlockList)request.getParameters().getData();
        Iterator<DataBlock> it = dataBlockList.blockIterator();
        while (it.hasNext())
            conn.sendSubmitData(newTask, it.next());        
        
        // add report and send response
        SubmitResponse sResponse = new SubmitResponse();
        sResponse.setVersion("2.0");
        ITask task = findTask(taskID);
        task.getStatusReport().setTaskStatus(TaskStatus.Completed);
        task.getStatusReport().touch();
        sResponse.setReport(task.getStatusReport());
        
        sendResponse(request, sResponse);
    }
    

    protected void handleRequest(UpdateRequest request) throws Exception
    {
        throw new SPSException(SPSException.unsupported_op_code, request.getOperation());
    }
    
    
    protected void handleRequest(CancelRequest request) throws Exception
    {
        throw new SPSException(SPSException.unsupported_op_code, request.getOperation());
    }
    

    protected void handleRequest(ReserveRequest request) throws Exception
    {
        throw new SPSException(SPSException.unsupported_op_code, request.getOperation());
    }
    
    
    protected void handleRequest(ConfirmRequest request) throws Exception
    {
        throw new SPSException(SPSException.unsupported_op_code, request.getOperation());
    }
    
    
    protected void handleRequest(DescribeResultAccessRequest request) throws Exception
    {
        /*ITask task = findTask(request.getTaskID());
        
        DescribeResultAccessResponse resp = new DescribeResultAccessResponse();     
        StatusReport status = task.getStatusReport();
        
        // TODO DescribeResultAccess
        
        return resp;*/
        throw new SPSException(SPSException.unsupported_op_code, request.getOperation());
    }
    
    
    protected final ISPSConnector getConnectorByProcedureID(String procedureID, OWSExceptionReport report) throws Exception
    {
        ISPSConnector connector = connectors.get(procedureID);
        
        if (connector == null)
            report.add(new SPSException(SPSException.invalid_param_code, "procedure", procedureID));
        
        return connector;
    }
    
    
    protected void checkQueryProcedureFormat(String procedureID, String format, OWSExceptionReport report) throws SOSException
    {
        // ok if default format can be used
        if (format == null)
            return;
        
        SPSOfferingCapabilities offering = this.procedureToOfferingMap.get(procedureID);
        if (!offering.getProcedureFormats().contains(format))
            report.add(new SOSException(SOSException.invalid_param_code, "procedureDescriptionFormat", format, "Procedure description format " + format + " is not available for procedure " + procedureID));
    }


    @Override
    protected String getServiceType()
    {
        return SPSUtils.SPS;
    }


    @Override
    protected String getDefaultVersion()
    {
        return "2.0";
    }
}
