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
    SHRIdMappingDao shrIdMappingDao;
    private DiagnosisIdMappingDao diagnosisIdMappingDao;
    private OrderIdMappingDao orderIdMappingDao;
    private ProviderIdMappingDao providerIdMappingDao;
    Database database;
    Logger logger = Logger.getLogger(IdMappingRepository.class);


    @Autowired
    public IdMappingRepository(Database database) {
        this.database = database;
        this.encounterIdMappingDao = new EncounterIdMappingDao(database);
        this.patientIdMappingDao = new PatientIdMappingDao(database);
        this.shrIdMappingDao = new SHRIdMappingDao(database);
        this.diagnosisIdMappingDao = new DiagnosisIdMappingDao(database);
        this.orderIdMappingDao = new OrderIdMappingDao(database);
        this.providerIdMappingDao = new ProviderIdMappingDao(database);
    }

    public void saveOrUpdateIdMapping(IdMapping idMapping) {
        idMappingDao(idMapping.getType()).saveOrUpdateIdMapping(idMapping);
    }

    public IdMapping findByExternalId(String externalId, String idMappingType) {
        return idMappingDao(idMappingType).findByExternalId(externalId);
    }

    public List<IdMapping> findMappingsByExternalId(String externalId, String idMappingType) {
        return idMappingDao(idMappingType).findMappingsByExternalId(externalId);
    }

    public IdMapping findByInternalId(String internalId, String idMappingType) {
        return idMappingDao(idMappingType).findByInternalId(internalId);
    }

    public List<IdMapping> findByHealthId(String healthId, String idMappingType) {
        return idMappingDao(idMappingType).findByHealthId(healthId);
    }

    public void replaceHealthId(final String toBeReplaced, final String toReplaceWith) {
        final List<IdMapping> reassignedEncounterIdMappings = updateHealthIds(findByHealthId(toBeReplaced, IdMappingType.ENCOUNTER), toBeReplaced, toReplaceWith);
        final List<IdMapping> reassignedMedicationRequestIdMappings = updateHealthIds(findByHealthId(toBeReplaced, IdMappingType.MEDICATION_ORDER), toBeReplaced, toReplaceWith);
//        not required now with type procedureOrder as medication order will take care of all orders.
//        final List<IdMapping> reassignedProcedureOrderIdMappings = updateHealthIds(findByHealthId(toBeReplaced, IdMappingType.PROCEDURE_ORDER), toBeReplaced, toReplaceWith);
        final List<IdMapping> reassignedDiagnosisIdMappings = updateHealthIds(findByHealthId(toBeReplaced, IdMappingType.DIAGNOSIS), toBeReplaced, toReplaceWith);
        database.executeInTransaction(new Database.TxWork<Object>() {
            @Override
            public Object execute(Connection connection) {
                PreparedStatement updateEncounterIdMappingBatch = null;
                PreparedStatement updateMedicationRequestMappingBatch = null;
                PreparedStatement updateProcedureOrderIdMappingBatch = null;
                PreparedStatement updateDiagnosisIdMappingBatch = null;
                try {
                    updateEncounterIdMappingBatch = idMappingDao(IdMappingType.ENCOUNTER).getBatchStatement(connection, reassignedEncounterIdMappings);
                    updateMedicationRequestMappingBatch = idMappingDao(IdMappingType.MEDICATION_ORDER).getBatchStatement(connection, reassignedMedicationRequestIdMappings);
//                    updateProcedureOrderIdMappingBatch = idMappingDao(IdMappingType.PROCEDURE_ORDER).getBatchStatement(connection, reassignedProcedureOrderIdMappings);
                    updateDiagnosisIdMappingBatch = idMappingDao(IdMappingType.DIAGNOSIS).getBatchStatement(connection, reassignedDiagnosisIdMappings);
                    executeBatch(updateEncounterIdMappingBatch, updateMedicationRequestMappingBatch, updateDiagnosisIdMappingBatch, updateProcedureOrderIdMappingBatch);
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while replacing healthids of id mapping", e);
                } finally {
                    try {
                        close(updateMedicationRequestMappingBatch, updateEncounterIdMappingBatch);
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
            return orderIdMappingDao;
        else if (IdMappingType.DIAGNOSIS.equals(idMappingType))
            return diagnosisIdMappingDao;
        else if (IdMappingType.PROCEDURE_REQUEST.equals(idMappingType))
            return orderIdMappingDao;
//        else if (IdMappingType.DIAGNOSTIC_ORDER.equals(idMappingType))
//            return orderIdMappingDao;
        else if (IdMappingType.PROVIDER.equals(idMappingType))
            return providerIdMappingDao;
        else
            return shrIdMappingDao;
    }
}
