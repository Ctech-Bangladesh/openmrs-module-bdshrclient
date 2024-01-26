package org.openmrs.module.shrclient.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.bidimap.UnmodifiableBidiMap;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

public class BbsCodeDao {

    private static final Logger log = Logger.getLogger(BbsCodeDao.class);

    private static final BbsCodeDao dao = new BbsCodeDao();

    private final Map<String, Map<String, String>> dataSource;
    private final BidiMap<String, String> genderDb;
    private final BidiMap<String, String> educationDb;
    private final BidiMap<String, String> occupationDb;

    private BbsCodeDao() {
        try {
            final URL url = this.getClass().getClassLoader().getResource("bbs-code.json");
            this.dataSource = new ObjectMapper().readValue(url, new TypeReference<Map<String,Map<String, String>>>() {});

            this.genderDb = getDb("gender");
            this.educationDb = getDb("education");
            this.occupationDb = getDb("occupation");

        } catch (IOException e) {
            log.error("Error while creating CodeMappingDao instance. ", e);
            throw new RuntimeException(e);
        }
    }

    private BidiMap<String, String> getDb(String dbName) {
        return UnmodifiableBidiMap.unmodifiableBidiMap(new DualHashBidiMap<String, String>(dataSource.get(dbName)));
    }

    public static BbsCodeDao getInstance() {
        return dao;
    }

    public String getGenderCode(String concept) {
        return this.genderDb.get(concept);
    }

    public String getGenderConcept(String code) {
        return this.genderDb.inverseBidiMap().get(code);
    }

    public String getEducationCode(String concept) {
        return this.educationDb.get(concept);
    }

    public String getEducationConcept(String code) {
        return this.educationDb.inverseBidiMap().get(code);
    }

    public String getOccupationCode(String concept) {
        return this.occupationDb.get(concept);
    }

    public String getOccupationConcept(String code) {
        return this.occupationDb.inverseBidiMap().get(code);
    }
}
