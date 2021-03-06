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
package edu.mit.ll.em.api.rs.model.builder;

import java.text.SimpleDateFormat;
import java.util.*;

import edu.mit.ll.em.api.rs.model.*;
import edu.mit.ll.em.api.rs.model.Location;

import edu.mit.ll.nics.common.entity.*;
import edu.mit.ll.nics.common.entity.DirectProtectionArea;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ROCMessageBuilderTest {

    private String additionalAffectedCounties = "name";
    private String reportType = "UPDATE";
//    private String fuelTypes = "cause";
    private List<String> fuelTypes = Arrays.asList(new String[] {"GRASS", "BUSH"});
    private List<String> otherSignificantInfo = Arrays.asList(new String[] {
            "Extensive mop up in oak woodlands",
            "Ground resources continue to mop-up and strengthen control line",
            "Suppression repair is under way",
            "Fire is in remote location with difficult access",
            "Access and terrain continue to hamper control efforts",
            "Short range spotting causing erratic fire behavior",
            "Long range spotting observed"
    });
    private List<String> incidentTypes = Arrays.asList(new String[] {
            "Blizzard",
            "Oil Spill"
    });

    private String additionalFuelTypes = "other fuel types";
    private String street = "";
    private String crossStreet = "";
    private String nearestCommunity = "";
    private String milesFromNearestCommunity = "";
    private String directionFromNearestCommunity = "";
    private String generalLocation = "5 miles from xy";
    private Date startDateTime = new Date();

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HHmm");
    String rocStartTime = simpleDateFormat.format(startDateTime);

    private String sra = "sra";
    private Jurisdiction jurisdiction = new Jurisdiction("sra", new DirectProtectionArea("dpa", "contract county", "unitid", "respondid"));

    private Weather weather = new Weather("objectId", "-123, 098", 78.9, 10.0f, 27.22,214.0, 2.3f, "OK", 10.0);
    private Location location = new Location("county", "state", "000 exact st, xm city, ca, USA, 90000");

    @Test
    public void buildsROCMessageWithGivenReportDetailsAndLeavesOtherFieldsBlank() {
        ROCMessage rocMessage = new ROCMessageBuilder().buildReportDetails(reportType, additionalAffectedCounties, street, crossStreet, nearestCommunity, milesFromNearestCommunity, directionFromNearestCommunity, generalLocation, fuelTypes, additionalFuelTypes, otherSignificantInfo, incidentTypes)
                .build();
        assertEquals(reportType, rocMessage.getReportType());
        assertEquals(additionalAffectedCounties, rocMessage.getAdditionalAffectedCounties());
        assertEquals(street, rocMessage.getStreet());
        assertEquals(crossStreet, rocMessage.getCrossStreet());
        assertEquals(nearestCommunity, rocMessage.getNearestCommunity());
        assertEquals(milesFromNearestCommunity, rocMessage.getMilesFromNearestCommunity());
        assertEquals(directionFromNearestCommunity, rocMessage.getDirectionFromNearestCommunity());
        assertEquals(generalLocation, rocMessage.getGeneralLocation());
        assertEquals(fuelTypes, rocMessage.getFuelTypes());
        assertEquals(additionalFuelTypes, rocMessage.getOtherFuelTypes());
        assertEquals(otherSignificantInfo, rocMessage.getOtherSignificantInfo());
        assertEquals(incidentTypes, rocMessage.getIncidentTypes());

        assertNull(rocMessage.getDateCreated());
        assertNull(rocMessage.getDate());
        assertNull(rocMessage.getStartTime());

        assertNull(rocMessage.getLocation());
        assertNull(rocMessage.getCounty());
        assertNull(rocMessage.getState());
        assertNull(rocMessage.getSra());
        assertNull(rocMessage.getDpa());
        assertNull(rocMessage.getJurisdiction());
        assertNull(rocMessage.getTemperature());
        assertNull(rocMessage.getRelHumidity());
        assertNull(rocMessage.getWindSpeed());
        assertNull(rocMessage.getWindGust());
        assertNull(rocMessage.getWindDirection());
    }

    @Test
    public void buildsROCMessageWithGivenReportDatesAndLeavesOtherFieldsBlank() {
        ROCMessage rocMessage = new ROCMessageBuilder().buildReportDates(startDateTime, startDateTime, rocStartTime)
                .build();
        assertEquals(startDateTime, rocMessage.getDateCreated());
        assertEquals(startDateTime, rocMessage.getDate());
        assertEquals(rocStartTime, rocMessage.getStartTime());

        assertNull(rocMessage.getReportType());
        assertNull(rocMessage.getAdditionalAffectedCounties());
        assertNull(rocMessage.getStreet());
        assertNull(rocMessage.getCrossStreet());
        assertNull(rocMessage.getNearestCommunity());
        assertNull(rocMessage.getMilesFromNearestCommunity());
        assertNull(rocMessage.getDirectionFromNearestCommunity());
        assertNull(rocMessage.getGeneralLocation());
        assertNull(rocMessage.getFuelTypes());
        assertNull(rocMessage.getOtherFuelTypes());

        assertNull(rocMessage.getLocation());
        assertNull(rocMessage.getCounty());
        assertNull(rocMessage.getState());
        assertNull(rocMessage.getSra());
        assertNull(rocMessage.getDpa());
        assertNull(rocMessage.getJurisdiction());
        assertNull(rocMessage.getTemperature());
        assertNull(rocMessage.getRelHumidity());
        assertNull(rocMessage.getWindSpeed());
        assertNull(rocMessage.getWindGust());
        assertNull(rocMessage.getWindDirection());
    }

    @Test
    public void buildsROCMessageWithGivenROCLocationBasedData() {
        ROCLocationBasedData rocLocationBasedData = new ROCLocationBasedDataBuilder()
                .buildLocationData(location)
                .buildJurisdictionData(jurisdiction)
                .buildWeatherData(weather)
                .build();
        ROCMessage rocMessage = new ROCMessageBuilder().buildLocationBasedData(rocLocationBasedData).build();
        assertEquals(rocLocationBasedData.getLocation(), rocMessage.getLocation());
        assertEquals(rocLocationBasedData.getCounty(), rocMessage.getCounty());
        assertEquals(rocLocationBasedData.getState(), rocMessage.getState());
        assertEquals(rocLocationBasedData.getSra(), rocMessage.getSra());
        assertEquals(rocLocationBasedData.getDpa(), rocMessage.getDpa());
        assertEquals(rocLocationBasedData.getJurisdiction(), rocMessage.getJurisdiction());
        assertEquals(rocLocationBasedData.getTemperature(), rocMessage.getTemperature());
        assertEquals(rocLocationBasedData.getRelHumidity(), rocMessage.getRelHumidity());
        assertEquals(rocLocationBasedData.getWindSpeed(), rocMessage.getWindSpeed());
        assertEquals(rocLocationBasedData.getWindGust(), rocMessage.getWindGust());
        assertEquals(rocLocationBasedData.getWindDirection(), rocMessage.getWindDirection());

        assertNull(rocMessage.getReportType());
        assertNull(rocMessage.getAdditionalAffectedCounties());
        assertNull(rocMessage.getStreet());
        assertNull(rocMessage.getCrossStreet());
        assertNull(rocMessage.getNearestCommunity());
        assertNull(rocMessage.getMilesFromNearestCommunity());
        assertNull(rocMessage.getDirectionFromNearestCommunity());
        assertNull(rocMessage.getGeneralLocation());
        assertNull(rocMessage.getFuelTypes());
        assertNull(rocMessage.getOtherFuelTypes());

        assertNull(rocMessage.getDateCreated());
        assertNull(rocMessage.getDate());
        assertNull(rocMessage.getStartTime());
    }

    @Test
    public void buildsROCMessageWithLocationBasedDataFieldsLeftBlankGivenNullROCLocationBasedData() {
        ROCMessage rocMessage = new ROCMessageBuilder().buildLocationBasedData(null).build();
        assertNull(rocMessage.getLocation());
        assertNull(rocMessage.getCounty());
        assertNull(rocMessage.getState());
        assertNull(rocMessage.getSra());
        assertNull(rocMessage.getDpa());
        assertNull(rocMessage.getJurisdiction());
        assertNull(rocMessage.getTemperature());
        assertNull(rocMessage.getRelHumidity());
        assertNull(rocMessage.getWindSpeed());
        assertNull(rocMessage.getWindGust());
        assertNull(rocMessage.getWindDirection());
    }

    @Test
    public void buildsROCMessageWithGivenReportDetailsDatesAndLocationBasedData() {
        ROCLocationBasedData rocLocationBasedData = new ROCLocationBasedDataBuilder()
                .buildLocationData(location)
                .buildJurisdictionData(jurisdiction)
                .buildWeatherData(weather)
                .build();

        ROCMessage rocMessage = new ROCMessageBuilder()
                .buildReportDetails(reportType, additionalAffectedCounties, street, crossStreet, nearestCommunity, milesFromNearestCommunity, directionFromNearestCommunity, generalLocation, fuelTypes, additionalFuelTypes, otherSignificantInfo, incidentTypes)
                .buildReportDates(startDateTime, startDateTime, rocStartTime)
                .buildLocationBasedData(rocLocationBasedData).build();

        assertEquals(reportType, rocMessage.getReportType());
        assertEquals(additionalAffectedCounties, rocMessage.getAdditionalAffectedCounties());
        assertEquals(street, rocMessage.getStreet());
        assertEquals(crossStreet, rocMessage.getCrossStreet());
        assertEquals(nearestCommunity, rocMessage.getNearestCommunity());
        assertEquals(milesFromNearestCommunity, rocMessage.getMilesFromNearestCommunity());
        assertEquals(directionFromNearestCommunity, rocMessage.getDirectionFromNearestCommunity());
        assertEquals(generalLocation, rocMessage.getGeneralLocation());
        assertEquals(fuelTypes, rocMessage.getFuelTypes());
        assertEquals(additionalFuelTypes, rocMessage.getOtherFuelTypes());
        assertEquals(otherSignificantInfo, rocMessage.getOtherSignificantInfo());
        assertEquals(incidentTypes, rocMessage.getIncidentTypes());

        assertEquals(startDateTime, rocMessage.getDateCreated());
        assertEquals(startDateTime, rocMessage.getDate());
        assertEquals(rocStartTime, rocMessage.getStartTime());

        assertEquals(rocLocationBasedData.getLocation(), rocMessage.getLocation());
        assertEquals(rocLocationBasedData.getCounty(), rocMessage.getCounty());
        assertEquals(rocLocationBasedData.getState(), rocMessage.getState());
        assertEquals(rocLocationBasedData.getSra(), rocMessage.getSra());
        assertEquals(rocLocationBasedData.getDpa(), rocMessage.getDpa());
        assertEquals(rocLocationBasedData.getJurisdiction(), rocMessage.getJurisdiction());
        assertEquals(rocLocationBasedData.getTemperature(), rocMessage.getTemperature());
        assertEquals(rocLocationBasedData.getRelHumidity(), rocMessage.getRelHumidity());
        assertEquals(rocLocationBasedData.getWindSpeed(), rocMessage.getWindSpeed());
        assertEquals(rocLocationBasedData.getWindGust(), rocMessage.getWindGust());
        assertEquals(rocLocationBasedData.getWindDirection(), rocMessage.getWindDirection());
    }
}
