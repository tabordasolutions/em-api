package edu.mit.ll.em.api.rs.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import edu.mit.ll.em.api.json.deserializer.LocationDeserializer;

@JsonDeserialize(using = LocationDeserializer.class)
public class Location {

    private String county;
    private String state;
    private String specificLocation;

    public Location(String county, String state, String specificLocation) {
        this.county = county;
        this.state = state;
        this.specificLocation = specificLocation;
    }

    public String getCounty() {
        return county;
    }

    public String getState() {
        return state;
    }

    public String getSpecificLocation() {
        return specificLocation;
    }
}