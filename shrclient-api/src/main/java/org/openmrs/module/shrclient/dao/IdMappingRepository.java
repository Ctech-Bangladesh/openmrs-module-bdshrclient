package org.openmrs.module.shrclient.dao;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

@Component
public class IdMappingRepository {

    EncounterIdMappingDao encounterIdMappingDao;
    PatientIdMappingDao patientIdMappingDao;
    MedicationOrderIdMappingDao medicationOrderIdMappingDao;
    SHRIdMappingDao shrIdMappingDao;
    Database database;
    Logger logger = Logger.getLogger(IdMappingRepository.class);


    @Autowired
    public IdMappingRepository(Database database) {
        this.database = database;
        this.encounterIdMappingDao = new EncounterIdMappingDao(database);
        this.patientIdMappingDao = new PatientIdMappingDao(database);
        this.medicationOrderIdMappingDao = new MedicationOrderIdMappingDao(database);
        this.shrIdMappingDao = new SHRIdMappingDao(database);
    }

    public void saveOrUpdateIdMapping(IdMapping idMapping) {
        idMappingDao(idMapping.getType()).saveOrUpdateIdMapping(idMapping);
    }

    public IdMapping findByExternalId(String externalId, String idMappingType) {
        return idMappingDao(idMappingType).findByExternalId(externalId);
    }

    public IdMapping findByInternalId(String internalId, String idMappingType) {
        return idMappingDao(idMappingType).findByInternalId(internalId);
    }

    public List<IdMapping> findByHealthId(String healthId, String idMappingType) {
        return idMappingDao(idMappingType).findByHealthId(healthId);
    }

    public void replaceHealthId(final String toBeReplaced, final String toReplaceWith) {
        final List<IdMapping> reassignedEncounterIdMappings = updateHealthIds(findByHealthId(toBeReplaced, IdMappingType.ENCOUNTER), toBeReplaced, toReplaceWith);
        final List<IdMapping> reassignedMedicationOrderIdMappings = updateHealthIds(findByHealthId(toBeReplaced, IdMappingType.MEDICATION_ORDER), toBeReplaced, toReplaceWith);
        database.executeInTransaction(new Database.TxWork<Object>() {
            @Override
            public Object execute(Connection connection) {
                PreparedStatement updateEncounterIdMappingBatch = null;
                PreparedStatement updateMedicationOrderMappingBatch = null;
                try {
                    updateEncounterIdMappingBatch = idMappingDao(IdMappingType.ENCOUNTER).getBatchStatement(connection, reassignedEncounterIdMappings);
                    updateMedicationOrderMappingBatch = idMappingDao(IdMappingType.MEDICATION_ORDER).getBatchStatement(connection, reassignedMedicationOrderIdMappings);
                    executeBatch(updateEncounterIdMappingBatch, updateMedicationOrderMappingBatch);
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while replacing healthids of id mapping", e);
                } finally {
                    try {
                        close(updateMedicationOrderMappingBatch, updateEncounterIdMappingBatch);
                    } catch (SQLException e) {
                        logger.warn("Could not close db statement or resultset", e);
                    }
                }
                return null;
            }
        });
    }

    private List<IdMapping> updateHealthIds(List<IdMapping> idMappings, String originalHID, String replaceHID) {
        Date modifiedDate = new Date();
        for (IdMapping idMapping : idMappings) {
            String uri = idMapping.getUri();
            idMapping.setUri(uri.replace(originalHID, replaceHID));
            idMapping.setLastSyncDateTime(modifiedDate);
        }
        return idMappings;
    }

    private void close(Statement... statements) throws SQLException {
        for (Statement statement : statements) {
            if (statement != null)
                statement.close();
        }
    }

    private void executeBatch(Statement... statements) throws SQLException {
        for (Statement statement : statements) {
            if (statement != null) {
                statement.executeBatch();
            }
        }

    }

    private IdMappingDao idMappingDao(String idMappingType) {
        if (IdMappingType.ENCOUNTER.equals(idMappingType))
            return encounterIdMappingDao;
        else if (IdMappingType.PATIENT.equals(idMappingType))
            return patientIdMappingDao;
        else if (IdMappingType.MEDICATION_ORDER.equals(idMappingType))
            return medicationOrderIdMappingDao;
        else
            return shrIdMappingDao;
    }
}
