package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FHIRObservationValueMapper {

    private final OMRSConceptLookup omrsConceptLookup;

    @Autowired
    public FHIRObservationValueMapper(OMRSConceptLookup omrsConceptLookup) {
        this.omrsConceptLookup = omrsConceptLookup;
    }

    public Obs map(Type value, Obs obs) {
        if (value != null && !value.isEmpty()) {
            if (value instanceof StringType) {
                obs.setValueText(((StringType) value).getValue());
            } else if (value instanceof Quantity) {
                obs.setValueNumeric(((Quantity) value).getValue().doubleValue());
            } else if (value instanceof DateTimeType) {
                obs.setValueDatetime(((DateTimeType) value).getValue());
            } else if (value instanceof DateType) {
                obs.setValueDate(((DateType) value).getValue());
            } else if (value instanceof BooleanType) {
                obs.setValueBoolean(((BooleanType) value).getValue());
            } else if (value instanceof CodeableConcept) {
                List<Coding> codings = ((CodeableConcept) value).getCoding();
                Boolean booleanValue = checkIfBooleanCoding(codings);
                if (booleanValue != null) {
                    obs.setValueBoolean(booleanValue);
                } else {
            /* TODO: The last element of codings is the concept. Make this more explicit*/
                    Drug drug = omrsConceptLookup.findDrug(codings);
                    if (drug != null) {
                        obs.setValueCoded(drug.getConcept());
                        obs.setValueDrug(drug);
                    } else {
                        obs.setValueCoded(omrsConceptLookup.findConceptByCodeOrDisplay(codings));
                    }
                }
            }
            return obs;
        }
        return null;
    }

    private Boolean checkIfBooleanCoding(List<Coding> codings) {
        for (Coding coding : codings) {
            if (coding.getSystem() != null && coding.getSystem().equals(FHIRProperties.FHIR_YES_NO_INDICATOR_URL)) {
                if (coding.getCode() != null && coding.getCode().equals(FHIRProperties.FHIR_NO_INDICATOR_CODE)) {
                    return false;
                } else if (coding.getCode() != null && coding.getCode().equals(FHIRProperties.FHIR_YES_INDICATOR_CODE)) {
                    return true;
                }
            }
        }
        return null;
    }
}
