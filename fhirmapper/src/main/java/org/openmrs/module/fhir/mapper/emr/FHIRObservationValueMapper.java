package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import ca.uhn.fhir.model.primitive.StringDt;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.FHIRProperties;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FHIRObservationValueMapper {

    @Autowired
    private OMRSConceptLookup omrsConceptLookup;
    @Autowired
    private ConceptService conceptService;

    public Obs map(IDatatype value, Obs obs) {
        if (value != null && !value.isEmpty()) {
            if (value instanceof StringDt) {
                obs.setValueText(((StringDt) value).getValue());
            } else if (value instanceof QuantityDt) {
                obs.setValueNumeric(((QuantityDt) value).getValue().doubleValue());
            } else if (value instanceof DateTimeDt) {
                obs.setValueDate(((DateTimeDt) value).getValue());
            } else if (value instanceof CodeableConceptDt) {
                List<CodingDt> codings = ((CodeableConceptDt) value).getCoding();
                Boolean booleanValue = checkIfBooleanCoding(codings);
                if (booleanValue != null) {
                    obs.setValueBoolean(booleanValue);
                } else {
            /* TODO: The last element of codings is the concept. Make this more explicit*/
                    Drug drug = omrsConceptLookup.findDrug(codings);
                    if (drug != null) {
                        obs.setValueCoded(drug.getConcept());
                    } else {
                        obs.setValueCoded(findConcept(codings));
                    }
                }
            }
            return obs;
        }
        return null;
    }

    private Boolean checkIfBooleanCoding(List<CodingDt> codings) {
        for (CodingDt coding : codings) {
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

    private Concept findConcept(List<CodingDt> codings) {
        Concept concept = omrsConceptLookup.findConceptByCode(codings);
        if (concept != null) return concept;
        return conceptService.getConceptByName(codings.get(0).getDisplay());
    }
}
