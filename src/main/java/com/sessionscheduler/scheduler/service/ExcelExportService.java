package com.sessionscheduler.scheduler.service;

import com.sessionscheduler.scheduler.model.ExamSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** Builds the .xlsx workbook supervisors download to allot proctors. */
@Service
public class ExcelExportService {
    private final TimeFormat fmt;

    public ExcelExportService(TimeFormat fmt) {
        this.fmt = fmt;
    }

    public byte[] export(List<ExamSession> sessions) {
        String zone = fmt.displayZone().getId();
        String[] head = {
            "Booking ID", "Student", "Student email", "Date (" + zone + ")", "Time (" + zone + ")",
            "Student's date and time", "Student's time zone", "Proctor status", "Proctor", "Proctor email",
            "Booked at (" + zone + ")"
        };
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Bookings");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle headStyle = wb.createCellStyle();
            headStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < head.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(head[i]);
                c.setCellStyle(headStyle);
            }
            int r = 1;
            for (ExamSession s : sessions) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(s.getId());
                row.createCell(1).setCellValue(s.getStudent().getFullName());
                row.createCell(2).setCellValue(s.getStudent().getEmail());
                row.createCell(3).setCellValue(fmt.displayPart(s.getStartsAt(), "EEE d MMM yyyy"));
                row.createCell(4).setCellValue(fmt.displayPart(s.getStartsAt(), "HH:mm"));
                row.createCell(5).setCellValue(fmt.inZone(s.getStartsAt(), s.getTimeZone()));
                row.createCell(6).setCellValue(UsTimeZones.options().getOrDefault(s.getTimeZone(), s.getTimeZone()));
                row.createCell(7).setCellValue(s.getStatus().getLabel());
                row.createCell(8).setCellValue(s.getProctor() == null ? "" : s.getProctor().getFullName());
                row.createCell(9).setCellValue(s.getProctor() == null ? "" : s.getProctor().getEmail());
                row.createCell(10).setCellValue(fmt.display(s.getCreatedAt()));
            }
            sheet.createFreezePane(0, 1);
            int[] widths = {12, 24, 30, 20, 12, 32, 30, 16, 24, 30, 26};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
