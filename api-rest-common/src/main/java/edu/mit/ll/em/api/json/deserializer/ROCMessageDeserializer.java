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
package edu.mit.ll.em.api.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import edu.mit.ll.em.api.rs.model.ROCMessageUnused;
import edu.mit.ll.em.api.rs.model.ROCMessage;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ROCMessageDeserializer extends StdDeserializer<ROCMessage>  {
    private static final SimpleDateFormat dateCreatedFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public ROCMessageDeserializer() {
        this(null);
    }

    public ROCMessageDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public ROCMessage deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
        JsonNode jsonNode = jsonParser.getCodec().readTree(jsonParser);
        String dateCreatedStr = jsonNode.findValue("datecreated").asText();
        Date dateCreated = null;
        try {
            dateCreated = StringUtils.isBlank(dateCreatedStr) ? null : dateCreatedFormat.parse(dateCreatedStr);
        } catch(ParseException e) {
            dateCreated = null;
        }
        JsonNode reportNode = jsonNode.findValue("report");
        if(reportNode == null) {
            ROCMessage rocMessage = new ROCMessage();
            rocMessage.setDateCreated(dateCreated);
            return rocMessage;
        }

        String reportType = reportNode.get("reportType") == null ? null : reportNode.get("reportType").asText();
        String dateStr = reportNode.get("date") == null ? null : reportNode.get("date").asText();
        Date date;

        try {
            date = StringUtils.isBlank(dateStr) ? null : dateFormat.parse(dateStr);
        } catch(ParseException e) {
            date = null;
        }
        String orgPrefix = reportNode.get("orgPrefix") == null ? null : reportNode.get("orgPrefix").asText();
        String startTime = reportNode.get("startTime") == null ? null : reportNode.get("startTime").asText();
        String location = reportNode.get("location") == null ? null : reportNode.get("location").asText();
        String generalLocation = reportNode.get("generalLocation") == null ? null : reportNode.get("generalLocation").asText();
        String county = reportNode.get("county") == null ? null : reportNode.get("county").asText();
        String additionalAffectedCounties = reportNode.get("additionalAffectedCounties") == null ? null : reportNode.get("additionalAffectedCounties").asText();
        String street = reportNode.get("street") == null ? null : reportNode.get("street").asText();
        String crossStreet = reportNode.get("crossStreet") == null ? null : reportNode.get("crossStreet").asText();
        String nearestCommunity = reportNode.get("nearestCommunity") == null ? null : reportNode.get("nearestCommunity").asText();
        String milesFromNearestCommunity = reportNode.get("milesFromNearestCommunity") == null ? null : reportNode.get("milesFromNearestCommunity").asText();
        String directionFromNearestCommunity = reportNode.get("directionFromNearestCommunity") == null ? null : reportNode.get("directionFromNearestCommunity").asText();
        String state = reportNode.get("state") == null ? null : reportNode.get("state").asText();
        String sra = reportNode.get("sra") == null ? null : reportNode.get("sra").asText();
        String dpa = reportNode.get("dpa") == null ? null : reportNode.get("dpa").asText();
        String scope = reportNode.get("scope") == null ? null : reportNode.get("scope").asText();
        String spreadRate = reportNode.get("spreadRate") == null ? null : reportNode.get("spreadRate").asText();
        String percentageContained = reportNode.get("percentContained") == null ? null : reportNode.get("percentContained").asText();
        String evacuations = reportNode.get("evacuations") == null ? null : reportNode.get("evacuations").asText();

        JsonNode incidentTypesRoot = reportNode.get("incidentTypes") == null ? null : reportNode.get("incidentTypes");
        JsonNode incidentTypesChild = (incidentTypesRoot == null) || incidentTypesRoot.get("incidenttype") == null ? null : incidentTypesRoot.get("incidenttype");
        List<String> incidentTypesLst =  incidentTypesChild==null ? null : getJsonNdeAsList(incidentTypesChild);


        JsonNode evacuationsInProgress = reportNode.get("evacuationsInProgress") == null ? null : reportNode.get("evacuationsInProgress");
        JsonNode evacuationsArray = (evacuationsInProgress == null) || evacuationsInProgress.get("evacuations") == null ? null : evacuationsInProgress.get("evacuations");
        List<String> evacuationsList =  evacuationsArray==null ? null : getJsonNdeAsList(evacuationsArray);

        String structuresThreat = reportNode.get("structuresThreat") == null ? null : reportNode.get("structuresThreat").asText();
        JsonNode structuresThreatInProgress = reportNode.get("structuresThreatInProgress") == null ? null : reportNode.get("structuresThreatInProgress");
        JsonNode structuresThreats = (structuresThreatInProgress == null) || structuresThreatInProgress.get("structuresThreat") == null ? null : structuresThreatInProgress.get("structuresThreat");
        List<String> structuresThreatsLst =  structuresThreats==null ? null : getJsonNdeAsList(structuresThreats);

        String infrastructuresThreat = reportNode.get("evacuations") == null ? null : reportNode.get("infrastructuresThreat").asText();
        JsonNode infrastructuresThreatInProgress = reportNode.get("infrastructuresThreatInProgress") == null ? null : reportNode.get("infrastructuresThreatInProgress");
        JsonNode infrastructuresThreats = (infrastructuresThreatInProgress == null) || infrastructuresThreatInProgress.get("infrastructuresThreat") == null ? null : infrastructuresThreatInProgress.get("infrastructuresThreat");
        List<String> infrastructuresThreatsLst =  structuresThreats==null ? null : getJsonNdeAsList(infrastructuresThreats);

        JsonNode resourcesAssignedRoot = reportNode.get("resourcesAssigned") == null ? null : reportNode.get("resourcesAssigned");
        JsonNode resourcesAssignedChild = (resourcesAssignedRoot == null) || resourcesAssignedRoot.get("resourcesAssigned") == null ? null : resourcesAssignedRoot.get("resourcesAssigned");
        List<String> resourcesAssignedLst =  resourcesAssignedChild==null ? null : getJsonNdeAsList(resourcesAssignedChild);

        String jurisdiction = reportNode.get("jurisdiction") == null ? null : reportNode.get("jurisdiction").asText();

        JsonNode temperatureJsonNode = reportNode.get("temperature");
        Double temperature = (temperatureJsonNode == null || temperatureJsonNode.isNull() || temperatureJsonNode.asText().equals("null") || StringUtils.isBlank(temperatureJsonNode.asText())) ? null : Double.parseDouble(temperatureJsonNode.asText());

        JsonNode relHumidityJsonNode = reportNode.get("relHumidity");
        Float relHumidity = (relHumidityJsonNode == null || relHumidityJsonNode.isNull() || relHumidityJsonNode.asText().equals("null") || StringUtils.isBlank(relHumidityJsonNode.asText()) ) ? null : Float.parseFloat(relHumidityJsonNode.asText());

        JsonNode windSpeedJsonNode = reportNode.get("windSpeed");
        Float windSpeed = (windSpeedJsonNode == null || windSpeedJsonNode.isNull() || windSpeedJsonNode.asText().equals("null") || StringUtils.isBlank(windSpeedJsonNode.asText())) ? null : Float.parseFloat(windSpeedJsonNode.asText());

        JsonNode windGustJsonNode = reportNode.get("windGust");
        Double windGust = (windGustJsonNode == null || windGustJsonNode.isNull() || windGustJsonNode.asText().equals("null") || StringUtils.isBlank(windGustJsonNode.asText())) ? null : Double.parseDouble(windGustJsonNode.asText());

        JsonNode windDirectionJsonNode = reportNode.get("windDirection");
        String windDirection = (windDirectionJsonNode == null || windDirectionJsonNode.isNull() || StringUtils.isBlank(windDirectionJsonNode.asText())) ? null : windDirectionJsonNode.asText();

        JsonNode fuelTypesJSN = reportNode.get("fuelTypes") == null ? null : reportNode.get("fuelTypes");
        List<String> fuelTypes = fuelTypesJSN==null ? null : getJsonNdeAsList(fuelTypesJSN);

        JsonNode otherSignificantInfoJSON = reportNode.get("otherSignificantInfo") == null ? null : reportNode.get("otherSignificantInfo");
        List<String> otherSignificantInfo = otherSignificantInfoJSON == null ? null : getJsonNdeAsList(otherSignificantInfoJSON);

        String otherFuelTypes = reportNode.get("otherFuelTypes") == null ? null : reportNode.get("otherFuelTypes").asText();
        String otherResourcesAssigned = reportNode.get("otherResourcesAssigned") == null ? null : reportNode.get("otherResourcesAssigned").asText();

        String otherEvacuations = reportNode.get("otherEvacuations") == null ? null : reportNode.get("otherEvacuations").asText();
        String otherStructuresThreat = reportNode.get("otherStructuresThreat") == null ? null : reportNode.get("otherStructuresThreat").asText();
        String otherInfrastructuresThreat = reportNode.get("otherInfrastructuresThreat") == null ? null : reportNode.get("otherInfrastructuresThreat").asText();
        String otherOtherSignificantInfo = reportNode.get("otherOtherSignificantInfo") == null ? null : reportNode.get("otherOtherSignificantInfo").asText();

        return new ROCMessage(dateCreated, reportType, date, startTime,
                location, generalLocation, county, additionalAffectedCounties, street, crossStreet, nearestCommunity, milesFromNearestCommunity, directionFromNearestCommunity, state,
                sra, dpa, jurisdiction, temperature, relHumidity, windSpeed, windGust, windDirection, percentageContained, scope, spreadRate,
                fuelTypes, otherSignificantInfo, otherFuelTypes, evacuations,evacuationsList, structuresThreat,
                structuresThreatsLst, infrastructuresThreat, infrastructuresThreatsLst, resourcesAssignedLst, otherResourcesAssigned, incidentTypesLst,
                otherEvacuations, otherStructuresThreat, otherInfrastructuresThreat, otherOtherSignificantInfo, orgPrefix);
    }

    private List<String> getJsonNdeAsList(JsonNode jsonNode){
        ArrayList<String> list = new ArrayList<String>();
        if(jsonNode.isArray()){
            for(JsonNode node : jsonNode){
                list.add(node.asText());
            }
        }else{
            list.add(jsonNode.asText());
        }
        return list;
    }


}
