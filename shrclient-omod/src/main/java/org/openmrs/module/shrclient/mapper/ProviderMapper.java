package org.openmrs.module.shrclient.mapper;

import static org.apache.commons.lang3.StringUtils.trim;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Location;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.ProviderEntry;
import org.openmrs.module.shrclient.model.ProviderIdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProviderMapper {
    private final static String ORGANIZATION_ATTRIBUTE_TYPE_NAME = "Organization";
    private final static String DESIGNATION_ATTRIBUTE_TYPE_NAME = "Designation";
    final static String PROVIDER_RETIRE_REASON = "Upstream Deletion";
    final static String PERSON_RETIRE_REASON = "Upstream Deletion of Mapped Provider";
    private final static String NOT_ACTIVE = "0";
    private final static String ACTIVE = "1";
    private final ProviderService providerService;
    private final IdMappingRepository idMappingRepository;
    private final PersonService personService;

    @Autowired
    public ProviderMapper(ProviderService providerService, IdMappingRepository idMappingRepository, PersonService personService) {
        this.providerService = providerService;
        this.idMappingRepository = idMappingRepository;
        this.personService = personService;
    }

    public void createOrUpdate(ProviderEntry providerEntry, SystemProperties systemProperties) {

        /*Trim the name in a short form if it's too large*/
        providerEntry.setName(shortenName(providerEntry.getName()));

        String providerIdentifier = trim(providerEntry.getId());
        Provider provider = providerService.getProviderByIdentifier(providerIdentifier);
        IdMapping idMapping = idMappingRepository.findByExternalId(providerIdentifier, IdMappingType.PROVIDER);

        if (provider != null && idMapping == null){
            String providerUrl = new EntityReference().build(Provider.class, systemProperties, providerIdentifier);
            idMappingRepository.saveOrUpdateIdMapping(new ProviderIdMapping(provider.getUuid(), providerIdentifier, providerUrl));
            idMapping = idMappingRepository.findByExternalId(providerIdentifier, IdMappingType.PROVIDER);
        }

        if (provider != null && idMapping != null){
            if (!provider.getUuid().equals(idMapping.getInternalId())){
                provider.setIdentifier(null);
                providerService.saveProvider(provider);
            }
        }

        if (idMapping == null) {
            provider = new Provider();
            mapProviderToPerson(providerEntry, provider);
        } else {
            provider = providerService.getProviderByUuid(idMapping.getInternalId());
            mapProviderToPerson(providerEntry, provider);
        }
        provider.setIdentifier(providerIdentifier);
        provider.setName(buildProviderName(providerEntry));
        mapActive(providerEntry, provider);
        mapOrganization(providerEntry, provider);
        mapDesignation(providerEntry, provider);
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
            ProviderAttribute providerAttribute = getProviderOrganizationAttribute(provider);
            String facilityUrl = providerEntry.getOrganization().getReference();
            String facilityId = new EntityReference().parse(Location.class, facilityUrl);
            providerAttribute.setValue(facilityId);
            providerAttribute.setValueReferenceInternal(facilityId);
            provider.setAttribute(providerAttribute);
        }
    }

    private void mapDesignation(ProviderEntry providerEntry, Provider provider) {
        if (providerEntry.getProperties() != null) {
            if (providerEntry.getProperties().getDesignation() != null){
                ProviderAttribute providerAttribute = getProviderDesignationAttribute(provider);
                String designation = providerEntry.getProperties().getDesignation();
                providerAttribute.setValue(designation);
                providerAttribute.setValueReferenceInternal(designation);
                provider.setAttribute(providerAttribute);
            }
        }
    }

    private ProviderAttribute getProviderOrganizationAttribute(Provider provider) {
        ProviderAttributeType organizationAttributeType = findOrganizationProviderAttributeType();
        ProviderAttribute providerAttribute = findInExistingAttributes(provider, organizationAttributeType);
        if (providerAttribute == null) {
            providerAttribute = createNewProviderAttribute(provider, organizationAttributeType);
        }
        return providerAttribute;
    }

    private ProviderAttribute getProviderDesignationAttribute(Provider provider) {
        ProviderAttributeType designationAttributeType = findDesignationProviderAttributeType();
        ProviderAttribute providerAttribute = findInExistingAttributes(provider, designationAttributeType);
        if (providerAttribute == null) {
            providerAttribute = createNewProviderAttribute(provider, designationAttributeType);
        }
        return providerAttribute;
    }

    private ProviderAttribute findInExistingAttributes(Provider provider, ProviderAttributeType organizationAttributeType) {
        List<ProviderAttribute> providerAttributes = provider.getActiveAttributes(organizationAttributeType);
        if (!providerAttributes.isEmpty()) return providerAttributes.get(0);
        return null;
    }

    private ProviderAttribute createNewProviderAttribute(Provider provider, ProviderAttributeType attributeType) {
        ProviderAttribute providerAttribute;
        providerAttribute = new ProviderAttribute();
        providerAttribute.setProvider(provider);
        providerAttribute.setAttributeType(attributeType);
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

    private ProviderAttributeType findDesignationProviderAttributeType() {
        for (ProviderAttributeType providerAttributeType : providerService.getAllProviderAttributeTypes(false)) {
            if (providerAttributeType.getName().equals(DESIGNATION_ATTRIBUTE_TYPE_NAME)) {
                return providerAttributeType;
            }
        }
        return null;
    }

/*    public static String shortenName(String fullName) {
        // Split the full name by spaces
        List<String> words = Arrays.asList(fullName.split(" "));

        // Check if the name has more than 6 words
        if (words.size() <= 6) {
            return fullName; // Return the original name if it has 6 or fewer words
        }

        // The first word is always included
        String firstWord = words.get(0);

        // Filter out invalid words and collect valid words except the first and the last two
        List<String> validWords = words.stream()
            .skip(1) // Skip the first word
            .limit(words.size() - 3) // Exclude the last two words
            .filter(word -> !word.contains(".") && word.length() > 2) // Filter valid words
            .collect(Collectors.toList());

        // If there are fewer than 4 valid words, return the full name
        if (validWords.size() < 4) {
            return fullName;
        }

        // Create the short form using the first letter of the first four valid words
        String shortForm = validWords.stream()
            .limit(4)
            .map(word -> String.valueOf(word.charAt(0)))
            .collect(Collectors.joining());

        // Append the rest of the valid words after the first four
        String restValidWords = validWords.stream()
            .skip(4)
            .map(word -> String.valueOf(word.charAt(0)))
            .collect(Collectors.joining());

        // Collect the last two words
        String lastTwoWords = String.join(" ", words.subList(words.size() - 2, words.size()));

        // Build the final shortened name
        StringBuilder shortenedName = new StringBuilder();
        shortenedName.append(firstWord).append(" ")
            .append(shortForm).append(" ");
        if (!restValidWords.isEmpty()) {
            shortenedName.append(restValidWords).append(" ");
        }
        shortenedName.append(lastTwoWords);

        return shortenedName.toString();
    }*/

    public static String shortenName(String name) {
      // Split the name into words
      String[] words = name.split("\\s+");

      // If there are 5 or fewer words, return the original name
      if (words.length <= 5) {
        return name;
      }

      // Join the first 5 words into a single string
      StringBuilder shortenedName = new StringBuilder();
      for (int i = 0; i < 5; i++) {
        shortenedName.append(words[i]);
        if (i < 4) {
          shortenedName.append(" ");
        }
      }

      return shortenedName.toString();
    }
}
