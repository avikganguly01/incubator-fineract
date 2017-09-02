/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.bulkimport.importhandler.office;

import java.util.ArrayList;
import java.util.List;

import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.bulkimport.constants.OfficeConstants;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.infrastructure.bulkimport.importhandler.ImportHandler;
import org.apache.fineract.infrastructure.bulkimport.importhandler.ImportHandlerUtils;
import org.apache.fineract.infrastructure.bulkimport.importhandler.helper.DateSerializer;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.organisation.office.data.OfficeData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.GsonBuilder;

@Service
public class OfficeImportHandler implements ImportHandler {
	
	private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    public OfficeImportHandler(final PortfolioCommandSourceWritePlatformService
    		commandsSourceWritePlatformService) {
    		this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
    }
    
    @Override
	public void process(final Workbook workbook, final String locale, final String dateFormat) {
    		final List<OfficeData> offices = readExcelFile(workbook, locale, dateFormat);
    		upload(workbook, offices);
	}

    private List<OfficeData> readExcelFile(final Workbook workbook, final String locale, final String dateFormat) {
    		final List<OfficeData> offices = new ArrayList<> ();
        Sheet officeSheet=workbook.getSheet(OfficeConstants.OFFICE_WORKBOOK_SHEET_NAME);
        Integer noOfEntries = ImportHandlerUtils.getNumberOfRows(officeSheet,0);
        for (int rowIndex=1;rowIndex<noOfEntries;rowIndex++) {
            Row row;
                row=officeSheet.getRow(rowIndex);
                if (ImportHandlerUtils.isNotImported(row, OfficeConstants.STATUS_COL)){
                    offices.add(readOffice(row, locale, dateFormat));
                }
        }
        return offices;
    }

    private OfficeData readOffice(Row row, final String locale, final String dateFormat) {
        String officeName = ImportHandlerUtils.readAsString(OfficeConstants.OFFICE_NAME_COL,row);
        Long parentId= ImportHandlerUtils.readAsLong(OfficeConstants.PARENT_OFFICE_ID_COL,row);
        LocalDate openedDate= ImportHandlerUtils.readAsDate(OfficeConstants.OPENED_ON_COL,row, dateFormat);
        String externalId= ImportHandlerUtils.readAsString(OfficeConstants.EXTERNAL_ID_COL,row);
        OfficeData office = OfficeData.importInstance(officeName,parentId,openedDate,externalId);
        office.setImportFields(row.getRowNum(), locale, dateFormat);
        return office;
    }

    private void upload(final Workbook workbook, final List<OfficeData> offices) {
        Sheet clientSheet = workbook.getSheet(OfficeConstants.OFFICE_WORKBOOK_SHEET_NAME);
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(LocalDate.class, new DateSerializer());
        for (OfficeData office: offices) {
            try {
                String payload = gsonBuilder.create().toJson(office);
                final CommandWrapper commandRequest = new CommandWrapperBuilder() //
                        .createOffice() //
                        .withJson(payload) //
                        .build(); //
                final CommandProcessingResult result = commandsSourceWritePlatformService.logCommandSource(commandRequest);
                Cell statusCell = clientSheet.getRow(office.getRowIndex()).createCell(OfficeConstants.STATUS_COL);
                statusCell.setCellValue(TemplatePopulateImportConstants.STATUS_CELL_IMPORTED);
                statusCell.setCellStyle(ImportHandlerUtils.getCellStyle(workbook, IndexedColors.LIGHT_GREEN));
            } catch (RuntimeException e) {
                e.printStackTrace();
                String message="";
                if (e.getMessage()!=null)
                		message = ImportHandlerUtils.parseStatus(e.getMessage());
                Cell statusCell = clientSheet.getRow(office.getRowIndex()).createCell(OfficeConstants.STATUS_COL);
                statusCell.setCellValue(message);
                statusCell.setCellStyle(ImportHandlerUtils.getCellStyle(workbook, IndexedColors.RED));

            }
        }
        clientSheet.setColumnWidth(OfficeConstants.STATUS_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        ImportHandlerUtils.writeString(OfficeConstants.STATUS_COL, clientSheet.getRow(0), TemplatePopulateImportConstants.STATUS_COLUMN_HEADER_NAME);
    }

}