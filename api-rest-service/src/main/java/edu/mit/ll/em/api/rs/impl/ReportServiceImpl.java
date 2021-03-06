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

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.mit.ll.em.api.json.deserializer.ROCMessageDeserializer;
import edu.mit.ll.em.api.rs.*;
import edu.mit.ll.em.api.rs.model.ROCMessage;
import edu.mit.ll.em.api.rs.validator.ReportValidator;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.email.JsonEmail;
import edu.mit.ll.nics.common.entity.*;
import edu.mit.ll.nics.common.entity.Incident;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.UxoreportDAO;
import edu.mit.ll.nics.nicsdao.impl.*;

import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.utils.ExceptionUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONException;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;

import edu.mit.ll.em.api.dataaccess.EntityCacheMgr;
import edu.mit.ll.em.api.dataaccess.ICSDatastoreException;
import edu.mit.ll.em.api.exception.BadContentException;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.APILogger;

/**
 *
 * @AUTHOR sa23148
 *
 */
public class ReportServiceImpl implements ReportService {

    private static final String CNAME = ReportServiceImpl.class.getName();
    private IncidentDAOImpl incidentDao = null;
    private UserDAOImpl userDao = null;
    private FormDAOImpl formDao = null;
    private UserSessionDAOImpl userSessionDao = null;
    private UxoreportDAO uxoReportDao = null;
    private IncidentService incidentService = null;
    private RabbitPubSubProducer rabbitProducer = null;
    private ReportValidator reportValidator = null;

    public ReportServiceImpl(IncidentDAOImpl incidentDao, UserDAOImpl userDao, FormDAOImpl formDao, UserSessionDAOImpl userSessionDao,
                             UxoreportDAO uxoReportDao, IncidentService incidentService,
                             RabbitPubSubProducer rabbitProducer, ReportValidator reportValidator) {
        this.incidentDao = incidentDao;
        this.userDao = userDao;
        this.formDao = formDao;
        this.userSessionDao = userSessionDao;
        this.uxoReportDao = uxoReportDao;
        this.incidentService = incidentService;
        this.rabbitProducer = rabbitProducer;
        this.reportValidator = reportValidator;
    }
    /**
     * Read and return all Report items.
     * @return Response
     */
    public Response getReports(int incidentId, String reportType, ReportOptParms optParms) {
        // Make sure we support this type of form.
        Response response = validateReportType(reportType, "GET");
        if (response != null) {
            return response;
        }

        // Provide some reasonable defaults where needed.
        if (optParms.getDateColumn() == null) {
            optParms.setDateColumn("seqtime");
        }
        if (optParms.getSortByColumn() == null) {
            optParms.setSortByColumn("seqtime");
        }
        if (optParms.getSortOrder() == null) {
            optParms.setSortOrder("DESC");
        }
        int maxRowsLimit = APIConfig.getInstance().getConfiguration().getInt(APIConfig.DB_MAX_ROWS, 1000);
        if (optParms.getLimit() == null || optParms.getLimit() > maxRowsLimit) {
            APILogger.getInstance().i(CNAME, "Rewriting max. rows LIMIT as " + maxRowsLimit);
            optParms.setLimit(maxRowsLimit);
        }

        // Collect optional parameters common to all resources.
        Map<String, Object> queryConstraints = QueryConstraintHelper.parseOptions(optParms);
        // The "incidentId" is a parameter specific to this resource so QueryConstraintHelper
        // does not parse it.
        //if (optParms.getIncidentId() != null) {
        //queryConstraints.put("incidentid", optParms.getIncidentId());
        queryConstraints.put("incidentid", incidentId);
        //}

        //Set<Report> reports = null;


        ReportServiceResponse reportResponse = new ReportServiceResponse();
        int formTypeId = -1;
        try {
            FormType ft = EntityCacheMgr.getInstance().getFormTypeByName(reportType);
            formTypeId = ft.getFormTypeId();
        } catch(ICSDatastoreException e) {
            APILogger.getInstance().e("ReportService:getReports()",
                    String.format("Exception getting formTypeId for type(%s): %s", reportType, e.getMessage()));

            reportResponse.setMessage(String.format("Exception getting formTypeId for type(%s): %s",
                    reportType,  e.getMessage()));

            return Response.ok(reportResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        //int formTypeId = -1;   // -1 means "All types of PHINICS reports.
		/*if (!"PHI".equalsIgnoreCase(reportType)) {
			// A specific type of report is	being requested.
			try {
				FormType ft = EntityCacheMgr.getInstance().getFormTypeByName(reportType);
				formTypeId = ft.getFormTypeId();
			} catch (ICSDatastoreException e) {
				APILogger.getInstance().e(CNAME, e.getMessage());
				reportResponse.setMessage("Failure. " + e.getMessage());
				reportResponse.setCount(0);
				response = Response.ok(reportResponse).status(Status.PRECONDITION_FAILED).build();
				return response;
			}
		}*/
        try {
            //reports = ReportDAO.getInstance().getReports(formTypeId, userName, queryConstraints);
            Set<Integer> formTypeIds = new HashSet<Integer>();
            formTypeIds.add(formTypeId);
            List<Form> forms = formDao.readForms(formTypeIds, queryConstraints);
			
			/*User u = null;
			try {
				if (userName != null && !userName.isEmpty()) {
					u = EntityCacheMgr.getInstance().getUserEntityByUsername(userName);
				}
			} catch (Exception e) {
				// Ignore it. We just need the User ID out of the entire entity.
			}*/
			
			/*Report rep = null;
			reports = new LinkedHashSet<Report>();
			for(Form form : forms) {
				rep = new Report();
				rep.setFormId(form.getFormId());
				rep.setFormTypeId(form.getFormtypeid());
				if (u != null) {
					rep.setSenderUserId(u.getUserId());
				}
				rep.setMessage(form.getMessage());
				rep.setIncidentId(form.getIncidentid());
				rep.setIncidentName(form.getIncidentname());
				rep.setUserSessionId(form.getUsersessionid());
				rep.setSeqTime(form.getSeqtime());
				rep.setCreatedUTC(form.getSeqtime());
				rep.setLastUpdatedUTC(form.getSeqtime());
				rep.setSeqNum(form.getSeqnum());
				reports.add(rep);
			}*/

            reportResponse.setReports(forms);
            reportResponse.setMessage("ok");
            reportResponse.setCount(reportResponse.getReports().size());
            response = Response.ok(reportResponse).status(Status.OK).build();
        } catch (Exception e) {
            e.printStackTrace();
            APILogger.getInstance().e(CNAME, e.getMessage());
            reportResponse.setMessage("Datastore exception, failed to read " +
                    "reports for incidentid " + incidentId + " of form type " + reportType);
            response = Response.ok(reportResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    /**
     * Delete all Report items.
     * This is an unsupported operation.
     *  Response
     *  ReportResponse
     */
    public Response deleteReports(int reportType) {
        return makeUnsupportedOpRequestResponse();
    }

    /**
     * Bulk creation of Report items.
     *  A collection of Report items to be created.
     *  Response
     *  ReportResponse
     */
    public Response putReports(int reportType, Collection<Report> reports) {
        // TODO: Needs implementation.
        return makeUnsupportedOpRequestResponse();
    }

    /**
     * Bulk creation of Form entities
     *
     * @param forms
     * @return
     */
    public Response postReports(Collection<Form> forms) {
        return makeUnsupportedOpRequestResponse();
    }

    /*
    public Response postIncidentAndROC(int orgId, Form form) {
        ReportServiceResponse reportServiceResponse = new ReportServiceResponse();
        boolean isIncidentPersisted = false;
        User user = null;
        try {
            if (form != null && (user = form.getUsersessionid() < 0 ? null : userDao.getUserBySessionId(form.getUsersessionid())) == null) {
                return Response.ok(new APIResponse(Status.UNAUTHORIZED.getStatusCode(), "Unauthorized, session with userSessionId " + form.getUsersessionid() + " is not active.")).build();
            }

            //validate input
            Map<String, String> validationErrors = reportValidator.validateForm(form, SADisplayConstants.FORM_TYPE_ROC, true);
            if(!validationErrors.isEmpty()) {
                ValidationErrorResponse validationErrorResponse = new ValidationErrorResponse(Status.BAD_REQUEST.getStatusCode(), "Invalid input", validationErrors);
                return Response.ok(validationErrorResponse).build();
            }
            Response iResponse = incidentService.postIncident(SADisplayConstants.DEFAULT_WORKSPACE_ID, orgId, user.getUserId(), form.getIncident(), form);
            IncidentServiceResponse incidentResponse = (iResponse.getEntity() instanceof IncidentServiceResponse) ? (IncidentServiceResponse) iResponse.getEntity() : null;

            if(iResponse.getStatus() == Status.OK.getStatusCode()) {
                if(incidentResponse.getCount() < 1) {
                    return iResponse; //send ok response with reason for FAILURE
                }
                isIncidentPersisted = true;

                Incident incidentPersisted = incidentResponse.getIncidents().iterator().next();
                return this.persistReportOnIncident(form, incidentPersisted);
            } else {
                APIResponse apiResponse = (incidentResponse != null) ? new APIResponse(iResponse.getStatus(), "Error persisting incident & ROC: " + incidentResponse.getMessage()) : new APIResponse(iResponse.getStatus(), "Error persisting incident & ROC");
                return Response.ok(apiResponse).build();
            }
        } catch (Exception e) {
            String errorMessage = (isIncidentPersisted ? "Successfully created new incident with name " + form.getIncident().getIncidentname() + ", but failed to persist ROC, Error: " : "Error persisting incident & ROC report: ") + e.getMessage();
            APILogger.getInstance().e("ReportServiceImpl", errorMessage, e);
            return Response.ok(new APIResponse(Status.INTERNAL_SERVER_ERROR.getStatusCode(), errorMessage)).build();
        }
    }

    */

    private Response persistReportOnIncident(Form form, Incident incident) throws Exception {
        form.setIncidentid(incident.getIncidentid());
        form.setIncidentname(incident.getIncidentname());
        return this.persistReport(SADisplayConstants.FORM_TYPE_ROC, form, incident.getIncidentid());
    }

    /**
     * Persists a single Form entity. If the Form has a valid and existing formid, it is
     * updated. Otherwise, it is persisted as a new form.
     *
     * @param form Form to update/persist
     * @return
     */
    public Response postReport(int incidentId, String reportType, Form form) {
        ReportServiceResponse reportServiceResponse = new ReportServiceResponse();
        if(form != null && (form.getUsersessionid() < 0 || userDao.getUserBySessionId(form.getUsersessionid()) == null)) {
            reportServiceResponse.setMessage("Unauthorized, session with userSessionId " + form.getUsersessionid() + " is not active.");
            return Response.ok(reportServiceResponse).status(Status.UNAUTHORIZED).build();
        }

        APILogger.getInstance().i("postReport", "Got report:\nincidentId: " + incidentId +
                "\nreportType: " + reportType + "\nForm:\n" + (form == null ? null : form.toString()));

        try {
            Map<String, String> valid = reportValidator.validateForm(form, reportType, false);
            if (!valid.isEmpty()) {
                reportServiceResponse.setMessage("failure: " + valid.values());
                return Response.ok(reportServiceResponse).status(Status.EXPECTATION_FAILED).build();
            }
            return this.persistReport(reportType, form, incidentId);
        } catch(Exception e) {
            APILogger.getInstance().e("ReportServiceImpl", "Exception persisting new form with type id: " +
                    form.getFormtypeid(), e);
            reportServiceResponse.setMessage("Failed to persist report: " + e.getMessage());
            return Response.ok(reportServiceResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Response persistReport(String reportType, Form form, int incidentId) throws Exception {
        ReportServiceResponse reportServiceResponse = new ReportServiceResponse();
        Response response = null;
        Form persistedForm = formDao.persistForm(form);

        if(persistedForm != null) {
            reportServiceResponse.setMessage("success: persisted report");
            reportServiceResponse.setCount(1);
            reportServiceResponse.getReports().add(persistedForm);
            reportServiceResponse.setStatus(Status.OK.getStatusCode());
            response = Response.ok(reportServiceResponse).status(Status.OK).build();

            // Since it was successful, also send it out to new users, except now any persist/create calls
            // to dao need to return the new fully formed form, with formid and everything...
            try {
                String topic = String.format("iweb.NICS.incident.%d.report.%s.new", form.getIncidentid(), reportType.toUpperCase());
                notifyNewReport(topic, form);

                Incident inc = incidentDao.getIncident(incidentId);
                User creator = userDao.getUserBySessionId(form.getUsersessionid());
                createNewROCIncidentEmail(creator, creator.getUsername(), inc, form);

            } catch (IOException e) {
                APILogger.getInstance().e("ReportServiceImpl", "Exception publishing new form with type id: " +
                        form.getFormtypeid());
                e.printStackTrace();
            }
        }
        return response;
    }

    private void createNewROCIncidentEmail(User creator, String toEmails, Incident newIncident, Form form) {
        JsonEmail email = null;
        Boolean prefixComma = false;

        try{
            String alertTopic = String.format("iweb.nics.email.alert");
            ROCMessage rocMessage = getROCMessage(form.getMessage());

            /* ------------------------- */
            /* Start Building Email Here */
            /* ------------------------- */


            /* Subject Line */
            String emailSubject = newIncident.getIncidentname() + ", ";
            emailSubject = emailSubject.replaceAll(rocMessage.getOrgPrefix(), "");

            /* Subject Line - Incident Types */
            List<IncidentType> incidentTypes = incidentDao.getIncidentTypesByIncidentId(newIncident.getIncidentid());
            if(incidentTypes != null && !incidentTypes.equals("null")) {
                int incidentTypesArrayListSize = incidentTypes.size();
                if(incidentTypesArrayListSize > 0) {
                    for (int i = 0; i < incidentTypesArrayListSize; i++) {
                        emailSubject = emailSubject + incidentTypes.get(i).getIncidentTypeName() + ", ";
                    }
                }
            }

            emailSubject = emailSubject + rocMessage.getCounty();

            if (rocMessage.getAdditionalAffectedCounties() != null && !rocMessage.getAdditionalAffectedCounties().equals("null") && rocMessage.getAdditionalAffectedCounties().trim().length() > 0) {
                List<String> additionalCountiesArrayList = new ArrayList<>(Arrays.asList(rocMessage.getAdditionalAffectedCounties().split("\\s*,\\s*")));
                int additionalCountiesArrayListSize = additionalCountiesArrayList.size();

                if(additionalCountiesArrayList.size() > 0) {
                    for (int i = 0; i < additionalCountiesArrayListSize; i++) {
                        if(i == additionalCountiesArrayListSize - 1) {
                            emailSubject = emailSubject + " and ";
                        } else {
                            emailSubject = emailSubject + ", ";
                        }

                        emailSubject = emailSubject + additionalCountiesArrayList.get(i);
                    }
                }
                emailSubject = emailSubject + " Counties, ";
            } else {
                emailSubject = emailSubject + " County, ";
            }

            emailSubject = emailSubject + convertToTitleCaseIteratingChars(rocMessage.getReportType());

            email = new JsonEmail(creator.getUsername(), toEmails + ", nikhil.devre@tabordasolutions.com", emailSubject);


            /* ************************************** */
            /* Email Body String building Starts Here */
            /* ************************************** */
            StringBuilder emailBodyString = new StringBuilder();
            emailBodyString.append("<html>");
            emailBodyString.append("<head>");
            emailBodyString.append("<style>");
            emailBodyString.append("ul { padding: 0; list-style-type: none; }");
            emailBodyString.append("</style>");
            emailBodyString.append("</head>");
            emailBodyString.append("<body><div>");


            /* Disclaimer */
            emailBodyString.append("&quot;Intel - for internal use only. Numbers subject to change.&quot;");
            emailBodyString.append("<br/><br/>");


            /* ------------- */
            /* Location line */
            /* ------------- */
            emailBodyString.append("<div>");

            // Street x Cross Street, Miles from Nearest Community miles Direction from Nearest Community of Nearest Community
            if(rocMessage.getStreet().trim().length() > 0) {
                emailBodyString.append(rocMessage.getStreet()  + " x ");
            }

            // Cross Street
            if(rocMessage.getCrossStreet().trim().length() > 0) {
                emailBodyString.append(rocMessage.getCrossStreet()  + ", ");
            }

            // Miles from nearest community
            if(rocMessage.getMilesFromNearestCommunity().trim().length() > 0) {
                if(rocMessage.getMilesFromNearestCommunity().equals("1")) {
                    emailBodyString.append(rocMessage.getMilesFromNearestCommunity()  + " mile ");
                } else {
                    emailBodyString.append(rocMessage.getMilesFromNearestCommunity()  + " miles ");
                }
            }

            // Direction from Nearest Community
            if(rocMessage.getDirectionFromNearestCommunity().trim().length() > 0) {
                emailBodyString.append(rocMessage.getDirectionFromNearestCommunity()  + " of ");
            }

            // Nearest community
            if(rocMessage.getNearestCommunity().trim().length() > 0) {
                emailBodyString.append(rocMessage.getNearestCommunity());
            }

            emailBodyString.append("</div>");


            /* -------------------------------- */
            /* DPA DPA, Ownership, Jurisdiction */
            /* -------------------------------- */
            emailBodyString.append("<div>");

            // DPA
            if (rocMessage.getDpa().trim().length() > 0) {
                emailBodyString.append(rocMessage.getDpa() + " DPA");
            }

            // SRA
            if (rocMessage.getSra().trim().length() > 0) {
                emailBodyString.append(", " + rocMessage.getSra());
            }

            // Jurisdiction
            if(rocMessage.getNearestCommunity().trim().length() > 0) {
                emailBodyString.append(", " + rocMessage.getJurisdiction());
            }
            emailBodyString.append("</div>");


            /* ---------------------- */
            /* Start time: Start Time */
            /* ---------------------- */
            // emailBodyString.append();

            // Start Time
            if(rocMessage.getReportType().equals("NEW")) {
                emailBodyString.append("<div>");
                emailBodyString.append("Start time: " + rocMessage.getStartTime());
                emailBodyString.append("</div>");
            }

            /* =================== */
            /* Start Unsorted List */
            /* =================== */
            emailBodyString.append("<ul>");


            /* --------------------------------------------------- */
            /* Acreage acres Fuel Type(s), % Contained % contained */
            /* --------------------------------------------------- */


            // Scope
            if (rocMessage.getScope().trim().length() > 0 && rocMessage.getScope() != null && !rocMessage.getScope().equals("null")) {
                emailBodyString.append("<li>&bull; ");
                emailBodyString.append(rocMessage.getScope() + " acres ");
            }

            // Fuel Types
            if(!rocMessage.getReportType().equals("FINAL")) {
                if (rocMessage.getFuelTypes() != null && !rocMessage.getFuelTypes().equals("null")) {
                    int fuelTypesArraySize = rocMessage.getFuelTypes().size();
                    for (int i = 0; i < fuelTypesArraySize; i++) {
                        if(i == fuelTypesArraySize - 1) {
                            emailBodyString.append(" and ");
                        }

                        if (rocMessage.getFuelTypes().get(i).equalsIgnoreCase("Other")) {
                            emailBodyString.append(rocMessage.getOtherFuelTypes() + ", ");
                        } else {
                            emailBodyString.append(rocMessage.getFuelTypes().get(i).toLowerCase() + ", ");
                        }
                    }
                }
            }

            // Percentage Contained
            if (rocMessage.getPercentageContained().trim().length() > 0 && rocMessage.getPercentageContained() != null && !rocMessage.getPercentageContained().equals("null")) {
                emailBodyString.append(rocMessage.getPercentageContained() + "% contained");
            }

            emailBodyString.append("</li>");


            /* -------------- */
            /* Rate of Spread */
            /* -------------- */

            // Spread Rate
            if(rocMessage.getReportType().equals("NEW")) {
                if (rocMessage.getSpreadRate().trim().length() > 0 && rocMessage.getSpreadRate() != null && !rocMessage.getSpreadRate().equals("null")) {
                    emailBodyString.append("<li>&bull; ");
                    emailBodyString.append(rocMessage.getSpreadRate());
                    emailBodyString.append("</li>");
                }
            }


            /* ------------------------------------------------------------------------------------------------- */
            /* Temperature degrees, Relative Humidity% RH, wind Wind Direction @ Wind Speed, gusts to Wind Gusts */
            /* ------------------------------------------------------------------------------------------------- */

            if(rocMessage.getReportType().equals("NEW")) {
                if (
                        rocMessage.getTemperature() != null && !rocMessage.getTemperature().equals("null") ||
                                rocMessage.getRelHumidity() != null && !rocMessage.getRelHumidity().equals("null") ||
                                rocMessage.getWindDirection() != null && !rocMessage.getWindDirection().equals("null") ||
                                rocMessage.getWindSpeed() != null && !rocMessage.getWindSpeed().equals("null") ||
                                rocMessage.getWindGust() != null && !rocMessage.getWindGust().equals("null")
                ) {
                    emailBodyString.append("<li>&bull; ");
                }

                // Tempreature
                if (rocMessage.getTemperature() != null && !rocMessage.getTemperature().equals("null")) {
                    int temperatureInEmailMessage = (int) Math.round(rocMessage.getTemperature());
                    emailBodyString.append(temperatureInEmailMessage + " degrees");
                    prefixComma = true;
                }

                // Humidity
                if (rocMessage.getRelHumidity() != null && !rocMessage.getRelHumidity().equals("null")) {
                    if (prefixComma) {
                        emailBodyString.append(", " + rocMessage.getRelHumidity().intValue() + "% RH");
                    } else {
                        emailBodyString.append(rocMessage.getRelHumidity().intValue() + "% RH");
                        prefixComma = true;
                    }
                }

                // Wind Direction
                if (rocMessage.getWindDirection() != null && !rocMessage.getWindDirection().equals("null")) {
                    if (prefixComma) {
                        emailBodyString.append(", wind " + rocMessage.getWindDirection());
                    } else {
                        emailBodyString.append(" wind " + rocMessage.getWindDirection());
                    }
                }

                // Wind Speed
                if (rocMessage.getWindSpeed() != null && !rocMessage.getWindSpeed().equals("null")) {
                    emailBodyString.append(" @ " + rocMessage.getWindSpeed().intValue());
                }

                // Wind Gust
                if (rocMessage.getWindGust() != null && !rocMessage.getWindGust().equals("null")) {
                    emailBodyString.append(", gusts to " + rocMessage.getWindGust().intValue());
                }

                if (
                        rocMessage.getTemperature() != null && !rocMessage.getTemperature().equals("null") ||
                                rocMessage.getRelHumidity() != null && !rocMessage.getRelHumidity().equals("null") ||
                                rocMessage.getWindDirection() != null && !rocMessage.getWindDirection().equals("null") ||
                                rocMessage.getWindSpeed() != null && !rocMessage.getWindSpeed().equals("null") ||
                                rocMessage.getWindGust() != null && !rocMessage.getWindGust().equals("null")
                ) {
                    emailBodyString.append("</li>");
                }
            }


            /* --------------------------------- */
            /* Structures Threat in progress for */
            /* --------------------------------- */

            if(rocMessage.getStructuresThreats() != null && !rocMessage.getStructuresThreats().equals("null")) {
                int structureThreatArraySize = rocMessage.getStructuresThreats().size();

                if(structureThreatArraySize > 0) {
                    for (int i = 0; i < structureThreatArraySize; i++) {
                        emailBodyString.append("<li>&bull; ");
                        if(rocMessage.getStructuresThreats().get(i).equalsIgnoreCase("Other")){
                            emailBodyString.append(rocMessage.getOtherStructuresThreat());
                        } else {
                            emailBodyString.append(rocMessage.getStructuresThreats().get(i));
                        }
                        emailBodyString.append("</li>");
                    }
                }
            }


            /* -------------------------------------- */
            /* Infrastructures Threat in progress for */
            /* -------------------------------------- */

            if(rocMessage.getInfrastructuresThreats() != null && !rocMessage.getInfrastructuresThreats().equals("null")) {
                int infraStructureThreatArraySize = rocMessage.getInfrastructuresThreats().size();

                if(infraStructureThreatArraySize > 0) {
                    for (int i = 0; i < infraStructureThreatArraySize; i++) {
                        emailBodyString.append("<li>&bull; ");
                        if(rocMessage.getInfrastructuresThreats().get(i).equalsIgnoreCase("Other")){
                            // otherInfrastructuresThreat
                            emailBodyString.append(rocMessage.getOtherInfrastructuresThreat());
                        } else {
                            emailBodyString.append(rocMessage.getInfrastructuresThreats().get(i));
                        }
                        emailBodyString.append("</li>");
                    }
                }
            }


            /* --------------------------- */
            /* Evacuations in progress for */
            /* --------------------------- */

            if(rocMessage.getEvacuationsList() != null && !rocMessage.getEvacuationsList().equals("null")) {
                int evacuationsListArraySize = rocMessage.getEvacuationsList().size();

                if(evacuationsListArraySize > 0) {
                    for (int i = 0; i < evacuationsListArraySize; i++) {
                        emailBodyString.append("<li>&bull; ");
                        if(rocMessage.getEvacuationsList().get(i).equalsIgnoreCase("Other")){
                            // otherEvacuations
                            emailBodyString.append(rocMessage.getOtherEvacuations());
                        } else {
                            emailBodyString.append(rocMessage.getEvacuationsList().get(i));
                        }

                        emailBodyString.append("</li>");
                    }
                }
            }


            /* ---------------------- */
            /* Other Significant Info */
            /* ---------------------- */

            if(rocMessage.getOtherSignificantInfo() != null && !rocMessage.getOtherSignificantInfo().equals("null")) {
                int otherSignificantInfoArraySize = rocMessage.getOtherSignificantInfo().size();

                if(otherSignificantInfoArraySize > 0) {
                    for (int i = 0; i < otherSignificantInfoArraySize; i++) {
                        emailBodyString.append("<li>&bull; ");
                        if(rocMessage.getOtherSignificantInfo().get(i).equalsIgnoreCase("Other")){
                            emailBodyString.append(rocMessage.getOtherOtherSignificantInfo());
                        } else {
                            emailBodyString.append(rocMessage.getOtherSignificantInfo().get(i));
                        }

                        emailBodyString.append("</li>");
                    }
                }
            }


            /* ------------------ */
            /* Resources Assigned */
            /* ------------------ */

            if(rocMessage.getResourcesAssigned() != null && !rocMessage.getResourcesAssigned().equals("null")) {
                int resourcesAssignedArraySize = rocMessage.getResourcesAssigned().size();

                if(resourcesAssignedArraySize > 0) {
                    for (int i = 0; i < resourcesAssignedArraySize; i++) {
                        emailBodyString.append("<li>&bull; ");
                        // otherResourcesAssigned
                        if(rocMessage.getResourcesAssigned().get(i).equalsIgnoreCase("Other")){
                            emailBodyString.append(rocMessage.getOtherResourcesAssigned());
                        } else {
                            emailBodyString.append(rocMessage.getResourcesAssigned().get(i));
                        }

                        emailBodyString.append("</li>");
                    }
                }
            }


            emailBodyString.append("</ul>");
            /* ==================== */
            /* End of Unsorted List */
            /* ==================== */


            emailBodyString.append("</div></body></html>");
            /* ************************************** */
            /* Email Body String building Ends Here.. */
            /* ************************************** */



            /* Set email body */
            email.setBody(emailBodyString.toString());
            notifyNewIncidentEmail(email.toJsonObject().toString(),alertTopic);
        } catch (Exception e) {
            APILogger.getInstance().e(CNAME,"Failed to send new Incident email alerts");
            APILogger.getInstance().e(CNAME, ExceptionUtils.getStackTrace(e));
        }
    }

    public static String convertToTitleCaseIteratingChars(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder converted = new StringBuilder();
        boolean convertNext = true;

        for (char ch : text.toCharArray()) {
            if (Character.isSpaceChar(ch)) {
                convertNext = true;
            } else if (convertNext) {
                ch = Character.toTitleCase(ch);
                convertNext = false;
            } else {
                ch = Character.toLowerCase(ch);
            }
            converted.append(ch);
        }
        return converted.toString();
    }


    private ROCMessage getROCMessage(String message) throws IOException{
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addDeserializer(ROCMessage.class, new ROCMessageDeserializer());
        objectMapper.registerModule(simpleModule);

        return objectMapper.readValue(message, ROCMessage.class);
    }

    private void notifyNewIncidentEmail(String email, String topic) throws IOException {
        if (email != null) {
            getRabbitProducer().produce(topic, email);
        }
    }

































    /**
     *  Creation of a single Report item.
     *  A collection of Report items to be created.
     *  Response
     *  ReportResponse
     */
    @Deprecated
    public Response postReports(int incidentId, int reportType, Report report) {

        return makeUnsupportedOpRequestResponse();
		/*
		// Make sure we support this type of form.
		Response response = validateReportType(reportType, "POST");
		if (response != null) {
			return response;
		}

		List<Report> reports = new ArrayList<Report>();
		ReportServiceResponse reportResponse = new ReportServiceResponse();
		int formTypeId = -1;
		try {
			FormType ft = EntityCacheMgr.getInstance().getFormTypeById(reportType);
			formTypeId = ft.getFormTypeId();
		} catch (ICSDatastoreException e) {
			APILogger.getInstance().e(CNAME, e.getMessage());
			reportResponse.setMessage("Failure. " + e.getMessage());
			reportResponse.setCount(0);
			response = Response.ok(reportResponse).status(Status.PRECONDITION_FAILED).build();
			return response;
		}
		
		try {
			// TODO:1209 workspaceId was changed to incidentId		
			reports.add(ReportDAO.getInstance().postReport(incidentId, formTypeId, report));
			
			reportResponse.setMessage("ok");
			reportResponse.setCount(reports.size());
			reportResponse.setReports(reports);
			response = Response.ok(reportResponse).status(Status.OK).build();			
		} catch (ICSDatastoreException e) {
			APILogger.getInstance().e(CNAME, e.getMessage());
			reportResponse.setMessage("Datastore exception, failed to publish form.");
			response = Response.ok(reportResponse).status(Status.INTERNAL_SERVER_ERROR).build();			
		}
		return response;*/
    }


    public Response postReports(int incidentId, String reportType, MultipartBody body) {
        Response response = validateReportType(reportType, "POST");
        ReportServiceResponse reportResponse = new ReportServiceResponse();

        int formTypeId = -1;
        FormType ft = null;
        APILogger.getInstance().d(CNAME, "Beginning");
        try {
            ft = EntityCacheMgr.getInstance().getFormTypeByName(reportType);
            formTypeId = ft.getFormTypeId();
        } catch (ICSDatastoreException e) {
            APILogger.getInstance().e(CNAME, e.getMessage(), e);
            reportResponse.setMessage("Failure. " + e.getMessage());
            reportResponse.setCount(0);
            response = Response.ok(reportResponse).status(Status.PRECONDITION_FAILED).build();
            return response;
        }
        APILogger.getInstance().d(CNAME, "Fetched formTypeId for postReports");
        try {
            if(ft.getFormTypeName().toUpperCase().equals("SR")) {
                APILogger.getInstance().e(CNAME, "Processing Simple Report POST request");
                return handleSimpleReport(incidentId, formTypeId, body);
            } else if(ft.getFormTypeName().toUpperCase().equals("DMGRPT")) {
                return handleDamageReport(incidentId, formTypeId, body);
            }else if(ft.getFormTypeName().toUpperCase().equals("UXO")) {
                return handleUXOReport(incidentId, formTypeId, body);
            }
        } catch (BadContentException e) {
            APILogger.getInstance().e(CNAME, e.getMessage(), e);
            reportResponse.setMessage("Failure. " + e.getMessage());
            reportResponse.setCount(0);
            response = Response.ok(reportResponse).status(Status.BAD_REQUEST).build();
            return response;
        } catch (JSONException e) {
            APILogger.getInstance().e(CNAME, e.getMessage(), e);
            reportResponse.setMessage("Failure. " + e.getMessage());
            reportResponse.setCount(0);
            response = Response.ok(reportResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        } catch (Exception e) {
            APILogger.getInstance().e(CNAME, e.getMessage(), e);
            reportResponse.setMessage("Failure. " + e.getMessage());
            reportResponse.setCount(0);
            response = Response.ok(reportResponse).status(Status.PRECONDITION_FAILED).build();
            return response;
        }

        return response;
    }

    /**
     *  Read a single Report item.
     *  ID of Report item to be read.
     *  Response
     *  ReportResponse
     */
    public Response getReport(int reportType, int reportId, String fields) {
        // TODO: Needs implementation.
        return makeUnsupportedOpRequestResponse();
    }

    /**
     *  Delete a single Report item.
     *  ID of Report item to be read.
     *  Response
     *  ReportResponse
     */
    public Response deleteReport(int reportType, int reportId) {
        // TODO: Needs implementation.
        return makeUnsupportedOpRequestResponse();
    }

    /**
     *  Update a single Report item.
     *  ID of Report item to be read.
     *  Response
     *  ReportResponse
     */
    public Response putReport(int reportType, int reportId, Report report) {
        // TODO: Needs implementation.
        return makeUnsupportedOpRequestResponse();
    }

    /**
     *  Post a single Report item.
     *  This is an illegal operation.
     *  ID of Report item to be read.
     *  Response
     *  ReportResponse
     */
    public Response postReport(int reportType, int reportId) {
        // Illegal as per RESTful guidelines.
        return makeIllegalOpRequestResponse();
    }

    /**
     *  Return the number of Report items stored.
     *  Response
     *  ReportResponse
     */
    public Response getReportCount(int reportType) {
        // TODO: Needs implementation.
        return makeUnsupportedOpRequestResponse();
    }


    /**
     *  Search the Report items stored.
     *  Response
     *  ReportResponse
     */
    public Response searchReportResources(int reportType, ReportOptParms optParms) {
        // TODO: Needs implementation.
        return makeUnsupportedOpRequestResponse();
    }

    private Response makeIllegalOpRequestResponse() {
        ReportServiceResponse reportResponse = new ReportServiceResponse();
        reportResponse.setMessage("Request ignored.") ;
        return Response.notModified("Illegal operation requested").
                status(Status.FORBIDDEN).build();
    }

    private Response makeUnsupportedOpRequestResponse() {
        ReportServiceResponse reportResponse = new ReportServiceResponse();
        reportResponse.setMessage("Request ignored.") ;
        return Response.notModified("Unsupported operation requested").
                status(Status.NOT_IMPLEMENTED).build();
    }

    private Response validateReportType(String reportType, String htmlOp) {
        Response response = null;
        ReportServiceResponse reportResponse = new ReportServiceResponse();

        try {
            FormType ft = EntityCacheMgr.getInstance().getFormTypeByName(reportType);
            if (ft == null) {
                Set<String> validTypes = EntityCacheMgr.getInstance().getFormTypeNames();
                StringBuilder sb = new StringBuilder();
                sb.append("Failure. Report type [").append(reportType)
                        .append("] not supported. Valid types are [")
                        .append(org.apache.commons.lang.StringUtils.join(validTypes, ","))
                        .append("].");
                APILogger.getInstance().e(CNAME, sb.toString());
                reportResponse.setMessage(sb.toString());
                reportResponse.setCount(0);
                response = Response.ok(reportResponse).status(Status.BAD_REQUEST).build();
            }
        } catch (NullPointerException e) {
            APILogger.getInstance().e(CNAME, "Missing reportType URI argument.");
            reportResponse.setMessage("Failure. Missing reportType URI argument.");
            reportResponse.setCount(0);
            response = Response.ok(reportResponse).status(Status.BAD_REQUEST).build();
        } catch (ICSDatastoreException e) {
            APILogger.getInstance().e(CNAME, e.getMessage());
            reportResponse.setMessage("Failure. " + e.getMessage());
            reportResponse.setCount(0);
            response = Response.ok(reportResponse).status(Status.BAD_REQUEST).build();
        } catch(Exception e) {
            APILogger.getInstance().e(CNAME, e.getMessage());
            reportResponse.setMessage("Failure. " + e.getMessage());
            reportResponse.setCount(0);
            response = Response.ok(reportResponse).status(Status.BAD_REQUEST).build();
        }

        return response;
    }

    private Response handleSimpleReport(int incidentId,
                                        int formTypeId, MultipartBody body) throws JSONException,
            BadContentException {
        Response response = null;
        ReportServiceResponse reportResponse = new ReportServiceResponse();

        List<Attachment> attachments = body.getAllAttachments();
        String key, value;

        // TODO:refactor remove phinics-dev url, and ensure defaults are
        // up to date with our newer Ubuntu 14.04 VM deployments
        String storagePath = APIConfig.getInstance().getConfiguration()
                .getString(APIConfig.REPORTS_SR_STORAGEPATH,
                        "/opt/data/nics/upload/");
        String url = APIConfig.getInstance().getConfiguration()
                .getString(APIConfig.REPORTS_SR_URL,
                        "/data/nics/static/image-upload/");
        String path = APIConfig.getInstance().getConfiguration()
                .getString(APIConfig.REPORTS_SR_PATH,
                        "https://phinics-dev.ll.mit.edu/static/image-upload/");

        // DB
        //PhinicsDbFacade db = PhinicsDbFactory.getInstance().getPhinicsDbFacadeSingleton();
        Location location = new Location();
        location.setTime(-1);

        // Other necessary entities
        Incident inc = null;
        User user = null;
        Coordinate lla = new Coordinate();
        Report report = new Report();
        Form form = new Form();
        form.setIncidentid(incidentId);
        form.setFormtypeid(formTypeId);
        form.setUsersessionid(-1);
        JSONObject msg = new JSONObject();
        String filename;
        String ext = ".png";
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

        for(Attachment a : attachments) {
            MediaType type = a.getContentType();

            if(type.getType().contains("text")) {
                key = a.getContentDisposition().getParameter("name");
                value = a.getObject(String.class);

                if (key.equalsIgnoreCase("incidentid")) {
                    report.setIncidentId(Integer.parseInt(value));
                    inc = incidentDao.getIncident(report.getIncidentId());
                } else if (key.equalsIgnoreCase("userid")) {
                    location.setUserid(Integer.parseInt(value));
                    user = userDao.getUserById(location.getUserid());
                    if(form.getUsersessionid() < 0) {
                        form.setUsersessionid(userSessionDao.getUserSessionid(Integer.parseInt(value)));
                    }
                    report.setSenderUserId(user.getUserId());
                    msg.put("user", user.getUsername());
                } else if (key.equalsIgnoreCase("usersessionid")) {
                    int usersessid = Integer.parseInt(value);
                    user = userDao.getUserBySessionId(usersessid);
                    if(user == null) {
                        reportResponse.setCount(0);
                        reportResponse.setMessage(String.format("userSessionId %s is not currently active", value));
                        APILogger.getInstance().e(CNAME, String.format("userSessionId %s is not currently active", value));
                        return Response.ok(reportResponse).status(Status.UNAUTHORIZED).build();
                    }
                    location.setUserid(user.getUserId());
                    report.setUserSessionId(Integer.parseInt(value));
                    if(usersessid > 0 && form.getUsersessionid() < 0) {
                        // Set the usersessionid if it's valid and not already set
                        form.setUsersessionid(usersessid);
                    }
                    report.setSenderUserId(user.getUserId());
                    msg.put("user", user.getUsername());
                } else if (key.equalsIgnoreCase("deviceid")) {
                    location.setDeviceId(value);
                } else if (key.equalsIgnoreCase("latitude")) {
                    lla.y = Double.parseDouble(value);
                    msg.put("lat", lla.y);
                } else if (key.equalsIgnoreCase("longitude")) {
                    lla.x = Double.parseDouble(value);
                    msg.put("lon", lla.x);
                } else if (key.equalsIgnoreCase("altitude")) {
                    lla.z = Double.parseDouble(value);
                } else if (key.equalsIgnoreCase("track")) {
                    location.setCourse(Double.parseDouble(value));
                } else if (key.equalsIgnoreCase("speed")) {
                    location.setSpeed(Double.parseDouble(value));
                } else if (key.equalsIgnoreCase("accuracy")) {
                    location.setAccuracy(Double.parseDouble(value));
                } else if (key.equalsIgnoreCase("seqtime")) {
                    location.setTime(Long.parseLong(value));
                } else if (key.equalsIgnoreCase("description")) {
                    msg.put("desc", value);
                } else if (key.equalsIgnoreCase("category")) {
                    msg.put("cat", value);
                }
            } else {
                filename = Calendar.getInstance().getTimeInMillis() + "-0" + ext;
                msg.put("image", filename);

                byte[] content = a.getObject(byte[].class);
                if (content != null) {
                    // Write to file
                    File f = new File(storagePath.concat(filename));
                    try {
                        FileUtils.writeByteArrayToFile(f, content);
                        msg.put("image", url.concat(filename));
                        msg.put("fullpath", path.concat(filename));
                    } catch (IOException e) {
                        APILogger.getInstance().e(CNAME, "Unable to write file" + e.getMessage(), e);
                        throw new BadContentException("Unable to write file: " + e.getMessage());
                    }
                } else {
                    APILogger.getInstance().e(CNAME, "No attachment");
                    throw new BadContentException("No attachment");
                }
            }
        }

        if(location.getTime() < 0) {
            location.setTime(Calendar.getInstance().getTimeInMillis());
        }
        report.setSeqTime(location.getTime());
        report.setMessage(msg.toString());

        form.setSeqtime(location.getTime());
        form.setMessage(msg.toString());

        // Set DB orm
        if(!validLocation(lla)) {
            lla.x = inc.getLon();
            lla.y = inc.getLat();
            lla.z = 0;
        }
        location.setLocation(gf.createPoint(lla));

        Image image = new Image();
        image.setIncident(inc);
        image.setLocation(location);
        image.setUrl(msg.getString("image"));
        image.setFullPath(msg.getString("fullpath"));

        // Persist DB entries
        //db.persist(location);
        //db.persist(image);
		
		/* Not persisting image or location entities, because nothing uses them, it's a PHINICS holdover. Also, 
		 * in move to springjdbc, there's a complication with persisting Location's location field of type Geometry
		 * 
		 *
		try {
			phiDao.persistLocation(location);
			phiDao.persistImage(image);
		} catch(Exception e) {
			APILogger.getInstance().e(CNAME, "Exception persisting location or image: " + e.getMessage());
			reportResponse.setMessage("error. Problem persisting location and image entities");
			response = Response.ok(reportResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			return response;
		}*/

        try {
            //reportResponse.getReports().add(ReportDAO.getInstance().postReport(
            //	incidentId, formTypeId, report));

            Form ret = null;
            ret = formDao.persistForm(form);

            if(ret != null) {
                reportResponse.setCount(1);
                reportResponse.setMessage("ok");
                reportResponse.getReports().add(ret);
                response = Response.ok(reportResponse).status(Status.OK).build();
                try {
                    String topic = String.format("iweb.NICS.incident.%d.report.SR.new", form.getIncidentid());
                    notifyNewReport(topic, form);
                } catch (IOException e) {
                    APILogger.getInstance().e(CNAME, "Exception publishing new form with type id: " +
                            form.getFormtypeid(), e);
                    e.printStackTrace();
                }
            } else {
                reportResponse.setCount(0);
                reportResponse.setMessage("Unable to persist report!");
                APILogger.getInstance().e(CNAME, "Unable to persist report");
                response = Response.ok(reportResponse).status(Status.EXPECTATION_FAILED).build();
            }
        } catch (ICSDatastoreException e) {
            APILogger.getInstance().e(CNAME, e.getMessage(), e);
            reportResponse.setMessage(e.getMessage());
            response = Response.ok(reportResponse).status(Status.NOT_FOUND).build();
        } catch (Exception e) {
            APILogger.getInstance().e(CNAME, e.getMessage(), e);
            reportResponse.setMessage(e.getMessage()) ;
            response = Response.ok(reportResponse).status(Status.EXPECTATION_FAILED).build();
        }

        return response;
    }

    private Response handleDamageReport(int incidentId, int formTypeId, MultipartBody body) throws JSONException,
            BadContentException {

        Response response = null;
        ReportServiceResponse reportResponse = new ReportServiceResponse();

        List<Attachment> attachments = body.getAllAttachments();
        String key, value;

        String storagePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.REPORTS_DR_STORAGEPATH, "/opt/data/nics/upload/report/damage/");
        String url = APIConfig.getInstance().getConfiguration().getString(APIConfig.REPORTS_DR_URL, "/data/nics/static/image-upload/report/damage/");
        String path = APIConfig.getInstance().getConfiguration().getString(APIConfig.REPORTS_DR_PATH, "https://phinics-dev.ll.mit.edu/static/image-upload/report/damage/");

        Location location = new Location(); // TODO:refactor get rid of this type, or at least
        //   refactor to be a common Location object if we're not using
        // one from a geo library
        location.setTime(-1);

        // Other necessary entities
        Incident inc = null;
        User user = null;
        Coordinate lla = new Coordinate();
        // TODO:refactor get rid of papi report, use nics Form
        //Report report = new Report();
        Form form = new Form();
        form.setIncidentid(incidentId);
        form.setFormtypeid(formTypeId);
        form.setUsersessionid(-1);
        String filename;
        String ext = ".png";
        JSONObject msg = null;
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

        for(Attachment a : attachments) {
            MediaType type = a.getContentType();

            if(type.getType().contains("text")) {
                key = a.getContentDisposition().getParameter("name");
                value = a.getObject(String.class);

                if (key.equalsIgnoreCase("incidentid")) {
                    //report.setIncidentId(Integer.parseInt(value));

                    //inc = incidentDao.getIncident(report.getIncidentId());
                    inc = incidentDao.getIncident(incidentId);
                } else if (key.equalsIgnoreCase("userid")) {
                    //location.setUserid(Integer.parseInt(value));
                    //user = db.readUser(location.getUserid());

                    // Removed this section because grabbing userid from currenusersessionid

                    //user = userDao.getUserById(location.getUserid());
                    //int usersessid = userSessionDao.getUserSessionid(location.getUserid());
                    //if(usersessid > 0 && form.getUsersessionid() < 0) {
                    //form.setUsersessionid(usersessid);
                    //}
                    //report.setSenderUserId(user.getUserId());
                } else if (key.equalsIgnoreCase("usersessionid")) {
                    //report.setUserSessionId(Integer.parseInt(value));
                    if(Integer.parseInt(value) > 0 && form.getUsersessionid() < 0) {
                        form.setUsersessionid(Integer.parseInt(value));
                    }
                    user = userDao.getUserBySessionId(Long.parseLong(value));
                    location.setUserid(user.getUserId());

                } else if (key.equalsIgnoreCase("deviceid")) {
                    location.setDeviceId(value);
                } else if (key.equalsIgnoreCase("msg")) {
                    msg = new JSONObject(value);
                } else if (key.equalsIgnoreCase("seqtime")) {
                    location.setTime(Long.parseLong(value));
                }

            } else {
                filename = Calendar.getInstance().getTimeInMillis() + "-0" + ext;
                msg.put("dr-D-image", filename);

                byte[] content = a.getObject(byte[].class);
                if (content != null) {
                    // Write to file
                    File f = new File(storagePath.concat(filename));
                    try {
                        FileUtils.writeByteArrayToFile(f, content);
                        msg.put("dr-D-image", url.concat(filename));
                        msg.put("dr-D-fullPath", path.concat(filename));
                    } catch (IOException e) {
                        throw new BadContentException("Unable to write file: " + e.getMessage());
                    }
                } else {
                    throw new BadContentException("No attachment");
                }
            }
        }

        lla.x = msg.getDouble("dr-B-propertyLongitude");
        lla.y = msg.getDouble("dr-B-propertyLatitude");
        lla.z = 0;
        location.setCourse(0.0);
        location.setSpeed(0.0);
        location.setAccuracy(0.0);

        msg.put("user", user.getUsername());
        msg.put("userfull", user.getFirstname() + " " + user.getLastname());
        if(location.getTime() < 0) {
            location.setTime(Calendar.getInstance().getTimeInMillis());
        }
        //report.setSeqTime(location.getTime());
        //report.setMessage(msg.toString());
        form.setSeqtime(location.getTime());
        form.setMessage(msg.toString());

        // Set DB orm
        if(!validLocation(lla)) {
            lla.x = inc.getLon();
            lla.y = inc.getLat();
            lla.z = 0;
        }
        location.setLocation(gf.createPoint(lla));

        // TODO:refactor don't use PhiImage, use NICS document of image type?
        Image image = new Image();
        image.setIncident(inc);
        image.setLocation(location);
        image.setUrl(msg.getString("dr-D-image"));
        image.setFullPath(msg.getString("dr-D-fullPath"));

        // Persist DB entries
        // TODO:refactor .... refactor these phi_image and phi_location objects.
		/*try {
			phiDao.persistLocation(location);
			phiDao.persistImage(image);
		} catch(Exception e) {
			APILogger.getInstance().e(CNAME, "Exception persisting location or image: " + e.getMessage());
			reportResponse.setMessage("error. Problem persisting location and image entities");
			response = Response.ok(reportResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			return response;
		}*/

        try {

            Form ret = formDao.persistForm(form);

            if(ret != null) {
                reportResponse.getReports().add(ret);
                reportResponse.setCount(1);
                reportResponse.setMessage("ok");
                response = Response.ok(reportResponse).status(Status.OK).build();

                try {
                    String topic = String.format("iweb.NICS.incident.%d.report.DMGRPT.new", form.getIncidentid());
                    notifyNewReport(topic, form);
                } catch (IOException e) {
                    APILogger.getInstance().e("ReportServiceImpl", "Exception publishing new form with type id: " +
                            form.getFormtypeid());
                    e.printStackTrace();
                }

            } else {
                reportResponse.setCount(0);
                reportResponse.setMessage("Did not successfully persist report!");
                response = Response.ok(reportResponse).status(Status.EXPECTATION_FAILED).build();
            }

        } catch (ICSDatastoreException e) {
            reportResponse.setMessage(e.getMessage()) ;
            response = Response.ok(reportResponse).status(Status.NOT_FOUND).build();
        } catch (Exception e) {
            reportResponse.setMessage(e.getMessage()) ;
            response = Response.ok(reportResponse).status(Status.EXPECTATION_FAILED).build();
        }

        return response;
    }

    private Response handleUXOReport(int incidentId, int formTypeId, MultipartBody body) throws JSONException,
            BadContentException {

        Response response = null;
        ReportServiceResponse reportResponse = new ReportServiceResponse();

        List<Attachment> attachments = body.getAllAttachments();
        String key, value;

        String storagePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.REPORTS_UXO_STORAGEPATH, "/opt/data/nics/upload/report/uxo/");
        String url = APIConfig.getInstance().getConfiguration().getString(APIConfig.REPORTS_UXO_URL, "/data/nics/static/image-upload/report/uxo/");
        String path = APIConfig.getInstance().getConfiguration().getString(APIConfig.REPORTS_UXO_PATH, "https://phinics-dev.ll.mit.edu/static/image-upload/report/uxo/");

        Location location = new Location(); // TODO:refactor get rid of this type, or at least
        //   refactor to be a common Location object if we're not using
        // one from a geo library
        location.setTime(-1);

        // Other necessary entities
        Incident inc = null;
        User user = null;
        Coordinate lla = new Coordinate();
        // TODO:refactor get rid of papi report, use nics Form
        //Report report = new Report();
        Form form = new Form();
        form.setIncidentid(incidentId);
        form.setFormtypeid(formTypeId);
        form.setUsersessionid(-1);
        String filename;
        String ext = ".png";
        JSONObject msg = null;
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

        for(Attachment a : attachments) {
            MediaType type = a.getContentType();

            if(type.getType().contains("text")) {
                key = a.getContentDisposition().getParameter("name");
                value = a.getObject(String.class);

                if (key.equalsIgnoreCase("incidentid")) {
                    //report.setIncidentId(Integer.parseInt(value));

                    //inc = incidentDao.getIncident(report.getIncidentId());
                    inc = incidentDao.getIncident(incidentId);
                } else if (key.equalsIgnoreCase("userid")) {
                    //location.setUserid(Integer.parseInt(value));
                    //user = db.readUser(location.getUserid());
                    //user = userDao.getUserById(location.getUserid());
                    //int usersessid = userSessionDao.getUserSessionid(location.getUserid());
                    //if(usersessid > 0 && form.getUsersessionid() < 0) {
                    //	form.setUsersessionid(usersessid);
                    //}
                    //report.setSenderUserId(user.getUserId());
                } else if (key.equalsIgnoreCase("usersessionid")) {
                    //report.setUserSessionId(Integer.parseInt(value));
                    if(Integer.parseInt(value) > 0 && form.getUsersessionid() < 0) {
                        form.setUsersessionid(Integer.parseInt(value));
                    }
                    user = userDao.getUserBySessionId(Long.parseLong(value));
                    location.setUserid(user.getUserId());
                } else if (key.equalsIgnoreCase("deviceid")) {
                    location.setDeviceId(value);
                } else if (key.equalsIgnoreCase("msg")) {
                    msg = new JSONObject(value);
                } else if (key.equalsIgnoreCase("seqtime")) {
                    location.setTime(Long.parseLong(value));
                }

            } else {
                filename = Calendar.getInstance().getTimeInMillis() + "-0" + ext;
                msg.put("ur-image", filename);

                byte[] content = a.getObject(byte[].class);
                if (content != null) {
                    // Write to file
                    File f = new File(storagePath.concat(filename));
                    try {
                        FileUtils.writeByteArrayToFile(f, content);
                        msg.put("ur-image", url.concat(filename));
                        msg.put("ur-fullPath", path.concat(filename));
                    } catch (IOException e) {
                        throw new BadContentException("Unable to write file: " + e.getMessage());
                    }
                } else {
                    throw new BadContentException("No attachment");
                }
            }
        }

        lla.x = msg.getDouble("ur-longitude");
        lla.y = msg.getDouble("ur-latitude");
        lla.z = 0;
        location.setCourse(0.0);
        location.setSpeed(0.0);
        location.setAccuracy(0.0);


        msg.put("user", user.getUsername());
        msg.put("userfull", user.getFirstname() + " " + user.getLastname());
        if(location.getTime() < 0) {
            location.setTime(Calendar.getInstance().getTimeInMillis());
        }
        //report.setSeqTime(location.getTime());
        //report.setMessage(msg.toString());
        form.setSeqtime(location.getTime());
        form.setMessage(msg.toString());

        // Set DB orm
        if(!validLocation(lla)) {
            lla.x = inc.getLon();
            lla.y = inc.getLat();
            lla.z = 0;
        }
        location.setLocation(gf.createPoint(lla));

        // TODO:refactor don't use PhiImage, use NICS document of image type?
        Image image = new Image();
        image.setIncident(inc);
        image.setLocation(location);
        image.setUrl(msg.getString("ur-image"));
        image.setFullPath(msg.getString("ur-fullPath"));

        // Persist DB entries
        // TODO:refactor .... refactor these phi_image and phi_location objects.
		/*try {
			phiDao.persistLocation(location);
			phiDao.persistImage(image);
		} catch(Exception e) {
			APILogger.getInstance().e(CNAME, "Exception persisting location or image: " + e.getMessage());
			reportResponse.setMessage("error. Problem persisting location and image entities");
			response = Response.ok(reportResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			return response;
		}*/

        try {
            Form ret = formDao.persistForm(form);

            if(ret != null) {
                reportResponse.getReports().add(ret);
                reportResponse.setCount(1);
                reportResponse.setMessage("ok");
                response = Response.ok(reportResponse).status(Status.OK).build();

                try {
                    String topic = String.format("iweb.NICS.incident.%d.report.UXO.new", form.getIncidentid());
                    System.out.println("DANS TOPIC : " +  topic);
                    notifyNewReport(topic, form);
                } catch (IOException e) {
                    APILogger.getInstance().e("ReportServiceImpl", "Exception publishing new form with type id: " +
                            form.getFormtypeid());
                    e.printStackTrace();
                }

            } else {
                reportResponse.setCount(0);
                reportResponse.setMessage("Did not successfully persist report!");
                response = Response.ok(reportResponse).status(Status.EXPECTATION_FAILED).build();
            }

        } catch (ICSDatastoreException e) {
            reportResponse.setMessage(e.getMessage()) ;
            response = Response.ok(reportResponse).status(Status.NOT_FOUND).build();
        } catch (Exception e) {
            reportResponse.setMessage(e.getMessage()) ;
            response = Response.ok(reportResponse).status(Status.EXPECTATION_FAILED).build();
        }

        try
        {
            Uxoreport report = new Uxoreport();
            report.setMessage(msg.toString());
            report.setIncidentid(incidentId);
            report.setLat(lla.y);
            report.setLon(lla.x);

            APILogger.getInstance().i(CNAME, "Trying to insert uxoreport");
            uxoReportDao.persistUxoreport(report);
            APILogger.getInstance().i(CNAME, "Inserted uxoreport");
        } catch (Exception e)
        {
            APILogger.getInstance().e(CNAME, "Exception trying to insert uxoreport");
            e.printStackTrace();
        }


        return response;
    }

    private boolean validLocation(Coordinate lla) {
        if(lla.x > 180 || lla.x < -180 || lla.y > 180 || lla.y < -180) {
            return false;
        }

        return true;
    }


    private void notifyNewReport(String topic, Form form) throws IOException {
        if (form != null) {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(form);
            // notifyReportCreation(form);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyReportCreation(Form form) throws IOException {
        if (form != null) {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(form);
        }
    }


    /**
     * Get Rabbit producer to send message
     * @return
     * @throws java.io.IOException
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

    @Override
    public Response getReportTypes() {
        Response response = null;
        ReportServiceResponse reportServiceResponse = new ReportServiceResponse();

        List<FormType> formTypes = EntityCacheMgr.getInstance().getFormTypes();
        if(formTypes != null) {
            reportServiceResponse.setMessage("Successfully retrieved ReportTypes");
            reportServiceResponse.setTypes(formTypes);
            reportServiceResponse.setCount(formTypes.size());
            response = Response.ok(reportServiceResponse).status(Status.OK).build();
        } else {
            reportServiceResponse.setMessage("Unable to retrieve ReportTypes");
            response = Response.ok(reportServiceResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }
}

