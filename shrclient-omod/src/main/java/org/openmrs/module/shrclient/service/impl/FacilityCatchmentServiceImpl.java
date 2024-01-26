package org.openmrs.module.shrclient.service.impl;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.dao.FacilityCatchmentRepository;
import org.openmrs.module.shrclient.model.FacilityCatchment;
import org.openmrs.module.shrclient.service.FacilityCatchmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FacilityCatchmentServiceImpl implements FacilityCatchmentService {
    private static final Logger logger = Logger.getLogger(FacilityCatchmentServiceImpl.class);

    private FacilityCatchmentRepository facilityCatchmentRepository;

    @Autowired
    public FacilityCatchmentServiceImpl(@Qualifier("bdShrClientFacilityCatchmentRepository") FacilityCatchmentRepository facilityCatchmentRepository) {
        this.facilityCatchmentRepository = facilityCatchmentRepository;
    }

    @Override
    public List<FacilityCatchment> getCatchmentsForFacility(int locationId) {
        logger.debug("Finding catchments for facility with location_id: " + locationId);
        return facilityCatchmentRepository.findByFacilityLocationId(locationId);
    }

    @Override
    public List<FacilityCatchment> getFacilitiesForCatchment(String catchment) {
        logger.debug("Finding facilities for catchment :  " + catchment);
        return facilityCatchmentRepository.findByCatchment(catchment);
    }
}
