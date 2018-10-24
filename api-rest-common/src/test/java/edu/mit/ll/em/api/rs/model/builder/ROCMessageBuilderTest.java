package edu.mit.ll.em.api.rs.model.builder;

import java.util.Date;

import edu.mit.ll.em.api.rs.model.*;
import edu.mit.ll.em.api.rs.model.Location;

import edu.mit.ll.nics.common.entity.*;
import edu.mit.ll.nics.common.entity.DirectProtectionArea;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ROCMessageBuilderTest {

    private String rocDisplayName = "name";
    private String reportType = "UPDATE";
    private String incidentCause = "cause";
    private String incidentType = "Planned event";
    private String generalLocation = "5 miles from xy";
    private Date startDateTime = new Date();
    private String sra = "sra";
    private Jurisdiction jurisdiction = new Jurisdiction("sra", new DirectProtectionArea("dpa", "contract county", "unitid", "respondid"));

    private Weather weather = new Weather("objectId", "-123, 098", 78.9, 10.0f, 214.0, 2.3f, "OK", 10.0);
    private Location location = new Location("county", "state", "000 exact st, xm city, ca, USA, 90000");

    @Test
    public void buildsROCMessageWithGivenReportDetailsAndLeavesOtherFieldsBlank() {
        ROCMessage rocMessage = new ROCMessageBuilder().buildReportDetails(rocDisplayName, reportType, incidentCause, incidentType, generalLocation)
                .build();
        assertEquals(rocDisplayName, rocMessage.getRocDisplayName());
        assertEquals(reportType, rocMessage.getReportType());
        assertEquals(incidentCause, rocMessage.getIncidentCause());
        assertEquals(generalLocation, rocMessage.getGeneralLocation());

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
        assertNull(rocMessage.getWindDirection());
    }

    @Test
    public void buildsROCMessageWithGivenReportDatesAndLeavesOtherFieldsBlank() {
        ROCMessage rocMessage = new ROCMessageBuilder().buildReportDates(startDateTime, startDateTime, startDateTime)
                .build();
        assertEquals(startDateTime, rocMessage.getDateCreated());
        assertEquals(startDateTime, rocMessage.getDate());
        assertEquals(startDateTime, rocMessage.getStartTime());

        assertNull(rocMessage.getRocDisplayName());
        assertNull(rocMessage.getReportType());
        assertNull(rocMessage.getIncidentCause());
        assertNull(rocMessage.getGeneralLocation());

        assertNull(rocMessage.getLocation());
        assertNull(rocMessage.getCounty());
        assertNull(rocMessage.getState());
        assertNull(rocMessage.getSra());
        assertNull(rocMessage.getDpa());
        assertNull(rocMessage.getJurisdiction());
        assertNull(rocMessage.getTemperature());
        assertNull(rocMessage.getRelHumidity());
        assertNull(rocMessage.getWindSpeed());
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
        assertEquals(rocLocationBasedData.getWindDirection(), rocMessage.getWindDirection());

        assertNull(rocMessage.getRocDisplayName());
        assertNull(rocMessage.getReportType());
        assertNull(rocMessage.getIncidentCause());
        assertNull(rocMessage.getGeneralLocation());

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
                .buildReportDetails(rocDisplayName, reportType, incidentCause, incidentType, generalLocation)
                .buildReportDates(startDateTime, startDateTime, startDateTime)
                .buildLocationBasedData(rocLocationBasedData).build();

        assertEquals(rocDisplayName, rocMessage.getRocDisplayName());
        assertEquals(reportType, rocMessage.getReportType());
        assertEquals(incidentCause, rocMessage.getIncidentCause());
        assertEquals(generalLocation, rocMessage.getGeneralLocation());

        assertEquals(startDateTime, rocMessage.getDateCreated());
        assertEquals(startDateTime, rocMessage.getDate());
        assertEquals(startDateTime, rocMessage.getStartTime());

        assertEquals(rocLocationBasedData.getLocation(), rocMessage.getLocation());
        assertEquals(rocLocationBasedData.getCounty(), rocMessage.getCounty());
        assertEquals(rocLocationBasedData.getState(), rocMessage.getState());
        assertEquals(rocLocationBasedData.getSra(), rocMessage.getSra());
        assertEquals(rocLocationBasedData.getDpa(), rocMessage.getDpa());
        assertEquals(rocLocationBasedData.getJurisdiction(), rocMessage.getJurisdiction());
        assertEquals(rocLocationBasedData.getTemperature(), rocMessage.getTemperature());
        assertEquals(rocLocationBasedData.getRelHumidity(), rocMessage.getRelHumidity());
        assertEquals(rocLocationBasedData.getWindSpeed(), rocMessage.getWindSpeed());
        assertEquals(rocLocationBasedData.getWindDirection(), rocMessage.getWindDirection());
    }
}