package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.openmrs.module.fhir.FHIRProperties.*;

@Component
public class ObservationValueMapper {

    private final CodeableConceptService codeableConceptService;
    private static ConceptService conceptService;
    private static Object monitor = new Object();

    private enum ValueReader {

        Numeric {
            @Override
            public Type readValue(Obs obs, CodeableConceptService codeableConceptService) {
                if (obs.getConcept().getDatatype().isNumeric() && obs.getValueNumeric() != null) {
                    Quantity quantity = new Quantity();
                    quantity.setValue(obs.getValueNumeric());
                    if (obs.getConcept().isNumeric()) {
                        Integer conceptId = obs.getConcept().getConceptId();
                        ConceptNumeric conceptNumeric = getConceptService().getConceptNumeric(conceptId);
                        if (conceptNumeric != null) {
                            String units = conceptNumeric.getUnits();
                            if (units != null) quantity.setUnit(units);
                        }
                    }
                    return quantity;
                }
                return null;
            }
        },

        Text {
            @Override
            public Type readValue(Obs obs, CodeableConceptService codeableConceptService) {
                if (obs.getConcept().getDatatype().isText() && obs.getValueText() != null) {
                    return new StringType(obs.getValueText());
                }
                return null;
            }
        },

        Boolean {
            @Override
            public Type readValue(Obs obs, CodeableConceptService codeableConceptService) {
                if (obs.getConcept().getDatatype().isBoolean() && obs.getValueAsBoolean() != null) {
                    CodeableConcept codeableConcept = new CodeableConcept();
                    Coding coding = codeableConcept.addCoding();
                    coding.setSystem(FHIR_YES_NO_INDICATOR_URL);
                    coding.setCode(obs.getValueBoolean() ? FHIR_YES_INDICATOR_CODE : FHIR_NO_INDICATOR_CODE);
                    coding.setDisplay(obs.getValueBoolean() ? FHIR_YES_INDICATOR_DISPLAY : FHIR_NO_INDICATOR_DISPLAY);
                    return codeableConcept;
                }
                return null;
            }
        },

        Date {
            @Override
            public Type readValue(Obs obs, CodeableConceptService codeableConceptService) {
                if (obs.getConcept().getDatatype().isDate() && obs.getValueDate() != null) {
                    return new DateTimeType(obs.getValueDate());
                }
                return null;
            }
        },

        DateTime {
            @Override
            public Type readValue(Obs obs, CodeableConceptService codeableConceptService) {
                if (obs.getConcept().getDatatype().isDateTime() && obs.getValueDatetime() != null) {
                    return new DateTimeType(obs.getValueDate());
                }
                return null;
            }
        },

        Coded {
            @Override
            public Type readValue(Obs obs, CodeableConceptService codeableConceptService) {
                if (obs.getConcept().getDatatype().isCoded() && obs.getValueCoded() != null) {
                    CodeableConcept codeableConcept = null;
                    if (obs.getValueDrug() != null) {
                        Drug valueDrug = obs.getValueDrug();
                        codeableConcept = codeableConceptService.addTRCodingOrDisplay(valueDrug);
                    } else {
                        Concept valueCoded = obs.getValueCoded();
                        codeableConcept = codeableConceptService.addTRCodingOrDisplay(valueCoded);
                    }
                    return codeableConcept;
                }
                return null;
            }
        };


        public abstract Type readValue(Obs obs, CodeableConceptService codeableConceptService);
    }

    @Autowired
    public ObservationValueMapper(

            CodeableConceptService codeableConceptService) {
        this.codeableConceptService = codeableConceptService;
    }

    private static ConceptService getConceptService() {
        if (conceptService == null) {
            synchronized (monitor) {
                if (conceptService == null) {
                    conceptService = Context.getConceptService();
                }
            }
        }
        return conceptService;
    }

    public Type map(Obs observation) {
        for (ValueReader valueReader : ValueReader.values()) {
            Type readValue = valueReader.readValue(observation, codeableConceptService);
            if (null != readValue) {
                return readValue;
            }
        }
        return null;
    }
}
