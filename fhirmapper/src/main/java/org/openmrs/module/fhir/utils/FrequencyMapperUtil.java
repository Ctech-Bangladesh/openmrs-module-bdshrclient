package org.openmrs.module.fhir.utils;

import org.hl7.fhir.dstu3.model.Timing;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component
public class FrequencyMapperUtil {
    private Map<String, FrequencyUnit> conceptNameToFrequencyUnitMap;

    public FrequencyMapperUtil() {
        buildConceptNameMap();
    }

    public enum FrequencyUnit {
        ONCE_A_DAY("Once a day", 1, Timing.UnitsOfTime.D, 1),
        TWICE_A_DAY("Twice a day", 2, Timing.UnitsOfTime.D, 1),
        THRICE_A_DAY("Thrice a day", 3, Timing.UnitsOfTime.D, 1),
        FOUR_TIMES_A_DAY("Four times a day", 4, Timing.UnitsOfTime.D, 1),
        EVERY_HOUR("Every Hour", 1, Timing.UnitsOfTime.H, 1),
        EVERY_TWO_HOURS("Every 2 hours", 1, Timing.UnitsOfTime.H, 2),
        EVERY_THREE_HOURS("Every 3 hours", 1, Timing.UnitsOfTime.H, 3),
        EVERY_FOUR_HOURS("Every 4 hours", 1, Timing.UnitsOfTime.H, 4),
        EVERY_SIX_HOURS("Every 6 hours", 1, Timing.UnitsOfTime.H, 6),
        EVERY_EIGHT_HOURS("Every 8 hours", 1, Timing.UnitsOfTime.H, 8),
        EVERY_TWELVE_HOURS("Every 12 hours", 1, Timing.UnitsOfTime.H, 12),
        FIVE_TIMES_A_DAY("Five times a day", 5, Timing.UnitsOfTime.D, 1),
        ON_ALTERNATE_DAYS("On alternate days", 1, Timing.UnitsOfTime.D, 2),
        ONCE_A_WEEK("Once a week", 1, Timing.UnitsOfTime.WK, 1),
        TWICE_A_WEEK("Twice a week", 2, Timing.UnitsOfTime.WK, 1),
        THRICE_A_WEEK("Thrice a week", 3, Timing.UnitsOfTime.WK, 1),
        FOUR_DAYS_A_WEEK("Four days a week", 4, Timing.UnitsOfTime.WK, 1),
        FIVE_DAYS_A_WEEK("Five days a week", 5, Timing.UnitsOfTime.WK, 1),
        SIX_DAYS_A_WEEK("Six days a week", 6, Timing.UnitsOfTime.WK, 1),
        EVERY_TWO_WEEKS("Every 2 weeks", 1, Timing.UnitsOfTime.WK, 2),
        EVERY_THREE_WEEKS("Every 3 weeks", 1, Timing.UnitsOfTime.WK, 3),
        ONCE_A_MONTH("Once a month", 1, Timing.UnitsOfTime.MO, 1);

        private final String conceptName;
        private int frequency;
        private final Timing.UnitsOfTime unitOfTime;
        private int frequencyPeriod;

        FrequencyUnit(String conceptName, int frequency, Timing.UnitsOfTime unitOfTime, int frequencyPeriod) {
            this.conceptName = conceptName;
            this.frequency = frequency;
            this.unitOfTime = unitOfTime;
            this.frequencyPeriod = frequencyPeriod;
        }

        public String getConceptName() {
            return conceptName;
        }

        public int getFrequency() {
            return frequency;
        }

        public Timing.UnitsOfTime getUnitOfTime() {
            return unitOfTime;
        }

        public int getFrequencyPeriod() {
            return frequencyPeriod;
        }
    }

    private void buildConceptNameMap() {
        conceptNameToFrequencyUnitMap = new HashMap<>();
        conceptNameToFrequencyUnitMap.put("Once a day", FrequencyUnit.ONCE_A_DAY);
        conceptNameToFrequencyUnitMap.put("Twice a day", FrequencyUnit.TWICE_A_DAY);
        conceptNameToFrequencyUnitMap.put("Thrice a day", FrequencyUnit.THRICE_A_DAY);
        conceptNameToFrequencyUnitMap.put("Four times a day", FrequencyUnit.FOUR_TIMES_A_DAY);
        conceptNameToFrequencyUnitMap.put("Every Hour", FrequencyUnit.EVERY_HOUR);
        conceptNameToFrequencyUnitMap.put("Every 2 hours", FrequencyUnit.EVERY_TWO_HOURS);
        conceptNameToFrequencyUnitMap.put("Every 3 hours", FrequencyUnit.EVERY_THREE_HOURS);
        conceptNameToFrequencyUnitMap.put("Every 4 hours", FrequencyUnit.EVERY_FOUR_HOURS);
        conceptNameToFrequencyUnitMap.put("Every 6 hours", FrequencyUnit.EVERY_SIX_HOURS);
        conceptNameToFrequencyUnitMap.put("Every 8 hours", FrequencyUnit.EVERY_EIGHT_HOURS);
        conceptNameToFrequencyUnitMap.put("Every 12 hours", FrequencyUnit.EVERY_TWELVE_HOURS);
        conceptNameToFrequencyUnitMap.put("Five times a day", FrequencyUnit.FIVE_TIMES_A_DAY);
        conceptNameToFrequencyUnitMap.put("On alternate days", FrequencyUnit.ON_ALTERNATE_DAYS);
        conceptNameToFrequencyUnitMap.put("Once a week", FrequencyUnit.ONCE_A_WEEK);
        conceptNameToFrequencyUnitMap.put("Twice a week", FrequencyUnit.TWICE_A_WEEK);
        conceptNameToFrequencyUnitMap.put("Thrice a week", FrequencyUnit.THRICE_A_WEEK);
        conceptNameToFrequencyUnitMap.put("Four days a week", FrequencyUnit.FOUR_DAYS_A_WEEK);
        conceptNameToFrequencyUnitMap.put("Five days a week", FrequencyUnit.FIVE_DAYS_A_WEEK);
        conceptNameToFrequencyUnitMap.put("Six days a week", FrequencyUnit.SIX_DAYS_A_WEEK);
        conceptNameToFrequencyUnitMap.put("Every 2 weeks", FrequencyUnit.EVERY_TWO_WEEKS);
        conceptNameToFrequencyUnitMap.put("Every 3 weeks", FrequencyUnit.EVERY_THREE_WEEKS);
        conceptNameToFrequencyUnitMap.put("Once a month", FrequencyUnit.ONCE_A_MONTH);
    }

    public FrequencyUnit getFrequencyUnits(String conceptName) {
        return conceptNameToFrequencyUnitMap.get(conceptName);
    }

    public FrequencyUnit getFrequencyUnitsFromRepeat(Timing.TimingRepeatComponent repeat) {
        for (FrequencyUnit frequencyUnit : FrequencyUnit.values()) {
//            if(frequencyUnit.getFrequency() == repeat.getFrequency() &&
//                    frequencyUnit.getFrequencyPeriod() == repeat.getPeriod().intValue() &&
//                    frequencyUnit.getUnitOfTime().equals(repeat.getPeriodUnitsElement().getValueAsEnum())) {
//                return frequencyUnit;
//            }
        }
        return null;
    }
}
