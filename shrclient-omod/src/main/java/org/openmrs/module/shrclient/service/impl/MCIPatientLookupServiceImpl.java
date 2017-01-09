package org.openmrs.module.shrclient.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.addresshierarchy.AddressHierarchyEntry;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.model.Address;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.mci.api.MciPatientSearchResponse;
import org.openmrs.module.shrclient.service.EMREncounterService;
import org.openmrs.module.shrclient.service.EMRPatientMergeService;
import org.openmrs.module.shrclient.service.EMRPatientService;
import org.openmrs.module.shrclient.service.MCIPatientLookupService;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.web.controller.MciPatientSearchRequest;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.util.Arrays.asList;

@Component
public class MCIPatientLookupServiceImpl extends BaseOpenmrsService implements MCIPatientLookupService {
    private static final Logger log = Logger.getLogger(MCIPatientLookupServiceImpl.class);

    private static final String NID_PARAM_KEY = "nid";
    private static final String UID_PARAM_KEY = "uid";
    private static final String BRN_PARAM_KEY = "bin_brn";
    private static final String HOUSE_HOLD_CODE_PARAM_KEY = "household_code";
    private static final String PHONE_NO_PARAM_KEY = "phone_no";
    private final String patientContext;

    private EMRPatientService emrPatientService;
    private PropertiesReader propertiesReader;
    private IdentityStore identityStore;
    private EMREncounterService emrEncounterService;
    private EMRPatientMergeService emrPatientMergeService;

    @Autowired
    public MCIPatientLookupServiceImpl(@Qualifier("hieEmrPatientService") EMRPatientService emrPatientService, PropertiesReader propertiesReader,
                                       IdentityStore identityStore, @Qualifier("hieEmrEncounterService") EMREncounterService emrEncounterService,
                                       EMRPatientMergeService emrPatientMergeService) {
        this.emrPatientService = emrPatientService;
        this.propertiesReader = propertiesReader;
        this.identityStore = identityStore;
        this.emrEncounterService = emrEncounterService;
        this.emrPatientMergeService = emrPatientMergeService;
        this.patientContext = propertiesReader.getMciPatientContext();
    }

    @Override
    public Object searchPatientInRegistry(MciPatientSearchRequest request) {
        Patient mciPatient = null;
        List<Patient> patientList = new ArrayList<>();
        if (StringUtils.isNotBlank(request.getHid())) {
            mciPatient = searchPatientByHealthId(request.getHid());
            if (mciPatient != null) {
                patientList.add(mciPatient);
            }

        } else {
            String searchParamKey = null;
            String searchParamValue = null;
            if (StringUtils.isNotBlank(request.getNid())) {
                searchParamKey = NID_PARAM_KEY;
                searchParamValue = request.getNid();
            } else if (StringUtils.isNotBlank(request.getUid())) {
                searchParamKey = UID_PARAM_KEY;
                searchParamValue = request.getUid();
            } else if (StringUtils.isNotBlank(request.getBrn())) {
                searchParamKey = BRN_PARAM_KEY;
                searchParamValue = request.getBrn();
            } else if (StringUtils.isNotBlank(request.getHouseHoldCode())) {
                searchParamKey = HOUSE_HOLD_CODE_PARAM_KEY;
                searchParamValue = request.getHouseHoldCode();
            } else if (StringUtils.isNotBlank(request.getPhoneNo())) {
                searchParamKey = PHONE_NO_PARAM_KEY;
                searchParamValue = request.getPhoneNo();
            }
            if (StringUtils.isNotBlank(searchParamKey) && StringUtils.isNotBlank(searchParamValue)) {
                Patient[] patients = searchPatients(searchParamKey, searchParamValue);
                if ((patients != null) && (patients.length > 0)) {
                    patientList.addAll(asList(patients));
                }
            }
        }
        if (!patientList.isEmpty()) {
            sortPatientList(patientList);
            return mapSearchResults(patientList);
        }
        return null;
    }

    @Override
    public Object downloadPatient(MciPatientSearchRequest request) {
        final String healthId = request.getHid();
        Patient mciPatient = searchPatientByHealthId(healthId);
        if (mciPatient != null) {
            org.openmrs.Patient emrPatient = emrPatientService.createOrUpdateEmrPatient(mciPatient);
            if (emrPatient != null) {
                Map<String, Object> downloadResponse = new HashMap<>();
                List<String> mergedHealthIds = null;
                if(CollectionUtils.isNotEmpty(request.getInactiveHids())) {
                    mergedHealthIds = emrPatientMergeService.mergePatients(mciPatient.getHealthId(), request.getInactiveHids());
                }
                createOrUpdateEncounters(healthId, emrPatient);
                downloadResponse.put("uuid", emrPatient.getUuid());
                downloadResponse.put("hid", healthId);
                List<String> localIds = convertPatientIdentifiersToList(emrPatient.getIdentifiers());
                downloadResponse.put("localIds", localIds);
                if (CollectionUtils.isNotEmpty(mergedHealthIds)) {
                    List<Object> mergedHealthIdsWithLocalIds = getMergedHealthIdsWithLocalIds(mergedHealthIds);
                    downloadResponse.put("mergedIdentifiers", mergedHealthIdsWithLocalIds);
                }
                return downloadResponse;
            }
        }
        return null;
    }

    private void sortPatientList(List<Patient> patientList) {
        Collections.sort(patientList, new Comparator<Patient>() {
            @Override
            public int compare(Patient o1, Patient o2) {
                return o2.isActive().compareTo(o1.isActive());
            }
        });
    }

    private List<String> convertPatientIdentifiersToList(Set<PatientIdentifier> identifiers) {
        List<String> localIds = new ArrayList<>();
        org.apache.commons.collections4.CollectionUtils.collect(identifiers, new Transformer<PatientIdentifier, String>() {
            @Override
            public String transform(PatientIdentifier patientIdentifier) {
                return patientIdentifier.getIdentifier();
            }
        }, localIds);
        return localIds;
    }

    private List<Object> getMergedHealthIdsWithLocalIds(List<String> mergedHealthIds) {
        List<Object> mergedHidWithLocalIds = new ArrayList<>();
        for (String mergedHealthId : mergedHealthIds) {
            org.openmrs.Patient mergedPatient = emrPatientService.getEMRPatientByHealthId(mergedHealthId);
            List<String> localIdentifiers = convertPatientIdentifiersToList(mergedPatient.getIdentifiers());
            HashMap<String, Object> map = new HashMap<>();
            map.put("hid", mergedHealthId);
            map.put("localIds", localIdentifiers);
            mergedHidWithLocalIds.add(map);
        }
        return mergedHidWithLocalIds;
    }

    private Object[] mapSearchResults(List<Patient> patientList) {
        ArrayList<Object> results = new ArrayList<>();
        for (Patient patient : patientList) {
            if (patient.isActive()) {
                results.add(mapToPatientUIModel(patient));
            } else {
                ArrayList<String> inactiveHids = new ArrayList<>();
                inactiveHids.add(patient.getHealthId());
                Patient activePatient = searchActivePatient(patient.getMergedWith(), inactiveHids);
                results.add(mapToPatientMergeUIModel(patient, activePatient, inactiveHids));
            }
        }
        return results.toArray();
    }

    private Patient searchActivePatient(String hid, List<String> inactiveHids) {
        Patient mergedWithPatient = searchPatientByHealthId(hid);
        if(mergedWithPatient != null && !mergedWithPatient.isActive()) {
            inactiveHids.add(mergedWithPatient.getHealthId());
            return searchActivePatient(mergedWithPatient.getMergedWith(), inactiveHids);
        }
        return mergedWithPatient;
    }

    private Object mapToPatientMergeUIModel(Patient inactivePatient, Patient activePatient, ArrayList<String> inactiveHids) {
        Map<String, Object> patientModel = mapToPatientUIModel(activePatient);
        patientModel.put("inactiveHID", inactivePatient.getHealthId());
        patientModel.put("inactiveHIDs", inactiveHids);
        patientModel.put("active", false);
        return patientModel;
    }

    private Map<String, Object> mapToPatientUIModel(Patient mciPatient) {
        Map<String, Object> patientModel = new HashMap<>();
        patientModel.put("firstName", mciPatient.getGivenName());
        patientModel.put("lastName", mciPatient.getSurName());
        patientModel.put("gender", getGender(mciPatient));
        patientModel.put("nationalId", mciPatient.getNationalId());
        patientModel.put("healthId", mciPatient.getHealthId());

        Map<String, String> addressModel = new HashMap<>();
        Address address = mciPatient.getAddress();
        addressModel.put("addressLine", address.getAddressLine());
        addressModel.put("division", getAddressEntryText(address.getDivisionId()));
        addressModel.put("district", getAddressEntryText(address.createUserGeneratedDistrictId()));
        addressModel.put("upazilla", getAddressEntryText(address.createUserGeneratedUpazillaId()));

        if (address.getCityCorporationId() != null) {
            addressModel.put("cityCorporation", getAddressEntryText(address.createUserGeneratedCityCorporationId()));
            if (address.getUnionOrUrbanWardId() != null)
                addressModel.put("unionOrUrbanWard", getAddressEntryText(address
                        .createUserGeneratedUnionOrUrbanWardId()));
        }
        patientModel.put("address", addressModel);
        patientModel.put("active", mciPatient.isActive());
        return patientModel;
    }

    private String getGender(Patient mciPatient) {
        if (mciPatient.getGender().equals("O")) return "T";
        return mciPatient.getGender();
    }

    private String getAddressEntryText(String code) {
        AddressHierarchyService addressHierarchyService = Context.getService(AddressHierarchyService.class);
        AddressHierarchyEntry entry = addressHierarchyService.getAddressHierarchyEntryByUserGenId(code);
        return entry.getName();
    }

    private void createOrUpdateEncounters(String healthId, org.openmrs.Patient emrPatient) {
        final String url = String.format("/patients/%s/encounters", healthId);
        List<EncounterEvent> encounterEvents = null;
        try {
            encounterEvents = new ClientRegistry(propertiesReader, identityStore).getSHRClient().getEncounters(url);
        } catch (IdentityUnauthorizedException e) {
            log.info("Clearing unauthorized identity token.");
            identityStore.clearToken();
        }
        emrEncounterService.createOrUpdateEncounters(emrPatient, encounterEvents);
    }

    private Patient[] searchPatients(String searchParamKey, String searchParamValue) {
        String url = String.format("%s?%s=%s", this.patientContext, searchParamKey, searchParamValue);
        MciPatientSearchResponse mciPatientSearchResponse = null;
        try {
            mciPatientSearchResponse = getMciRestClient().get(url, MciPatientSearchResponse.class);
        } catch (IdentityUnauthorizedException e) {
            log.info("Clearing unauthorized identity token.");
            identityStore.clearToken();
            throw new RuntimeException(e);
        }
        return mciPatientSearchResponse.getResults();
    }

    private Patient searchPatientByHealthId(String hid) {
        if (StringUtils.isBlank(hid)) {
            return null;
        }
        try {
            return getMciRestClient().get(this.patientContext + "/" + hid, Patient.class);
        } catch (IdentityUnauthorizedException e) {
            log.info("Clearing unauthorized identity token.");
            identityStore.clearToken();
            throw new RuntimeException(e);
        }
    }

    private RestClient getMciRestClient() throws IdentityUnauthorizedException {
        return new ClientRegistry(propertiesReader, identityStore).getMCIClient();
    }
}
