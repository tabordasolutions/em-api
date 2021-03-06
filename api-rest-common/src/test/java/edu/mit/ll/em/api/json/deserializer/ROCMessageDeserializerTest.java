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

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mit.ll.em.api.rs.model.ROCMessage;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ROCMessageDeserializerTest {

    private SimpleDateFormat dateCreatedFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String orgPrefix = "CA o1";
    private static final SimpleDateFormat startDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private String dateCreatedStr = "2018-09-10 10:32:16";
    private String reportType = "FINAL";
    private String incidentType = "Planned event";
    private String startDateStr = "2018-09-10T17:10:49.881Z";
    private String county = "sacramento";
    private String additionalAffectedCounties = "addtl counties";
    private String state = "state";
    private String location = "88 hello dr, Folsom, ca";
    private String generalLocation = "20 miles south of x";
    private String sra = "SRA";
    private String dpa = "DPA";
    private String jurisdiction = "contract county";
    private Double temperature = 92.0;
    private Float relHumidity = 10.0f;
    private Float windSpeed = 8.0f;
    private Double windGust = 27.22;
    private String windDirection = "South";
    private String fuelTypes = "GRASS";
//    private String fuelTypes = "\"fuelTypes\":[\"GRASS\",\"BUSH\"]";
    private List<String> fuelTypesList = Arrays.asList(new String[] {"GRASS"});
    private String otherFuelTypes = "other types";
    private String evacuations = "Yes";
    private String structuresThreat = "Yes";
    private String infrastructuresThreat = "Yes";

    // SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HHmm");
    String rocStartTime = null;

//    private String evacuationsInProgress = "Evacuation orders in place";
    private String evacuationsInProgress = "Evacuation orders in place,Evacuation center has been established,Evaculation orders remain in place";
    public static ArrayList<String> evacuationsList;

    static {
        evacuationsList = new ArrayList<String>();
        evacuationsList.add("Evacuation orders in place");
        evacuationsList.add("Evacuation center has been established");
        evacuationsList.add("Evaculation orders remain in place");

    }
    private List<String> structuresThreats = Arrays.asList(new String[] {"Structures threatened","Damage inspection is on going"});
    private List<String> infrastructuresThreats = Arrays.asList(new String[] {"mmediate structure threat","Major power lines are threatened"});

    private String evacuationsProjgressJson = "{\"evacuations\":\"Evacuation orders in place\"}";
    private String evacuationsProgressJsonInArray = "{\"evacuations\":[\"Evacuation orders in place\",\"Evacuation center has been established\",\"Evaculation orders remain in place\"]}";
    private String structThreatProgressJsonInArray = "{\"structuresThreat\":[\"Structures threatened\",\"Damage inspection is on going\"]}";
    private String infrastructProgressJsonInArray = "{\"infrastructuresThreat\":[\"Immediate structure threat\",\"Major power lines are threatened\"]}";
    private Date dateCreated, startDate;


   private String rocJson = "{\"message\":{\"datecreated\":\"" + dateCreatedStr + "\"" +
            ",\"report\":{\"reportType\":\"" + reportType + "\"" +
           ",\"orgPrefix\":\"" + orgPrefix + "\"" +
            ",\"date\":\"" + startDateStr +
            "\",\"starttime\":\"" + rocStartTime + "\"" +
            ",\"formTypeId\":1,\"reportBy\":\"sneha nagole\",\"email\":\"sneha.nagole@tabordasolutions.com\"" +
            ",\"additionalAffectedCounties\":\"" + additionalAffectedCounties + "\",\"county\":\"" + county + "\"" +
            ",\"state\":\"" + state + "\"" +
            ",\"location\":\"" + location + "\"" +
            ",\"generalLocation\":\"" + generalLocation + "\"" +
            ",\"sra\":\"" + sra + "\"" +
            ",\"dpa\":\"" + dpa + "\"" +
            ",\"jurisdiction\":\"" + jurisdiction + "\"" +
            ",\"incidentType\":\"" + incidentType + "\"" +
            ",\"fuelTypes\":\"" + fuelTypes + "\"" +
            ",\"otherFuelTypes\":\"" + otherFuelTypes + "\"" +
            ",\"scope\":\"2\",\"spreadRate\":\"2\",\"percentContained\":\"1\"" +
            ",\"temperature\":\"" + temperature + "\"" +
            ",\"relHumidity\":\"" + relHumidity + "\"" +
            ",\"windSpeed\":\"" + windSpeed + "\"" +
            ",\"windGust\":\"" + windGust + "\"" +
            ",\"windDirection\":\"" + windDirection + "\"" +/* "\",\"predictedWeather\":\"mild\",\"evacuations\":\"0\",\"structuresThreat\":\"0\",\"infrastructuresThreat\":\"0\",\"comments\":\"lkjljklj - Final 1\",\"simplifiedEmail\":true,\"airAttack\":\"none\"" + */
            ",\"evacuations\":\"" + evacuations + "\"" +
            ",\"evacuationsInProgress\":" + evacuationsProgressJsonInArray +
           ",\"structuresThreat\":\"" + structuresThreat + "\"" +
           ",\"structuresThreatInProgress\":" + structThreatProgressJsonInArray +
           ",\"infrastructuresThreat\":\"" + infrastructuresThreat + "\"" +
           ",\"infrastructuresThreatInProgress\":" + infrastructProgressJsonInArray +
           "}}}\"";
   /*
   private String rocJson = "{\"message\":{\"datecreated\":\"" + dateCreatedStr + "\"," +
           "\"report\":{\"reportType\":\"FINAL\",\"county\":\"San Bernardino\",\"additionalAffectedCounties\":\""+additionalAffectedCounties+ "\"," +
           "\"date\":\"" + startDateStr+ "\"," +
           "\"starttime\":\""+ startDateStr + "\""+ ","+"\"location\":\"Unnamed Road, California, USA\",\"dpa\":\"State\",\"sra\":\"SRA\",\"jurisdiction\":\"Jurisdiction\",\"incidentTypes\":[],\"scope\":\"40\",\"spreadRate\":null,\"fuelTypes\":\"Bush\",\"otherFuelTypes\":null,\"percentContained\":\"70\",\"temperature\":null,\"relHumidity\":null,\"windSpeed\":null,\"windDirection\":null,\"evacuations\":\"Yes\"," +
           "\"evacuationsInProgress\":{\"evacuations\":\"Evacuation orders in place\"}," +
           "\"structuresThreat\":\"Yes\"," +
           "\"structuresThreatInProgress\":{\"structuresThreat\":\"Structures threatened\"}," +
           "\"infrastructuresThreat\":\"Yes\"," +
           "\"infrastructuresThreatInProgress\":{\"infrastructuresThreat\":\"Immediate structure threat, evacuation in place\"}," +
           "\"otherThreatsAndEvacuations\":\"No\"," +
           "\"otherThreatsAndEvacuationsInProgress\":\"dfasdf\"," +
           "\"calfireIncident\":null," +
           "\"resourcesAssigned\":{\"resourcesAssigned\":\"CAL FIRE Air Resources Assigned\"}," +
           "\"email\":\"tester@tabordasolutions.net\"," +
           "\"simplifiedEmail\":true," +
           "\"latitudeAtROCSubmission\":34.7764148004392," +
           "\"longitudeAtROCSubmission\":-114.95675526559353," +
           "\"weatherDataAvailable\":false}" +
           "}}}\""; */

    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setup() throws ParseException {
        dateCreated = dateCreatedFormat.parse(dateCreatedStr);
        startDate = startDateFormat.parse(startDateStr);
    }

    @Test
    public void deserializesValidROCMessageJsonSuccessfull() throws IOException {
        ROCMessage rocMessage = objectMapper.readValue(rocJson, ROCMessage.class);
        assertEquals(rocMessage.getDateCreated(), dateCreated);
        assertEquals(rocMessage.getReportType(), reportType);
        assertEquals(rocMessage.getAdditionalAffectedCounties(), additionalAffectedCounties);
        assertEquals(rocMessage.getGeneralLocation(), generalLocation);
        assertEquals(rocMessage.getFuelTypes(), fuelTypesList);
        assertEquals(rocMessage.getOtherFuelTypes(), otherFuelTypes);
        assertEquals(rocMessage.getDate(), startDate);
        assertEquals(rocMessage.getOrgPrefix(), orgPrefix);
        assertEquals(rocMessage.getStartTime(), rocStartTime);
        assertEquals(rocMessage.getLocation(), location);
        assertEquals(rocMessage.getCounty(), county);
        assertEquals(rocMessage.getState(), state);
        assertEquals(rocMessage.getGeneralLocation(), generalLocation);
        assertEquals(rocMessage.getSra(), sra);
        assertEquals(rocMessage.getDpa(), dpa);
        assertEquals(rocMessage.getJurisdiction(), jurisdiction);
        assertEquals(rocMessage.getFuelTypes(), fuelTypesList);
        assertEquals(rocMessage.getOtherFuelTypes(), otherFuelTypes);

        assertEquals(rocMessage.getTemperature(), temperature);
        assertEquals(rocMessage.getRelHumidity(), relHumidity);
        assertEquals(rocMessage.getWindSpeed(), windSpeed);
        assertEquals(rocMessage.getWindGust(), windGust);
        assertEquals(rocMessage.getWindDirection(), windDirection);
        assertEquals(rocMessage.getEvacuations(), evacuations);
        assertEquals(rocMessage.getEvacuationsList(), evacuationsList);
    }

    @Test
    public void deserializesROCMessageWithEmptyReportSection() throws IOException
    {
        String rocJsonWithEmptyReportSection  = "{\"message\":{\"datecreated\":\"" + dateCreatedStr + "\"" +
                                        ",\"report\":{}}}\"";
        ROCMessage rocMessage = objectMapper.readValue(rocJsonWithEmptyReportSection, ROCMessage.class);
        assertEquals(rocMessage.getDateCreated(), dateCreated);
        assertNull(rocMessage.getReportType());
        assertNull(rocMessage.getAdditionalAffectedCounties());
        assertNull(rocMessage.getGeneralLocation());
        assertNull(rocMessage.getFuelTypes());
        assertNull(rocMessage.getOtherFuelTypes());
        assertNull(rocMessage.getDate());
        assertNull(rocMessage.getStartTime());
        assertNull(rocMessage.getLocation());
        assertNull(rocMessage.getCounty());
        assertNull(rocMessage.getState());
        assertNull(rocMessage.getGeneralLocation());
        assertNull(rocMessage.getSra());
        assertNull(rocMessage.getDpa());
        assertNull(rocMessage.getJurisdiction());
        assertNull(rocMessage.getFuelTypes());
        assertNull(rocMessage.getOtherFuelTypes());

        assertNull(rocMessage.getTemperature());
        assertNull(rocMessage.getRelHumidity());
        assertNull(rocMessage.getWindSpeed());
        assertNull(rocMessage.getWindGust());
        assertNull(rocMessage.getWindDirection());
    }

    @Test
    public void deserializeROCMessageCanHandelEmptyStrings() throws IOException {
        String rocJsonWithEmptyString = "{\"message\":{\"datecreated\":\"" + dateCreatedStr + "\"" +
                ",\"report\":{\"reportType\":\"" + reportType + "\"" +
                ",\"orgPrefix\":\"" + orgPrefix + "\"" +
                ",\"date\":\"" + startDateStr +
                "\",\"starttime\":\"" + rocStartTime + "\"" +
                ",\"formTypeId\":1,\"reportBy\":\"sneha nagole\",\"email\":\"sneha.nagole@tabordasolutions.com\"" +
                ",\"additionalAffectedCounties\":\"" + additionalAffectedCounties + "\",\"county\":\"" + county + "\"" +
                ",\"state\":\"" + state + "\"" +
                ",\"location\":\"" + location + "\"" +
                ",\"generalLocation\":\"" + generalLocation + "\"" +
                ",\"sra\":\"" + sra + "\"" +
                ",\"dpa\":\"" + dpa + "\"" +
                ",\"jurisdiction\":\"" + jurisdiction + "\"" +
                ",\"incidentType\":\"" + incidentType + "\"" +
                ",\"fuelTypes\":\"" + fuelTypes + "\"" +
//                "," + fuelTypes +
                ",\"otherFuelTypes\":\"" + otherFuelTypes + "\"" +
                ",\"scope\":\"2\",\"spreadRate\":\"2\",\"percentContained\":\"1\"" +
                ",\"temperature\":\"\"" +
                ",\"relHumidity\":\"\"" +
                ",\"windSpeed\":\"\"" +
                ",\"windGust\":\"\"" +
                ",\"windDirection\":\"" + windDirection + "\",\"predictedWeather\":\"mild\",\"evacuations\":\"0\",\"structuresThreat\":\"0\",\"infrastructuresThreat\":\"0\",\"comments\":\"lkjljklj - Final 1\",\"simplifiedEmail\":true,\"airAttack\":\"none\"" +
                "}}}\"";
        ROCMessage rocMessage = objectMapper.readValue(rocJsonWithEmptyString, ROCMessage.class);
        assertNull(rocMessage.getTemperature());
        assertNull(rocMessage.getRelHumidity());
        assertNull(rocMessage.getWindSpeed());
        assertNull(rocMessage.getWindGust());
    }

    @Test
    public void deserializeROCMessageCanHandelNullStrings() throws IOException {
        String rocJsonWithNullString = "{\"message\":{\"datecreated\":\"" + dateCreatedStr + "\"" +
                ",\"report\":{\"reportType\":\"" + reportType + "\"" +
                ",\"orgPrefix\":\"" + orgPrefix + "\"" +
                ",\"date\":\"" + startDateStr +
                "\",\"starttime\":\"" + rocStartTime + "\"" +
                ",\"formTypeId\":1,\"reportBy\":\"sneha nagole\",\"email\":\"sneha.nagole@tabordasolutions.com\"" +
                ",\"additionalAffectedCounties\":\"" + additionalAffectedCounties + "\",\"county\":\"" + county + "\"" +
                ",\"state\":\"" + state + "\"" +
                ",\"location\":\"" + location + "\"" +
                ",\"generalLocation\":\"" + generalLocation + "\"" +
                ",\"sra\":\"" + sra + "\"" +
                ",\"dpa\":\"" + dpa + "\"" +
                ",\"jurisdiction\":\"" + jurisdiction + "\"" +
                ",\"incidentType\":\"" + incidentType + "\"" +
                ",\"fuelTypes\":\"" + fuelTypes + "\"" +
                ",\"otherFuelTypes\":\"" + otherFuelTypes + "\"" +
                ",\"scope\":\"2\",\"spreadRate\":\"2\",\"percentContained\":\"1\"" +
                ",\"temperature\":\"null\"" +
                ",\"relHumidity\":\"null\"" +
                ",\"windSpeed\":\"null\"" +
                ",\"windGust\":\"null\"" +
                ",\"windDirection\":\"" + windDirection + "\",\"predictedWeather\":\"mild\",\"evacuations\":\"0\",\"structuresThreat\":\"0\",\"infrastructuresThreat\":\"0\",\"comments\":\"lkjljklj - Final 1\",\"simplifiedEmail\":true,\"airAttack\":\"none\"" +
                "}}}\"";
        ROCMessage rocMessage = objectMapper.readValue(rocJsonWithNullString, ROCMessage.class);
        assertNull(rocMessage.getTemperature());
        assertNull(rocMessage.getRelHumidity());
        assertNull(rocMessage.getWindSpeed());
        assertNull(rocMessage.getWindGust());
    }

    @Test
    public void deserializeROCMessageCanHandelNulls() throws IOException {
        String rocJsonWithEmptyString = "{\"message\":{\"datecreated\":\"" + dateCreatedStr + "\"" +
                ",\"report\":{\"reportType\":\"" + reportType + "\"" +
                ",\"orgPrefix\":\"" + orgPrefix + "\"" +
                ",\"date\":\"" + startDateStr +
                "\",\"starttime\":\"" + rocStartTime + "\"" +
                ",\"formTypeId\":1,\"reportBy\":\"sneha nagole\",\"email\":\"sneha.nagole@tabordasolutions.com\"" +
                ",\"additionalAffectedCounties\":\"" + additionalAffectedCounties + "\",\"county\":\"" + county + "\"" +
                ",\"state\":\"" + state + "\"" +
                ",\"location\":\"" + location + "\"" +
                ",\"generalLocation\":\"" + generalLocation + "\"" +
                ",\"sra\":\"" + sra + "\"" +
                ",\"dpa\":\"" + dpa + "\"" +
                ",\"jurisdiction\":\"" + jurisdiction + "\"" +
                ",\"incidentType\":\"" + incidentType + "\"" +
                ",\"fuelTypes\":\"" + fuelTypes + "\"" +
//                "," + fuelTypes +
                ",\"additionalFuelTypes\":\"" + otherFuelTypes + "\"" +
                ",\"scope\":\"2\",\"spreadRate\":\"2\",\"percentContained\":\"1\"" +
                ",\"temperature\": null" +
                ",\"relHumidity\": null" +
                ",\"windSpeed\": null" +
                ",\"windGust\": null" +
                ",\"windDirection\":\"" + windDirection + "\",\"predictedWeather\":\"mild\",\"evacuations\":\"0\",\"structuresThreat\":\"0\",\"infrastructuresThreat\":\"0\",\"comments\":\"lkjljklj - Final 1\",\"simplifiedEmail\":true,\"airAttack\":\"none\"" +
                "}}}\"";
        ROCMessage rocMessage = objectMapper.readValue(rocJsonWithEmptyString, ROCMessage.class);
        assertNull(rocMessage.getTemperature());
        assertNull(rocMessage.getRelHumidity());
        assertNull(rocMessage.getWindSpeed());
        assertNull(rocMessage.getWindGust());
    }

    @Test
    public void deserializerReturnsROCMessageWithBlankReportFields() throws IOException {
        String rocJson = "{\"message\":{\"datecreated\":\"" + dateCreatedStr + "\"}}";
        ROCMessage rocMessage = objectMapper.readValue(rocJson, ROCMessage.class);
        assertEquals(rocMessage.getDateCreated(), dateCreated);
    }
}
