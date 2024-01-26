package org.openmrs.module.shrclient.mapper;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.*;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.ProviderEntry;
import org.openmrs.module.shrclient.model.ProviderIdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.trim;
import static org.openmrs.module.fhir.utils.DateUtil.*;

@Component
public class ProviderMapper {
    private final static String ORGANIZATION_ATTRIBUTE_TYPE_NAME = "Organization";
    final static String PROVIDER_RETIRE_REASON = "Upstream Deletion";
    final static String PERSON_RETIRE_REASON = "Upstream Deletion of Mapped Provider";
    private final static String NOT_ACTIVE = "0";
    private final static String ACTIVE = "1";
    private ProviderService providerService;
    private IdMappingRepository idMappingRepository;
    private PersonService personService;

    @Autowired
    public ProviderMapper(ProviderService providerService, IdMappingRepository idMappingRepository, PersonService personService) {
        this.providerService = providerService;
        this.idMappingRepository = idMappingRepository;
        this.personService = personService;
    }

    public void createOrUpdate(ProviderEntry providerEntry, SystemProperties systemProperties) {
        String providerIdentifier = trim(providerEntry.getId());
        IdMapping idMapping = idMappingRepository.findByExternalId(providerIdentifier, IdMappingType.PROVIDER);
        Provider provider = null;
        if (idMapping == null) {
            provider = new Provider();
            provider.setIdentifier(providerIdentifier);
            mapProviderToPerson(providerEntry, provider);
        } else {
            provider = providerService.getProviderByUuid(idMapping.getInternalId());
            mapProviderToPerson(providerEntry, provider);
        }
        provider.setName(buildProviderName(providerEntry));
        mapActive(providerEntry, provider);
        mapOrganization(providerEntry, provider);
        personService.savePerson(provider.getPerson());
        providerService.saveProvider(provider);
        String providerUrl = new EntityReference().build(Provider.class, systemProperties, providerIdentifier);
        idMappingRepository.saveOrUpdateIdMapping(new ProviderIdMapping(provider.getUuid(), providerIdentifier, providerUrl));
    }

    private void mapProviderToPerson(ProviderEntry providerEntry, Provider provider) {
        Person person = provider.getPerson();
        if (person == null) {
            person = new Person();
        }
        PersonName personName = person.getPersonName();
        if (personName == null) {
            personName = new PersonName();
            personName.setPreferred(true);
            person.addName(personName);
        }

        personName.setGivenName(providerEntry.getName());
        if (null != providerEntry.getOrganization()) {
            String familyName = String.format("@ %s", providerEntry.getOrganization().getDisplay());
            String familyNameFormatted = familyName.replace("\n", " ").replace("\r", "").replace("\t", " ");
            personName.setFamilyName(StringUtils.substring(familyNameFormatted,0, 50));
        } else {
            personName.setFamilyName("@");
        }
        person.setGender(getGender(providerEntry));
        provider.setPerson(person);
    }

    private String getGender(ProviderEntry providerEntry) {
        if ("Male".equalsIgnoreCase(providerEntry.getGender())){
            return "M";
        }
        if ("Female".equalsIgnoreCase(providerEntry.getGender())){
            return "F";
        }
        return "O";
    }

    private String buildProviderName(ProviderEntry providerEntry) {
        String name = providerEntry.getName();
        if (providerEntry.getOrganization() != null)
            name = String.format("%s @ %s", name, providerEntry.getOrganization().getDisplay());
        return name;
    }

    private void mapActive(ProviderEntry providerEntry, Provider provider) {
        Person person = provider.getPerson();
        if (providerEntry.getActive().equals(NOT_ACTIVE)) {
            provider.setRetired(true);
            provider.setRetireReason(PROVIDER_RETIRE_REASON);
            person.setPersonVoided(true);
            person.setPersonVoidReason(PERSON_RETIRE_REASON);
        } else if (providerEntry.getActive().equals(ACTIVE)) {
            provider.setRetired(false);
            provider.setRetireReason(null);
            person.setPersonVoided(false);
            person.setPersonVoidReason(null);
        }
    }

    private void mapOrganization(ProviderEntry providerEntry, Provider provider) {
        if (providerEntry.getOrganization() != null) {
            ProviderAttribute providerAttribute = getProviderAttribute(provider);
            String facilityUrl = providerEntry.getOrganization().getReference();
            String facilityId = new EntityReference().parse(Location.class, facilityUrl);
            providerAttribute.setValue(facilityId);
            providerAttribute.setValueReferenceInternal(facilityId);
            provider.setAttribute(providerAttribute);
        }
    }

    private ProviderAttribute getProviderAttribute(Provider provider) {
        ProviderAttributeType organizationAttributeType = findOrganizationProviderAttributeType();
        ProviderAttribute providerAttribute = findInExistingAttributes(provider, organizationAttributeType);
        if (providerAttribute == null) {
            providerAttribute = createNewProviderAttribute(provider, organizationAttributeType);
        }
        return providerAttribute;
    }

    private ProviderAttribute findInExistingAttributes(Provider provider, ProviderAttributeType organizationAttributeType) {
        List<ProviderAttribute> providerAttributes = provider.getActiveAttributes(organizationAttributeType);
        if (!providerAttributes.isEmpty()) return providerAttributes.get(0);
        return null;
    }

    private ProviderAttribute createNewProviderAttribute(Provider provider, ProviderAttributeType organizationAttributeType) {
        ProviderAttribute providerAttribute;
        providerAttribute = new ProviderAttribute();
        providerAttribute.setProvider(provider);
        providerAttribute.setAttributeType(organizationAttributeType);
        return providerAttribute;
    }

    private ProviderAttributeType findOrganizationProviderAttributeType() {
        for (ProviderAttributeType providerAttributeType : providerService.getAllProviderAttributeTypes(false)) {
            if (providerAttributeType.getName().equals(ORGANIZATION_ATTRIBUTE_TYPE_NAME)) {
                return providerAttributeType;
            }
        }
        return null;
    }
}
