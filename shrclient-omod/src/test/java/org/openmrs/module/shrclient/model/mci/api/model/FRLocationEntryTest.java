package org.openmrs.module.shrclient.model.mci.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.openmrs.module.shrclient.model.FRLocationEntry;

import static org.junit.Assert.assertEquals;

public class FRLocationEntryTest {
    @Test
    public void shouldPopulateAllTheFieldsFromJSON() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\n" +
                "        \"name\": \"Dhaka Divisional Health Office\",\n" +
                "        \"url\": \"\",\n" +
                "        \"id\": \"10000001\",\n" +
                "        \"active\": \"1\",\n" +
                "        \"createdAt\": \"\",\n" +
                "        \"updatedAt\": \"2001-11-30 00:00:00\",\n" +
                "        \"coordinates\": [\n" +
                "            \"90.39659940000001\",\n" +
                "            \"23.742545\"\n" +
                "        ],\n" +
                "        \"identifiers\": {\n" +
                "            \"agency\": \"DGHS\",\n" +
                "            \"context\": \"HRM\",\n" +
                "            \"id\": \"1\"\n" +
                "        },\n" +
                "        \"properties\": {\n" +
                "            \"ownership\": \"Fully Government-owned\",\n" +
                "            \"org_type\": \"Divisional Level Office\",\n" +
                "            \"org_level\": \"National\",\n" +
                "            \"care_level\": \"\",\n" +
                "            \"services\": [],\n" +
                "            \"locations\": {\n" +
                "                \"division_code\": \"30\",\n" +
                "                \"district_code\": \"26\",\n" +
                "                \"upazila_code\": \"10\",\n" +
                "                \"paurasava_code\": \"\",\n" +
                "                \"union_code\": \"\",\n" +
                "                \"ward_code\": \"\"\n" +
                "            },\n" +
                "            \"contacts\": {\n" +
                "                \"name\": \"\",\n" +
                "                \"email\": \"ddho@ld.dghs.gov.bd\",\n" +
                "                \"phone\": \"9560909\",\n" +
                "                \"mobile\": \"001711956722\",\n" +
                "                \"fax\": \"9561531\"\n" +
                "            },\n" +
                "            \"catchment\": [\n" +
                "                \"30261\"\n" +
                "            ]\n" +
                "        }\n" +
                "    }";

        FRLocationEntry frLocationEntry = mapper.readValue(json, FRLocationEntry.class);

        assertEquals("Dhaka Divisional Health Office", frLocationEntry.getName());
        assertEquals("", frLocationEntry.getUrl());
        assertEquals("10000001", frLocationEntry.getId());
        assertEquals("1", frLocationEntry.getActive());
        assertEquals("", frLocationEntry.getCreatedAt());
        assertEquals("2001-11-30 00:00:00", frLocationEntry.getUpdatedAt());

        assertEquals("90.39659940000001", frLocationEntry.getCoordinates().get(0));
        assertEquals("23.742545", frLocationEntry.getCoordinates().get(1));

        assertEquals("DGHS", frLocationEntry.getIdentifiers().getAgency());
        assertEquals("HRM", frLocationEntry.getIdentifiers().getContext());
        assertEquals("1", frLocationEntry.getIdentifiers().getId());


        assertEquals("Fully Government-owned", frLocationEntry.getProperties().getOwnership());
        assertEquals("Divisional Level Office", frLocationEntry.getProperties().getOrgType());
        assertEquals("National", frLocationEntry.getProperties().getOrgLevel());
        assertEquals("", frLocationEntry.getProperties().getCareLevel());
        assertEquals(0, frLocationEntry.getProperties().getServices().size());

        assertEquals("30", frLocationEntry.getProperties().getLocations().getDivisionCode());
        assertEquals("26", frLocationEntry.getProperties().getLocations().getDistrictCode());
        assertEquals("10", frLocationEntry.getProperties().getLocations().getUpazilaCode());
        assertEquals("", frLocationEntry.getProperties().getLocations().getPaurasavaCode());
        assertEquals("", frLocationEntry.getProperties().getLocations().getUnionCode());
        assertEquals("", frLocationEntry.getProperties().getLocations().getWardCode());

        assertEquals("", frLocationEntry.getProperties().getContacts().getName());
        assertEquals("ddho@ld.dghs.gov.bd", frLocationEntry.getProperties().getContacts().getEmail());
        assertEquals("9560909", frLocationEntry.getProperties().getContacts().getPhone());
        assertEquals("001711956722", frLocationEntry.getProperties().getContacts().getMobile());
        assertEquals("9561531", frLocationEntry.getProperties().getContacts().getFax());

        assertEquals("30261", frLocationEntry.getProperties().getCatchments().get(0));

    }
}