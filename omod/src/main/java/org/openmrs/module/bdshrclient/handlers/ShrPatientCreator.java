package org.openmrs.module.bdshrclient.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.UserService;
import org.openmrs.module.addresshierarchy.AddressHierarchyLevel;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.bdshrclient.model.Address;
import org.openmrs.module.bdshrclient.model.Patient;
import org.openmrs.module.bdshrclient.util.GenderEnum;
import org.openmrs.module.bdshrclient.util.FreeShrClientProperties;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openmrs.module.bdshrclient.util.Constants.*;

public class ShrPatientCreator implements EventWorker {

    private static final Logger log = Logger.getLogger(ShrPatientCreator.class);


    private static ObjectMapper jsonMapper = new ObjectMapper();
    private static HttpClient httpClient = HttpClientBuilder.create().build();

    private AddressHierarchyService addressHierarchyService;
    private PatientService patientService;
    private UserService userService;
    private PersonService personService;
    private FreeShrClientProperties properties;

    public ShrPatientCreator(AddressHierarchyService addressHierarchyService, PatientService patientService,
                             UserService userService, PersonService personService) throws IOException {
        this.addressHierarchyService = addressHierarchyService;
        this.patientService = patientService;
        this.userService = userService;
        this.personService = personService;
        properties = new FreeShrClientProperties();
    }

    @Override
    public void process(Event event) {
        log.debug("Event: [" + event + "]");
        try {
            String uuid = getPatientUuid(event);
            log.debug("Patient uuid: [" + uuid + "]");

            org.openmrs.Patient openMrsPatient = patientService.getPatientByUuid(uuid);
            if (openMrsPatient == null) {
                log.debug(String.format("No OpenMRS patient exists with uuid: [%s].", uuid));
                return;
            }

            if (!shouldSyncPatient(openMrsPatient)) {
                log.debug(String.format("OpenMRS patient [%s] was created from SHR. Ignoring Patient Sync.", openMrsPatient));
                return;
            }
            Patient patient = populatePatient(openMrsPatient);
            log.debug("Patient: [ " + patient + "]");

            String healthId = httpPostToMci(patient);
            updateOpenMrsPatientHealthId(openMrsPatient, healthId);

        } catch (IOException e) {
            log.error("Error while processing patient sync event.", e);
        }
    }

    void updateOpenMrsPatientHealthId(org.openmrs.Patient openMrsPatient, String healthId) {
        log.debug(String.format("Trying to update OpenMRS patient [%s] with health id [%s]", openMrsPatient, healthId));

        if (StringUtils.isBlank(healthId)) {
            log.debug("Health id is blank. Hence, not updated.");
            return;
        }

        PersonAttribute healthIdAttribute = openMrsPatient.getAttribute(HEALTH_ID_ATTRIBUTE);
        if (healthIdAttribute != null && healthId.equals(healthIdAttribute.getValue())) {
            log.debug("OpenMRS patient health id is same as the health id provided. Hence, not updated.");
            return;
        }

        if (healthIdAttribute == null) {
            healthIdAttribute = new PersonAttribute();
            PersonAttributeType healthAttrType = personService.getPersonAttributeTypeByName(HEALTH_ID_ATTRIBUTE);
            healthIdAttribute.setAttributeType(healthAttrType);
            healthIdAttribute.setValue(healthId);
            openMrsPatient.addAttribute(healthIdAttribute);

        } else {
            healthIdAttribute.setValue(healthId);
        }

        User shrClientSystemUser = userService.getUserByUsername(SHR_CLIENT_SYSTEM_NAME);
        log.debug("SHR client system user: " + shrClientSystemUser);
        openMrsPatient.setChangedBy(shrClientSystemUser);

        patientService.savePatient(openMrsPatient);
        log.debug(String.format("OpenMRS patient updated."));
    }

    Patient populatePatient(org.openmrs.Patient openMrsPatient) {
        Patient patient = new Patient();

        String nationalId = getAttributeValue(openMrsPatient, NATIONAL_ID_ATTRIBUTE);
        if (nationalId != null) {
            patient.setNationalId(nationalId);
        }

        String healthId = getAttributeValue(openMrsPatient, HEALTH_ID_ATTRIBUTE);
        if (healthId != null) {
            patient.setHealthId(healthId);
        }

        patient.setFirstName(openMrsPatient.getGivenName());
        patient.setMiddleName(openMrsPatient.getMiddleName());
        patient.setLastName(openMrsPatient.getFamilyName());
        patient.setGender(GenderEnum.getCode(openMrsPatient.getGender()));
        patient.setDateOfBirth(new SimpleDateFormat(ISO_DATE_FORMAT).format(openMrsPatient.getBirthdate()));

        String occupation = getAttributeValue(openMrsPatient, OCCUPATION_ATTRIBUTE);
        if (occupation != null) {
            patient.setOccupation(occupation);
        }

        String educationLevel = getAttributeValue(openMrsPatient, EDUCATION_ATTRIBUTE);
        if (educationLevel != null) {
            patient.setEducationLevel(educationLevel);
        }

        String primaryContact = getAttributeValue(openMrsPatient, PRIMARY_CONTACT_ATTRIBUTE);
        if (primaryContact != null) {
            patient.setPrimaryContact(primaryContact);
        }

        patient.setAddress(getAddress(openMrsPatient));
        return patient;
    }

    private String getAttributeValue(org.openmrs.Patient openMrsPatient, String attributeName) {
        PersonAttribute attribute = openMrsPatient.getAttribute(attributeName);
        return attribute != null ? attribute.getValue() : null;
    }

    boolean shouldSyncPatient(org.openmrs.Patient openMrsPatient) {
        User changedByUser = openMrsPatient.getChangedBy();
        if (changedByUser == null) {
            changedByUser = openMrsPatient.getCreator();
        }
        User shrClientSystemUser = userService.getUserByUsername(SHR_CLIENT_SYSTEM_NAME);
        return !shrClientSystemUser.getId().equals(changedByUser.getId());
    }

    String getPatientUuid(Event event) {
        String patientUuid = null;
        Pattern p = Pattern.compile("^\\/openmrs\\/ws\\/rest\\/v1\\/patient\\/(.*)\\?v=full");
        Matcher m = p.matcher(event.getContent());
        if (m.matches()) {
            patientUuid = m.group(1);
        }
        return patientUuid;
    }

    private Address getAddress(org.openmrs.Patient openMrsPatient) {
        PersonAddress openMrsPersonAddress = openMrsPatient.getPersonAddress();
        String addressLine = openMrsPersonAddress.getAddress1();
        String division = openMrsPersonAddress.getStateProvince();
        String district = openMrsPersonAddress.getCountyDistrict();
        String upazilla = openMrsPersonAddress.getAddress3();
        String union = openMrsPersonAddress.getCityVillage();

        List<AddressHierarchyLevel> levels = addressHierarchyService.getAddressHierarchyLevels();
        String divisionId = addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(levels.get(0), division).get(0).getUserGeneratedId();
        String districtId = addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(levels.get(1), district).get(0).getUserGeneratedId();
        String upazillaId = addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(levels.get(2), upazilla).get(0).getUserGeneratedId();
        String unionId = addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(levels.get(3), union).get(0).getUserGeneratedId();

        return new Address(addressLine, divisionId, districtId, upazillaId, unionId);
    }

    String httpPostToMci(Patient patient) throws IOException {
        final String url = properties.getMciUrl();
        final String json = jsonMapper.writeValueAsString(patient);
        log.debug(String.format("HTTP post. \nURL: [%s] \nJSON:[%s]", url, json));

        HttpPost post = new HttpPost(url);
        Map<String, String> headers = new HashMap<String, String>();
        String authHeader = getAuthHeader();
        headers.put("Authorization", authHeader);
        post.setHeaders(toHttpHeaders(headers));

        StringEntity entity = new StringEntity(json);
        entity.setContentType("application/json");
        post.setEntity(entity);

        HttpResponse response = httpClient.execute(post);
        final int statusCode = response.getStatusLine().getStatusCode();
        log.debug(String.format("HTTP post response status code: " + statusCode));

        String healthId = null;
        if (statusCode == 201) {
            healthId = EntityUtils.toString(response.getEntity());
        }
        log.debug(String.format("HTTP post response health id: " + healthId));
        return healthId;
    }

    String getAuthHeader() throws IOException {
        String auth = properties.getMciUser() + ":" + properties.getMciPassword();
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));
        return "Basic " + new String(encodedAuth);
    }

    private static Header[] toHttpHeaders(Map<String, String> headers) {
        List<Header> httpHeaders = new ArrayList<Header>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            httpHeaders.add(new BasicHeader(header.getKey(), header.getValue()));
        }
        return httpHeaders.toArray(new Header[headers.size()]);
    }

    @Override
    public void cleanUp(Event event) {
    }
}
