/**
 * Copyright (C) 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dashbuilder.dataprovider.backend.csv;

import java.io.InputStream;

import org.dashbuilder.dataset.def.CSVDataSetDef;

/**
 * Interface for getting access to CSV data set definition files
 */
public interface CSVFileStorage {

    /**
     * Get an input stream for accessing the CSV file attached to the data set definition
     * @param def The CSV data set definition
     * @return An input stream for getting the CSV content
     */
    InputStream getCSVInputStream(CSVDataSetDef def);

    /**
     * Saves the CSV file
     * @param def The CSV data set definition
     */
    void saveCSVFile(CSVDataSetDef def);

    /**
     * Deletes the CSV file
     * @param def The CSV data set definition
     */
    void deleteCSVFile(CSVDataSetDef def);
}
