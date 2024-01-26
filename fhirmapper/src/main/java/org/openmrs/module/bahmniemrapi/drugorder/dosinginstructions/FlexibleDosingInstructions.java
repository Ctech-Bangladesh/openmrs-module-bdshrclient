package org.openmrs.module.bahmniemrapi.drugorder.dosinginstructions;

import org.openmrs.*;
import org.openmrs.api.APIException;
import org.springframework.validation.Errors;

import java.util.Date;
import java.util.Locale;

import static org.openmrs.module.fhir.utils.DateUtil.aSecondBefore;

public class FlexibleDosingInstructions implements DosingInstructions {

    @Override
    public String getDosingInstructionsAsString(Locale locale) {
        return null;
    }

    @Override
    public void setDosingInstructions(DrugOrder order) {
        order.setDosingType(this.getClass());
    }

    @Override
    public DosingInstructions getDosingInstructions(DrugOrder order) {
        if (!order.getDosingType().equals(this.getClass())) {
            throw new APIException("Dosing type of drug order is mismatched. Expected:" + this.getClass() + " but received:"
                    + order.getDosingType());
        }
        return new FlexibleDosingInstructions();
    }

    @Override
    public void validate(DrugOrder order, Errors errors) {
    }

    @Override
    public Date getAutoExpireDate(DrugOrder drugOrder) {
        return calculateAutoExpireDate(drugOrder.getDuration(), drugOrder.getDurationUnits(), drugOrder.getNumRefills(), drugOrder.getEffectiveStartDate(), drugOrder.getFrequency());
    }

    public Date calculateAutoExpireDate(Integer orderDuration, Concept durationUnits, Integer numRefills, Date effectiveStartDate, OrderFrequency frequency) {
        if (orderDuration == null || durationUnits == null) {
            return null;
        }
        if (numRefills != null && numRefills > 0) {
            return null;
        }
        String durationCode = Duration.getCode(durationUnits);
        if (durationCode == null) {
            return null;
        }
        Duration duration = new Duration(orderDuration, durationCode);
        return aSecondBefore(duration.addToDate(effectiveStartDate, frequency));
    }
}
