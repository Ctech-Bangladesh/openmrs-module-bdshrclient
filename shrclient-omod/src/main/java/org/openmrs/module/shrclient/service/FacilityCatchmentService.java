package org.openmrs.module.shrclient.service;

import org.openmrs.module.shrclient.model.FacilityCatchment;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface FacilityCatchmentService {
    public List<FacilityCatchment> getCatchmentsForFacility(int locationId);

    public List<FacilityCatchment> getFacilitiesForCatchment(String catchment);
}
