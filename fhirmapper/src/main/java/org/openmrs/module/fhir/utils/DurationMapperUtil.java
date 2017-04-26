package org.openmrs.module.fhir.utils;

import org.hl7.fhir.dstu3.model.Timing;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component
public class DurationMapperUtil {

    private Map<String, Timing.UnitsOfTime> unitsOfTimeMap;
    private Map<Timing.UnitsOfTime, String> conceptNameToUnitsOfTimeMap;

    public DurationMapperUtil() {
        buildUnitOfTimeMap();
        buildConceptNameToUnitOfTimeMap();
    }

    private void buildUnitOfTimeMap() {
        unitsOfTimeMap = new HashMap<>();
        unitsOfTimeMap.put("Minute(s)", Timing.UnitsOfTime.MIN);
        unitsOfTimeMap.put("Hour(s)", Timing.UnitsOfTime.H);
        unitsOfTimeMap.put("Day(s)", Timing.UnitsOfTime.D);
        unitsOfTimeMap.put("Week(s)", Timing.UnitsOfTime.WK);
        unitsOfTimeMap.put("Month(s)", Timing.UnitsOfTime.MO);
    }

    private void buildConceptNameToUnitOfTimeMap() {
        conceptNameToUnitsOfTimeMap = new HashMap<>();
        conceptNameToUnitsOfTimeMap.put(Timing.UnitsOfTime.MIN, "Minute(s)");
        conceptNameToUnitsOfTimeMap.put(Timing.UnitsOfTime.H, "Hour(s)");
        conceptNameToUnitsOfTimeMap.put(Timing.UnitsOfTime.D, "Day(s)");
        conceptNameToUnitsOfTimeMap.put(Timing.UnitsOfTime.WK, "Week(s)");
        conceptNameToUnitsOfTimeMap.put(Timing.UnitsOfTime.MO, "Month(s)");
    }

    public Timing.UnitsOfTime getUnitOfTime(String conceptName) {
        return unitsOfTimeMap.get(conceptName);
    }

    public String getConceptNameFromUnitOfTime(Timing.UnitsOfTime unitsOfTime) {
        return conceptNameToUnitsOfTimeMap.get(unitsOfTime);
    }
}
