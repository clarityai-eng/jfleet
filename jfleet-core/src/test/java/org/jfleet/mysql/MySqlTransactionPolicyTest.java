/**
 * Copyright 2017 Jerónimo López Bezanilla
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jfleet.mysql;

import static org.jfleet.util.TransactionPolicyTestHelper.employeesWithForeignKeyError;
import static org.jfleet.util.TransactionPolicyTestHelper.employeesWithMultipleConstraintsErrors;
import static org.jfleet.util.TransactionPolicyTestHelper.employeesWithOutErrors;
import static org.jfleet.util.TransactionPolicyTestHelper.employeesWithUniqueError;
import static org.jfleet.util.TransactionPolicyTestHelper.numberOfRowsInEmployeeTable;
import static org.jfleet.util.TransactionPolicyTestHelper.setupDatabase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

import org.jfleet.BulkInsert;
import org.jfleet.JFleetException;
import org.jfleet.entities.Employee;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySqlTransactionPolicyTest {

    private static Logger logger = LoggerFactory.getLogger(MySqlTransactionPolicyTest.class);
    private static final long VERY_LOW_SIZE_TO_FREQUENT_LOAD_DATA = 10;

    @Test
    public void longTransactionExecuteMultipleLoadDataOperationsTransactionaly()
            throws IOException, SQLException, JFleetException {
        Supplier<Connection> provider = new MySqlTestConnectionProvider();
        try (Connection connection = provider.get()) {
            setupDatabase(connection);
            connection.setAutoCommit(false);

            BulkInsert<Employee> bulkInsert = new LoadDataBulkInsert<>(Employee.class,
                    VERY_LOW_SIZE_TO_FREQUENT_LOAD_DATA, true, true);

            bulkInsert.insertAll(connection, employeesWithOutErrors());

            // We don't know how many load data operations were executed, but with
            // low batch size, multiple load data are executed with few records.
            assertEquals(7, numberOfRowsInEmployeeTable(connection));
            connection.rollback();
            assertEquals(0, numberOfRowsInEmployeeTable(connection));
        }
    }

    @Test
    public void inLongTransactionWithMissedForeignKeyCanBeRollbacked() throws IOException, SQLException {
        Supplier<Connection> provider = new MySqlTestConnectionProvider();
        try (Connection connection = provider.get()) {
            setupDatabase(connection);
            connection.setAutoCommit(false);

            BulkInsert<Employee> bulkInsert = new LoadDataBulkInsert<>(Employee.class,
                    VERY_LOW_SIZE_TO_FREQUENT_LOAD_DATA, true, true);

            try {
                bulkInsert.insertAll(connection, employeesWithForeignKeyError());
                connection.commit();
            } catch (JFleetException e) {
                connection.rollback();
            }
            assertEquals(0, numberOfRowsInEmployeeTable(connection));
        }
    }

    @Test
    public void inLongTransactionWithMissedForeignKeyCanBeSkipped() throws IOException, SQLException, JFleetException {
        Supplier<Connection> provider = new MySqlTestConnectionProvider();
        try (Connection connection = provider.get()) {
            setupDatabase(connection);
            connection.setAutoCommit(false);

            BulkInsert<Employee> bulkInsert = new LoadDataBulkInsert<>(Employee.class,
                    VERY_LOW_SIZE_TO_FREQUENT_LOAD_DATA, true, false);

            bulkInsert.insertAll(connection, employeesWithForeignKeyError());
            assertEquals(6, numberOfRowsInEmployeeTable(connection));
            connection.rollback();
            assertEquals(0, numberOfRowsInEmployeeTable(connection));
        }
    }

    @Test
    public void inLongTransactionWithDuplicatedIdCanBeRollbacked() throws IOException, SQLException {
        Supplier<Connection> provider = new MySqlTestConnectionProvider();
        try (Connection connection = provider.get()) {
            setupDatabase(connection);
            connection.setAutoCommit(false);

            BulkInsert<Employee> bulkInsert = new LoadDataBulkInsert<>(Employee.class,
                    VERY_LOW_SIZE_TO_FREQUENT_LOAD_DATA, true, true);

            try {
                bulkInsert.insertAll(connection, employeesWithUniqueError());
                connection.commit();
            } catch (JFleetException e) {
                connection.rollback();
            }
            assertEquals(0, numberOfRowsInEmployeeTable(connection));
        }
    }

    @Test
    public void inLongTransactionWithDuplicatedIdCanBeSkipped() throws IOException, SQLException, JFleetException {
        Supplier<Connection> provider = new MySqlTestConnectionProvider();
        try (Connection connection = provider.get()) {
            setupDatabase(connection);
            connection.setAutoCommit(false);

            BulkInsert<Employee> bulkInsert = new LoadDataBulkInsert<>(Employee.class,
                    VERY_LOW_SIZE_TO_FREQUENT_LOAD_DATA, true, false);

            bulkInsert.insertAll(connection, employeesWithUniqueError());
            assertEquals(4, numberOfRowsInEmployeeTable(connection));
            connection.rollback();
            assertEquals(0, numberOfRowsInEmployeeTable(connection));
        }
    }

    @Test
    public void multipleBatchOperationsExecuteMultipleLoadDataOperationsWithHisOwnTransaction()
            throws IOException, SQLException {
        Supplier<Connection> provider = new MySqlTestConnectionProvider();
        try (Connection connection = provider.get()) {
            setupDatabase(connection);

            BulkInsert<Employee> bulkInsert = new LoadDataBulkInsert<>(Employee.class,
                    VERY_LOW_SIZE_TO_FREQUENT_LOAD_DATA, false, true);

            try {
                bulkInsert.insertAll(connection, employeesWithForeignKeyError());
            } catch (JFleetException e) {
                logger.info("Expected error on missed FK");
            }
            assertTrue(numberOfRowsInEmployeeTable(connection) > 0);
        }
    }

    @Test
    public void multipleBatchOperationsCanMissRows() throws IOException, SQLException {
        Supplier<Connection> provider = new MySqlTestConnectionProvider();
        try (Connection connection = provider.get()) {
            setupDatabase(connection);

            BulkInsert<Employee> bulkInsert = new LoadDataBulkInsert<>(Employee.class,
                    VERY_LOW_SIZE_TO_FREQUENT_LOAD_DATA, false, false);

            try {
                bulkInsert.insertAll(connection, employeesWithMultipleConstraintsErrors());
            } catch (JFleetException e) {
                logger.info("Expected error on constraint error");
            }
            assertEquals(6, numberOfRowsInEmployeeTable(connection));
        }
    }

}
