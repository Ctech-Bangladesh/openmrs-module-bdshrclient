package org.openmrs.module.shrclient.dao;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.model.AddressHierarchyEntryTranslation;
import org.openmrs.module.shrclient.util.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class AddressHierarchyEntryTranslationRepository {
    private static final String ADDRESS_HIERARCHY_TRANSLATION_TABLE = "address_hierarchy_entry_translation";
    private Logger logger = Logger.getLogger(AddressHierarchyEntryTranslationRepository.class);

    private Database database;

    @Autowired
    public AddressHierarchyEntryTranslationRepository(Database database) {
        this.database = database;
    }

    public void save(final AddressHierarchyEntryTranslation entryTranslation) {

        database.executeInTransaction(new Database.TxWork<Object>() {
            @Override
            public Object execute(Connection connection) {
                String deleteQuery = "DELETE FROM " + ADDRESS_HIERARCHY_TRANSLATION_TABLE + " WHERE address_hierarchy_entry_id=?";
                String createQuery = "INSERT INTO " + ADDRESS_HIERARCHY_TRANSLATION_TABLE + " (address_hierarchy_entry_id, local_name) VALUES (?,?)";

                PreparedStatement deleteStatement = null;
                PreparedStatement createStatement = null;
                try {

                    deleteStatement = connection.prepareStatement(deleteQuery);
                    deleteStatement.setInt(1, entryTranslation.getId());
                    deleteStatement.execute();

                    createStatement = connection.prepareStatement(createQuery);
                    createStatement.setInt(1, entryTranslation.getId());
                    createStatement.setString(2, entryTranslation.getLocalName());
                    createStatement.execute();

                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while creating address hierarchy entry translation", e);
                } finally {
                    try {
                        if (deleteStatement != null) deleteStatement.close();
                        if (createStatement != null) createStatement.close();
                    } catch (SQLException e) {
                        logger.warn("Could not close db createStatement, deleteStatement or resultset", e);
                    }
                }
                return null;
            }
        });

    }

    public AddressHierarchyEntryTranslation get(final int addressHierarchyEntryId) {
        return database.executeInTransaction(new Database.TxWork<AddressHierarchyEntryTranslation>() {
            @Override
            public AddressHierarchyEntryTranslation execute(Connection connection) {
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                try {
                    statement = connection.prepareStatement("SELECT address_hierarchy_entry_id, local_name FROM " + ADDRESS_HIERARCHY_TRANSLATION_TABLE + " WHERE address_hierarchy_entry_id = ?");
                    statement.setInt(1, addressHierarchyEntryId);

                    resultSet = statement.executeQuery();
                    if (resultSet.next()) {
                        int entryId = resultSet.getInt("address_hierarchy_entry_id");
                        String localName = resultSet.getString("local_name");
                        return new AddressHierarchyEntryTranslation(entryId, localName);

                    }
                } catch (SQLException e) {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                    } catch (SQLException s) {
                        logger.warn("Could not close db statement or result set", s);
                    }
                }
                return null;
            }
        });
    }
}
