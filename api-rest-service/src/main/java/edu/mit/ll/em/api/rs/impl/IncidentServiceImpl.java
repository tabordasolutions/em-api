/**
 * Copyright (c) 2008-2018, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.em.api.rs.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.mit.ll.em.api.json.deserializer.ROCMessageDeserializer;
import edu.mit.ll.em.api.rs.model.ROCMessage;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.SADisplayConstants;
import edu.mit.ll.nics.common.entity.*;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;

import org.apache.commons.lang.StringUtils;
import org.apache.cxf.jaxrs.utils.ExceptionUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.dao.DataAccessException;

import edu.mit.ll.em.api.exception.DuplicateCollabRoomException;
import edu.mit.ll.em.api.rs.CollabService;
import edu.mit.ll.em.api.rs.FieldMapResponse;
import edu.mit.ll.em.api.rs.IncidentService;
import edu.mit.ll.em.api.rs.IncidentServiceResponse;
import edu.mit.ll.em.api.util.APILogger;
import edu.mit.ll.nics.nicsdao.impl.IncidentDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.OrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.WorkspaceDAOImpl;
import edu.mit.ll.nics.common.email.JsonEmail;
import edu.mit.ll.nics.common.entity.IncidentType;

/**
 * 
 * @AUTHOR sa23148
 *
 */
public class IncidentServiceImpl implements IncidentService {
	
	/** CNAME - the name of this class for referencing in loggers */
	private static final String CNAME = IncidentServiceImpl.class.getName();
	
	private static final String WORKING_MAP = "Working Map";
	
	public static final String DUPLICATE_NAME = "Incident name already exists.";
	
	/** The Incident DAO */
	private static final IncidentDAOImpl incidentDao = new IncidentDAOImpl();
	
	/** The Org DAO */
	private static final OrgDAOImpl orgDao = new OrgDAOImpl();
	
	/** The User DAO */
	private static final UserDAOImpl userDao = new UserDAOImpl();
	
	/** The User DAO */
	private static final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();
	
	/** The User DAO */
	private static final WorkspaceDAOImpl workspaceDao = new WorkspaceDAOImpl();
	
	private RabbitPubSubProducer rabbitProducer;
	

	/**
	 * Read and return all Incident items.
	 * 
	 * @param workspaceId
	 * @param accessibleByUserId
	 * 
	 * @return Response IncidentResponse containing all Incidents with the specified workspace
	 * @see IncidentServiceResponse
	 */
	public Response getIncidents(Integer workspaceId, Integer accessibleByUserId) {
		Response response = null;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		List<edu.mit.ll.nics.common.entity.Incident> incidents = null;
		try {
			incidents = incidentDao.getIncidents(workspaceId); //.getIncidentsAccessibleByUser(workspaceId, accessibleByUserId);
			
			incidentResponse.setIncidents(incidents);
			incidentResponse.setCount(incidents.size());
			incidentResponse.setMessage("ok");
			response = Response.ok(incidentResponse).status(Status.OK).build();			
		} catch (DataAccessException e) { //(ICSDatastoreException e) {
			APILogger.getInstance().e(CNAME, "Data access exception while getting Incidents"
					+ "in (workspaceid,accessibleByUserId): " + workspaceId + ", " 
					+ accessibleByUserId + ": " + e.getMessage());
			incidentResponse.setMessage("Data access failure. Unable to read all incidents: " + e.getMessage());
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();			
		} catch (Exception e) {
			APILogger.getInstance().e(CNAME, "Unhandled exception while getting Incidents"
					+ "in (workspaceid,accessibleByUserId): " + workspaceId + ", " 
					+ accessibleByUserId + ": " + e.getMessage());
			
			incidentResponse.setMessage("Unhandled exception. Unable to read all incidents: " + e.getMessage());			
			response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return response;
	}
	
	public Response findArchivedIncidents(Integer workspaceId, String orgPrefix, String name){
		FieldMapResponse mapResponse = new FieldMapResponse();
		if(orgPrefix != null){
			mapResponse.setData(incidentDao.findArchivedIncidentsByPrefix(workspaceId, orgPrefix));
		}else{
			mapResponse.setData(incidentDao.findArchivedIncidentsByName(workspaceId, name));
		}
		
		return Response.ok(mapResponse).status(Status.OK).build();
	}
	
	public Response activateIncident(int workspaceId, int incidentId, String username){
		if(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) ||
				incidentDao.isAdmin(workspaceId, incidentId, username)){
		
			boolean ret = incidentDao.setIncidentActive(incidentId, true);
			if(ret){
				String topic = String.format("iweb.NICS.ws.%s.newIncident", workspaceId);
				try{
					this.notifyIncident(incidentDao.getIncident(incidentId), topic);
					return Response.ok(Status.OK.toString()).status(Status.OK).build();
				}catch(Exception e){
					return Response.ok("The incident was activated successfully, but there "
							+ "was an error notifying the users.").status(Status.INTERNAL_SERVER_ERROR).build();
				}
			}
			return Response.ok("There was an error activating the incident").status(Status.INTERNAL_SERVER_ERROR).build();
		}else{
			return Response.ok(Status.FORBIDDEN).status(Status.FORBIDDEN).build();
		}
	}
	
	public Response archiveIncident(int workspaceId, int incidentId, String username){
		if(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) ||
				incidentDao.isAdmin(workspaceId, incidentId, username)){
			boolean ret = incidentDao.setIncidentActive(incidentId, false);
			if(ret){
				String topic = String.format("iweb.NICS.ws.%s.removeIncident", workspaceId);
				try{
					this.notifyIncident(incidentId, topic);
					return Response.ok(Status.OK.toString()).status(Status.OK).build();
				}catch(Exception e){
					return Response.ok("The incident was activated successfully, but there "
							+ "was an error notifying the users.").status(Status.INTERNAL_SERVER_ERROR).build();
				}
			}
			return Response.ok("There was an error activating the incident").status(Status.INTERNAL_SERVER_ERROR).build();
		}else{
			return Response.ok(Status.FORBIDDEN).status(Status.FORBIDDEN).build();
		}

	}
	
	public Response getActiveIncidents(Integer workspaceId, Integer orgId){
		return this.getIncidents(workspaceId, orgId, true);
	}	
	
	public Response getArchivedIncidents(Integer workspaceId, Integer orgId){
		return this.getIncidents(workspaceId, orgId, false);
	}
	
	private Response getIncidents(Integer workspaceId, Integer orgId, boolean active){
		FieldMapResponse response = new FieldMapResponse();
		response.setData(incidentDao.getActiveIncidents(workspaceId, orgId, active));
		return Response.ok(response).status(Status.OK).build();
	}
	
	/**
	 * Read and return all Incident items.
	 * 
	 * @param workspaceId
	 *
	 * @return Response IncidentResponse containing all Incidents with the specified workspace
	 * @see IncidentServiceResponse
	 
	public Response getIncidentsTree(Integer workspaceId, Integer accessibleByUserId) {

	 * @return Response IncidentResponse containing all Incidents with the specified workspace and there children
	 * @see IncidentServiceResponse
	 */
	
	public Response getIncidentsTree(Integer workspaceId) {
		Response response = null;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		List<edu.mit.ll.nics.common.entity.Incident> incidents = null;
		try {
			incidents = incidentDao.getIncidentsTree(workspaceId);

			
			incidentResponse.setIncidents(incidents);
			incidentResponse.setCount(incidents.size());
			incidentResponse.setMessage("ok");
			response = Response.ok(incidentResponse).status(Status.OK).build();			
		} catch (DataAccessException e) { //(ICSDatastoreException e) {
			APILogger.getInstance().e(CNAME, "Data access exception while getting Incidents Tree"
					+ "in (workspaceid): " + workspaceId + ": " + e.getMessage());
			incidentResponse.setMessage("Data access failure. Unable to read all incidents in tree: " + e.getMessage());
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();			
		} catch (Exception e) {
			APILogger.getInstance().e(CNAME, "Unhandled exception while getting Incidents Tree"
					+ "in (workspaceid): " + workspaceId + ": " + e.getMessage());
			
			incidentResponse.setMessage("Unhandled exception. Unable to read all incidents in tree: " + e.getMessage());			
			response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return response;
	}

	
	/**
	 * Updates the incident that was sent
	 * 
	 * @param workspaceId
	 * @param incident to update
	 * 
	 * @return Response IncidentResponse containing the updated incident
	 * @see IncidentServiceResponse
	 */
	public Response updateIncident(Integer workspaceId, Incident incident) {
		Response response = null;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		Incident updatedIncident = null;

		Incident incidentByName = incidentDao.getIncidentByName(incident.getIncidentname(), workspaceId);
		if(incidentByName != null &&
                incidentByName.getIncidentid().intValue() != incident.getIncidentid()){
			incidentResponse.setMessage(DUPLICATE_NAME);
			return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}

		try {
			updatedIncident = incidentDao.updateIncident(workspaceId,incident); 
			
			if (updatedIncident != null) {
				incidentResponse.setCount(1);
				incidentResponse.setMessage(Status.OK.getReasonPhrase());
				response = Response.ok(incidentResponse).status(Status.OK).build();
			}
			else{
				incidentResponse.setMessage("Error updating incident");
				incidentResponse.setCount(0);
				response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
		} catch (Exception e) {
			incidentResponse.setMessage("updateIncident failed: " + e.getMessage());
			APILogger.getInstance().e(CNAME, "Data access exception while updating Incident"
					+ incident.getIncidentname() +  ": " + e.getMessage());
			incidentResponse.setMessage("Data access failure. Unable to update incident: " + e.getMessage());
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();			
		}
		
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				String topic = String.format("iweb.NICS.ws.%s.updateIncident", workspaceId);
				notifyIncident(updatedIncident, topic);
			} catch (Exception e) {
				APILogger.getInstance().e(CNAME,"Failed to publish a update Incident message event");
			}
		}
		
		return response;
	}

	
	/**
	 * Delete all Incident items.
	 * 
	 * <p>Currently this is an unsupported operation.</p>
	 * 
	 * @param workspaceId ID for Workspace to delete incidents from
	 *  
	 * @see Response
	 * @see IncidentServiceResponse
	 */
	public Response deleteIncidents(Integer workspaceId) {
		return makeUnsupportedOpRequestResponse();
	}
	

	/**
	 * Bulk creation of Incident items.
	 * 
	 * <p>
	 * 	TODO: not used by mobile, so not converting to SpringJDBC. During refactor,
	 *  UI framework may choose to implement it at that time
	 * <p>
	 *  
	 * @param workspaceId
	 * @param incidents A collection of Incident items to be created.
	 *  
	 * @return Response IncidentServiceResponse 
	 *  
	 * @see Response
	 * @see IncidentServiceResponse
	 */
	@Deprecated
	public Response putIncidents(Integer workspaceId, Collection<Incident> incidents) {
		return makeUnsupportedOpRequestResponse();
		/*
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		Response response = null;
		int errorCount = 0;
		for (Incident incident : incidents) {
			try {
				incident.setWorkspaceId(workspaceId);  // Make sure this is set consistently.
				Incident newIncident = IncidentDAO.getInstance().createIncident(incident);
				incidentResponse.getIncidents().add(newIncident);
			} catch (ICSDatastoreException e) {
				PAPILogger.getInstance().e(CNAME, e.getMessage());
				++errorCount;
			}			
		}
		
		if (errorCount == 0) {
			incidentResponse.setMessage("ok");
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.OK).build();
		} else {
			incidentResponse.setMessage("Failures. " + errorCount + " out of " + incidents.size() + " were not created.");
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}

		return response;*/
	}

	
	/**
	 *  Creates single Incident
	 *  
	 *  <p>
	 *  	TODO: not used by mobile, AND needs fully implemented for use with the ui-framework
	 *  		incident module. This call simply persists an Incident entity w/o all the
	 *  		other setup the SA/message does
	 *  </p>
	 *  
	 *  @param workspaceId
	 *  @param incident Incident to be persisted
	 *  
	 *  @return Response
	 * @throws Exception 
	 * @throws DuplicateCollabRoomException 
	 * @throws DataAccessException 
	 *  
	 *  @see IncidentServiceResponse
	 *  
	 */

	/*
	public Response postIncident(Integer workspaceId, Integer orgId, Integer userId, Incident incident, Form form)
			throws DataAccessException, DuplicateCollabRoomException, Exception {

		APILogger.getInstance().e(CNAME,"postIncident for ROC Incident Creation called.");

		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		Response response = null;

		if(incidentDao.getIncidentByName(incident.getIncidentname(), workspaceId) != null){
			APILogger.getInstance().e(CNAME,"Duplicate ROC Incident Name.");

			incidentResponse.setMessage(DUPLICATE_NAME);
			return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}

		Incident newIncident = null;
		try {
			incident.setCreated(new Date());

			APILogger.getInstance().e(CNAME,"NON-ROC calling incidentDao.create(incident.");

			newIncident = incidentDao.create(incident);

			if(newIncident != null){

				APILogger.getInstance().e(CNAME,"ROC newIncident is not null.");

				incidentResponse.getIncidents().add(newIncident);
				incidentResponse.setMessage(Status.OK.getReasonPhrase());
				incidentResponse.setCount(incidentResponse.getIncidents().size());
				response = Response.ok(incidentResponse).status(Status.OK).build();
			}else{

				APILogger.getInstance().e(CNAME,"ROC newIncident is null. Something probably went wrong in incidentDao.");

				incidentResponse.setMessage(Status.EXPECTATION_FAILED.getReasonPhrase());
				incidentResponse.setCount(0);
				return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
		} catch (Exception e) {

			APILogger.getInstance().e(CNAME,"ROC exception in postIncident before Creating default Collaboration Rooms.");

			// incidentResponse.setMessage("postIncident failed: " + e.getMessage());
			incidentResponse.setMessage("ROC exception postIncident failed before Creating default Collaboration Rooms: " + e.getMessage());

			return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}


		APILogger.getInstance().e(CNAME,"Calling ROC createDefaultCollabRoom().");

		//Create default rooms
		CollabRoom incidentMap = createDefaultCollabRoom(newIncident.getUsersessionid(), 
				APIConfig.getInstance().getConfiguration().getString(APIConfig.INCIDENT_MAP, 
						SADisplayConstants.INCIDENT_MAP));

		APILogger.getInstance().e(CNAME,"Calling ROC orgDao.getOrgAdmins(orgId,workspaceId).");

		List<Integer> admins = orgDao.getOrgAdmins(orgId,workspaceId);

		if(!admins.contains(userId)){

			APILogger.getInstance().e(CNAME,"ROC - No userId in admins list found");

			APILogger.getInstance().e(CNAME,"ROC - Calling incidentMap.getAdminUsers().add(userId).");

			incidentMap.getAdminUsers().add(userId);

		}

		APILogger.getInstance().e(CNAME,"ROC - Calling incidentMap.getAdminUsers().addAll(admins).");

		incidentMap.getAdminUsers().addAll(admins);

		APILogger.getInstance().e(CNAME,"ROC - Creating collabRoomEndpoint. ");

		CollabService collabRoomEndpoint = new CollabServiceImpl();

		APILogger.getInstance().e(CNAME,"ROC - calling createCollabRoomWithPermissions(). ");

		collabRoomEndpoint.createCollabRoomWithPermissions(newIncident.getIncidentid(),orgId, workspaceId,
				incidentMap);

		APILogger.getInstance().e(CNAME,"ROC - calling createUnsecureCollabRoom(). ");

		collabRoomEndpoint.createUnsecureCollabRoom(newIncident.getIncidentid(), createDefaultCollabRoom(
				newIncident.getUsersessionid(), WORKING_MAP));

		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				String topic = String.format("iweb.NICS.ws.%s.newIncident", workspaceId);

				APILogger.getInstance().e(CNAME,"ROC - Topic string created before calling notifyIncident(newIncident, topic) ");

				APILogger.getInstance().e(CNAME,"ROC - calling notifyIncident(newIncident, topic) ");

				notifyIncident(newIncident, topic);

				try {

					APILogger.getInstance().e(CNAME,"ROC - creating newIncidentUsers list. ");

					String newIncidentUsers = APIConfig.getInstance().getConfiguration().getString(APIConfig.NEW_INCIDENT_USERS_EMAIL);

					APILogger.getInstance().e(CNAME,"ROC - calling userDao.getUserBySessionId(newIncident.getUsersessionid()). ");

					User creator = userDao.getUserBySessionId(newIncident.getUsersessionid());

					APILogger.getInstance().e(CNAME,"ROC - calling orgDao.getLoggedInOrg(creator.getUserId()). ");

					Org org = orgDao.getLoggedInOrg(creator.getUserId());

					APILogger.getInstance().e(CNAME,"ROC - calling orgDao.getOrgAdmins(org.getOrgId(). ");

					List<String>  disList = orgDao.getOrgAdmins(org.getOrgId());

					APILogger.getInstance().e(CNAME,"ROC - calling form.getFormtypeid(). ");

					Integer formTypeId = form.getFormtypeid();

				} catch (Exception e) {
					APILogger.getInstance().e(CNAME,"Failed to send new Incident email alerts");
					APILogger.getInstance().e(CNAME, e.getMessage());
					// incidentResponse.setMessage("postIncident failed: " + e.getMessage());
					incidentResponse.setMessage("ROC - Failed to send new Incident email alerts: " + e.getMessage());
					return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
				}
			} catch (Exception e) {
				APILogger.getInstance().e(CNAME,"Failed to publish a new Incident message event");
				// incidentResponse.setMessage("postIncident failed: " + e.getMessage());
				incidentResponse.setMessage("ROC - Failed to publish a new Incident message event: " + e.getMessage());
				return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
		
		return response;
	}

	 */


	/**
	 *  Creates single Incident
	 *
	 *  <p>
	 *  	TODO: not used by mobile, AND needs fully implemented for use with the ui-framework
	 *  		incident module. This call simply persists an Incident entity w/o all the
	 *  		other setup the SA/message does
	 *  </p>
	 *
	 *  @param workspaceId
	 *  @param incident Incident to be persisted
	 *
	 *  @return Response
	 * @throws Exception
	 * @throws DuplicateCollabRoomException
	 * @throws DataAccessException
	 *
	 *  @see IncidentServiceResponse
	 *
	 */
	public Response postIncident(Integer workspaceId, Integer orgId, Integer userId, Incident incident)
			throws DataAccessException, DuplicateCollabRoomException, Exception {

		APILogger.getInstance().e(CNAME,"postIncident for NON-ROC Incident Creation called.");

		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		Response response = null;
		JsonEmail email = null;

		if(incidentDao.getIncidentByName(incident.getIncidentname(), workspaceId) != null){
			APILogger.getInstance().e(CNAME,"Duplicate NON-ROC Incident Name.");
			incidentResponse.setMessage(DUPLICATE_NAME);
			return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}

		Incident newIncident = null;
		try {
			incident.setCreated(new Date());

			APILogger.getInstance().e(CNAME,"NON-ROC calling incidentDao.create(incident.");

			newIncident = incidentDao.create(incident);

			if(newIncident != null){

				APILogger.getInstance().e(CNAME,"NON-ROC newIncident is not null.");

				incidentResponse.getIncidents().add(newIncident);
				incidentResponse.setMessage(Status.OK.getReasonPhrase());
				incidentResponse.setCount(incidentResponse.getIncidents().size());
				response = Response.ok(incidentResponse).status(Status.OK).build();
			}else{

				APILogger.getInstance().e(CNAME,"NON-ROC newIncident is null. Something probably went wrong in incidentDao.");

				incidentResponse.setMessage(Status.EXPECTATION_FAILED.getReasonPhrase());
				incidentResponse.setCount(0);
				return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
		} catch (Exception e) {

			APILogger.getInstance().e(CNAME,"NON-ROC exception in postIncident before Creating default Collaboration Rooms.");

			incidentResponse.setMessage("NON-ROC exception postIncident failed before Creating default Collaboration Rooms: " + e.getMessage());
			return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}

		APILogger.getInstance().e(CNAME,"NON-ROC calling createDefaultCollabRoom().");

		//Create default rooms
		CollabRoom incidentMap = createDefaultCollabRoom(newIncident.getUsersessionid(),
				APIConfig.getInstance().getConfiguration().getString(APIConfig.INCIDENT_MAP,
						SADisplayConstants.INCIDENT_MAP));

		APILogger.getInstance().e(CNAME,"NON-ROC calling orgDao.getOrgAdmins(orgId,workspaceId).");

		List<Integer> admins = orgDao.getOrgAdmins(orgId,workspaceId);


		if(!admins.contains(userId)){

			APILogger.getInstance().e(CNAME,"NON-ROC - No userId in admins list found");

			APILogger.getInstance().e(CNAME,"NON-ROC - calling incidentMap.getAdminUsers().add(userId).");

			incidentMap.getAdminUsers().add(userId);
		}

		APILogger.getInstance().e(CNAME,"NON-ROC - calling incidentMap.getAdminUsers().addAll(admins).");

		incidentMap.getAdminUsers().addAll(admins);

		APILogger.getInstance().e(CNAME,"NON-ROC - creating collabRoomEndpoint. ");

		CollabService collabRoomEndpoint = new CollabServiceImpl();

		APILogger.getInstance().e(CNAME,"NON-ROC - calling createCollabRoomWithPermissions(). ");

		collabRoomEndpoint.createCollabRoomWithPermissions(newIncident.getIncidentid(),orgId, workspaceId,
				incidentMap);

		APILogger.getInstance().e(CNAME,"NON-ROC - calling createUnsecureCollabRoom(). ");

		collabRoomEndpoint.createUnsecureCollabRoom(newIncident.getIncidentid(), createDefaultCollabRoom(
				newIncident.getUsersessionid(), WORKING_MAP));

		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {

				APILogger.getInstance().e(CNAME,"NON-ROC - Creating topic string before calling notifyIncident(newIncident, topic) ");

				String topic = String.format("iweb.NICS.ws.%s.newIncident", workspaceId);

				APILogger.getInstance().e(CNAME,"NON-ROC - calling notifyIncident(newIncident, topic)");

				notifyIncident(newIncident, topic);

				try {

					String date = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy").format(new Date());
					String alertTopic = String.format("iweb.nics.email.alert");

					APILogger.getInstance().e(CNAME,"NON-ROC - creating list of newIncidentUsers. ");

					String newIncidentUsers = APIConfig.getInstance().getConfiguration().getString(APIConfig.NEW_INCIDENT_USERS_EMAIL);

					APILogger.getInstance().e(CNAME,"NON-ROC - creating hostname. ");

					String hostname = InetAddress.getLocalHost().getHostName();

					APILogger.getInstance().e(CNAME,"NON-ROC - calling userDao.getUserBySessionId(newIncident.getUsersessionid()). ");

					User creator = userDao.getUserBySessionId(newIncident.getUsersessionid());

					APILogger.getInstance().e(CNAME,"NON-ROC - calling orgDao.getLoggedInOrg(creator.getUserId()). ");

					Org org = orgDao.getLoggedInOrg(creator.getUserId());

					APILogger.getInstance().e(CNAME,"NON-ROC - calling orgDao.getOrgAdmins(org.getOrgId(). ");

					List<String>  disList = orgDao.getOrgAdmins(org.getOrgId());


					APILogger.getInstance().e(CNAME,"NON-ROC - toEmails list formed. ");

					/* Create and send email for non-ROC Incidents */
					String toEmails = disList.toString().substring(1, disList.toString().length()-1) + ", " + newIncidentUsers;

					APILogger.getInstance().e(CNAME,"NON-ROC - workspaceDao.getWorkspaceName(workspaceId) called. ");

					String siteName = workspaceDao.getWorkspaceName(workspaceId);

					if(disList.size() > 0){

						APILogger.getInstance().e(CNAME,"NON-ROC - calling new JsonEmail(). ");

						email = new JsonEmail(creator.getUsername(),toEmails,"Alert from NewIncident@" + hostname);

						APILogger.getInstance().e(CNAME,"NON-ROC - calling email.setBody(). Ready to notify New Incident Email.");


						email.setBody(date + "\n\n" + "A new incident has been created: " + newIncident.getIncidentname() + "\n" +
								"Creator: " + creator.getUsername() + "\n" +
								"Location: " + newIncident.getLat() + "," + newIncident.getLon() + "\n" +
								"Site: " + siteName);

						APILogger.getInstance().e(CNAME,"NON-ROC - Calling notifyNewIncidentEmail(email.toJsonObject().toString(),alertTopic).");

						notifyNewIncidentEmail(email.toJsonObject().toString(),alertTopic);

					}
				} catch (Exception e) {
					APILogger.getInstance().e(CNAME,"Failed to send new Incident email alerts");
					APILogger.getInstance().e(CNAME, e.getMessage());
					// incidentResponse.setMessage("postIncident failed: " + e.getMessage());
					incidentResponse.setMessage("NON-ROC - Failed to send new Incident email alerts: " + e.getMessage());
					return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
				}
			} catch (Exception e) {
				APILogger.getInstance().e(CNAME,"Failed to publish a new Incident message event");
				// incidentResponse.setMessage("postIncident failed: " + e.getMessage());
				incidentResponse.setMessage("NON-ROC - Failed to publish a new Incident message event: " + e.getMessage());
				return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}

		return response;
	}

	private CollabRoom createDefaultCollabRoom(int userSessionId, String name){
		CollabRoom collabRoom = new CollabRoom();
		collabRoom.setName(name);
		collabRoom.setUsersessionid(userSessionId);
		return collabRoom;
	}
	
	private void notifyIncident(Incident newIncident, String topic) throws IOException {
		if (newIncident != null) {
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(newIncident);
			getRabbitProducer().produce(topic, message);
		}
	}
	
	private void notifyIncident(int incidentId, String topic) throws IOException {
		getRabbitProducer().produce(topic, (new Integer(incidentId).toString()));
	}
	
	private void notifyNewIncidentEmail(String email, String topic) throws IOException {
		if (email != null) {
			getRabbitProducer().produce(topic, email);
		}
	}

	/**
	 *  Read a single Incident item.
	 *  
	 *  <p>
	 *  	TODO: remove workspaceid parameter IncidentID is incidentId, regardless of Workspace, why is it required?
	 *  </p>
	 *  
	 *  @param workspaceId Workspace to filter Incident by?
	 *  @param incidentId Incident ID to be read
	 *  
	 *  @return Response IncidentResponse containing the requested Incident
	 *  @see IncidentServiceResponse
	 */
	public Response getIncident(@Deprecated Integer workspaceId, int incidentId) {
		Response response = null;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();

		// TODO: the forever incident id is a hack for an early coast guard demo, needs to be factored out
		/*
		if (incidentId < 0) {
			// The client is referring to the Default-Forever incident.
			int foreverIncidentId = PAPIConfig.getInstance().getConfiguration()
				.getInt("phinics.papi.service.incident.foreverid", -1);
			if (foreverIncidentId < 0) {
				PAPILogger.getInstance().e(CNAME, "No Forever Incident ID has been configured. "
						+ "Add key phinics.papi.service.incident.foreverid to file papi-svc.properties.");
				incidentResponse.setMessage("No Forever Incident ID has been configured. Invalid ID: "
						+ incidentId) ;
				incidentResponse.setCount(incidentResponse.getIncidents().size());
				response = Response.ok(incidentResponse).status(Status.BAD_REQUEST).build();
				return response;
			} else {
				PAPILogger.getInstance().i(CNAME, "Resolving Forever Incident ID to "
						+ foreverIncidentId);
				incidentId = foreverIncidentId;
			}
		}*/

		String message = "";
		Incident incident = null;
		try {
			//incident = IncidentDAO.getInstance().getIncidentById(workspaceId, incidentId);
			incident = incidentDao.getIncident(incidentId);
		} catch (DataAccessException e) {
			APILogger.getInstance().e(CNAME, e.getMessage());
			message = "Error. Data access exception retrieving Incident with ID " + 
					incidentId + ": " + e.getMessage();
		} catch (Exception e) {
			APILogger.getInstance().e(CNAME, e.getMessage());
			message = "Error. Unhandled exception retrieving Incident with ID " + 
					incidentId + ": " + e.getMessage();
		}
		
		if (incident == null) {
			incidentResponse.setMessage(message);
			// TODO: double check mobile wasn't specifically handling 404, the Status was previously set to Not Found
			response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			return response;
		}
		
		incidentResponse.getIncidents().add(incident);
		incidentResponse.setCount(1);
		incidentResponse.setMessage("ok");
		response = Response.ok(incidentResponse).status(Status.OK).build();

		return response;
	}

	public Response getIncident(@Deprecated Integer workspaceId, String incidentName) {
		Response response = null;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();

		String message = "";
		Incident incident = null;
		try {
			//incident = IncidentDAO.getInstance().getIncidentById(workspaceId, incidentId);
			incident = incidentDao.getIncidentByName(incidentName,workspaceId);
		} catch (DataAccessException e) {
			APILogger.getInstance().e(CNAME, e.getMessage());
			message = "Error. Data access exception retrieving Incident with name " + 
					incidentName + ": " + e.getMessage();
		} catch (Exception e) {
			APILogger.getInstance().e(CNAME, e.getMessage());
			message = "Error. Unhandled exception retrieving Incident with name " + 
					incidentName + ": " + e.getMessage();
		}
		
		if (incident == null) {
			incidentResponse.setMessage(message);
			// TODO: double check mobile wasn't specifically handling 404, the Status was previously set to Not Found
			response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			return response;
		}
		
		incidentResponse.getIncidents().add(incident);
		incidentResponse.setCount(1);
		incidentResponse.setMessage("ok");
		response = Response.ok(incidentResponse).status(Status.OK).build();

		return response;
	}
	
	public Response getIncidentOrgs(Integer workspaceId){
		FieldMapResponse dataResponse = new FieldMapResponse();
        dataResponse.setData(incidentDao.getIncidentOrg(workspaceId));
        
        dataResponse.setMessage(Status.OK.getReasonPhrase());
		return Response.ok(dataResponse).status(Status.OK).build();
	}
	
	
	/**
	 *  Delete a single Incident
	 *  
	 *  @param workspaceId 
	 *  @param incidentId ID of Incident item to be read.
	 *  
	 *  @return Response
	 *  @see IncidentServiceResponse
	 */	
	public Response deleteIncident(@Deprecated Integer workspaceId, int incidentId) {
		return makeUnsupportedOpRequestResponse();
		/* We don't support simply deleting an incident entity, there's an archive process
		 * 
		Response response = null;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();

		if (incidentId < 1) {
			incidentResponse.setMessage("Invalid incidentId value: " + incidentId) ;
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.BAD_REQUEST).build();
			return response;
		}

		try {
			IncidentDAO.getInstance().removeIncident(incidentId);
			incidentResponse.setMessage("ok");
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.OK).build();			
		} catch (ICSDatastoreException e) {
			incidentResponse.setMessage(e.getMessage()) ;
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.NOT_FOUND).build();			
		}

		return response;*/
	}
	

	/**
	 *  Update a single Incident
	 *  
	 *  @param workspaceId
	 *  @param incidentId ID of Incident item to be read
	 *  @param incident The incident entity to be used to update the existing one
	 *  
	 *  @return Response
	 *  @see IncidentServiceResponse
	 */	
	public Response putIncident(Integer workspaceId, int incidentId, Incident incident) {
		// TODO: mobile never used this, implement when web needs it
		return makeUnsupportedOpRequestResponse();
		/*
		Response response = null;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();

		if (incidentId < 0) {
			incidentResponse.setMessage("Invalid incidentId value: " + incidentId) ;
			response = Response.ok(incidentResponse).status(Status.BAD_REQUEST).build();
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			return response;
		}

		if (incident == null) {
			incidentResponse.setMessage("Invalid null Incident object.") ;
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.BAD_REQUEST).build();
			return response;
		}		

		try {			
			IncidentDAO.getInstance().updateIncident(incidentId, incident);			
			Incident u = IncidentDAO.getInstance().getIncidentById(workspaceId, incidentId);
			incidentResponse.getIncidents().add(u);
			incidentResponse.setMessage("ok");
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.OK).build();			
		} catch (Exception e) {
			incidentResponse.setMessage(e.getMessage()) ;
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.NOT_FOUND).build();	
		}

		return response;*/
	}
	

	/**
	 *  Return the number of Incidents in the specified workspace 
	 *  
	 *  @param workspaceId ID of workspace to get incident count from
	 *    
	 *  @return Response An IncidentResponse with the count field set to the count of incidents
	 *  @see IncidentServiceResponse
	 */		
	public Response getIncidentCount(Integer workspaceId) {
		Response response;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		incidentResponse.setMessage("ok");
		int count = incidentDao.getIncidentCount(workspaceId);		
		incidentResponse.setCount(count);
		if(count == -1) {
			incidentResponse.setMessage("error");			
			response = Response.ok(incidentResponse)
					.status(Status.INTERNAL_SERVER_ERROR).build();
			return response;
		}
		
		response = Response.ok(incidentResponse).status(Status.OK).build();		
		return response;
	}


	/**
	 *  Search the Incident items stored. 
	 *  
	 *  @return Response
	 *  @see IncidentServiceResponse
	 */		
	public Response searchIncidentResources() {
		return makeUnsupportedOpRequestResponse();
	}

	/**
	 * Retrieves notification form associated with specified incident
	 * 
	 * @param workspaceId Not used, remove
	 * @param incidentId ID of incident to get form for
	 */
	@Deprecated	
	public Response getIncidentNotificationForm(@Deprecated Integer workspaceId, int incidentId) {
		// TODO: Not used, and no real "notification" form anymore. THere's also a FormService, but
		//  this call can probably be removed
		return makeUnsupportedOpRequestResponse();
		/*
		Response response = null;
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();

		if (incidentId < 1) {
			incidentResponse.setMessage("Invalid incidentId value: " + incidentId) ;
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			response = Response.ok(incidentResponse).status(Status.BAD_REQUEST).build();
			return response;
		}

		Incident u = null;
		try {
			u = IncidentDAO.getInstance().getIncidentById(workspaceId, incidentId);
		} catch (ICSDatastoreException e) {
			PAPILogger.getInstance().e(CNAME, e.getMessage());
			e.printStackTrace();
		}
		if (u == null) {
			incidentResponse.setMessage("No incident found for incidentId value: " + incidentId) ;
			response = Response.ok(incidentResponse).status(Status.NOT_FOUND).build();
			incidentResponse.setCount(incidentResponse.getIncidents().size());
			return response;			
		}
		
		Incident ret = new Incident();
		//ret.setNotificationForm(u.getNotificationForm());

		incidentResponse.getIncidents().add(ret);
		incidentResponse.setMessage("ok");
		incidentResponse.setCount(incidentResponse.getIncidents().size());
		response = Response.ok(incidentResponse).status(Status.OK).build();

		return response;*/
	}

	private Response makeIllegalOpRequestResponse() {
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		incidentResponse.setMessage("Request ignored.") ;
		Response response = Response.notModified("Illegal operation requested").
				status(Status.FORBIDDEN).build();
		return response;
	}	
	
	private Response makeUnsupportedOpRequestResponse() {
		IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
		incidentResponse.setMessage("Request ignored.") ;
		Response response = Response.notModified("Unsupported operation requested").
				status(Status.NOT_IMPLEMENTED).build();
		return response;
	}
	
	/**
	 * Get Rabbit producer to send message
	 * @return
	 * @throws IOException
	 */
	private RabbitPubSubProducer getRabbitProducer() throws IOException {
		if (rabbitProducer == null) {
			rabbitProducer = RabbitFactory.makeRabbitPubSubProducer(
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_HOSTNAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_EXCHANGENAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERNAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERPWD_KEY));
		}
		return rabbitProducer;
	}
}

