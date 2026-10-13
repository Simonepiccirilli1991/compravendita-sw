package it.compravendita.gestionale.esportazioni;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * File Excel (.xlsx) con un foglio per tabella: intestazioni in grassetto, filtri, riga
 * bloccata e valori numerici veri (importi, date, percentuali) per poterci fare calcoli.
 */
public final class ExcelScrittore {

	private static final int LARGHEZZA_MASSIMA = 60 * 256;

	private ExcelScrittore() {
	}

	public static byte[] scrivi(List<Tabella> tabelle) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Map<Tabella.Tipo, CellStyle> stili = stili(workbook);
			CellStyle intestazione = intestazione(workbook);
			for (Tabella tabella : tabelle) {
				foglio(workbook, tabella, stili, intestazione);
			}
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Impossibile creare il file Excel", ex);
		}
	}

	private static void foglio(XSSFWorkbook workbook, Tabella tabella, Map<Tabella.Tipo, CellStyle> stili,
			CellStyle intestazione) {
		Sheet foglio = workbook.createSheet(WorkbookUtil.createSafeSheetName(tabella.getNome()));
		List<Tabella.Colonna> colonne = tabella.getColonne();
		Row testata = foglio.createRow(0);
		for (int i = 0; i < colonne.size(); i++) {
			Cell cella = testata.createCell(i);
			cella.setCellValue(colonne.get(i).titolo());
			cella.setCellStyle(intestazione);
		}
		int numero = 1;
		for (List<Object> valori : tabella.getRighe()) {
			Row riga = foglio.createRow(numero++);
			for (int i = 0; i < valori.size(); i++) {
				cella(riga.createCell(i), valori.get(i), stili.get(colonne.get(i).tipo()));
			}
		}
		foglio.createFreezePane(0, 1);
		if (!colonne.isEmpty()) {
			foglio.setAutoFilter(new CellRangeAddress(0, Math.max(0, numero - 1), 0, colonne.size() - 1));
		}
		for (int i = 0; i < colonne.size(); i++) {
			foglio.autoSizeColumn(i);
			// spazio per la freccia del filtro, senza colonne enormi per le note lunghe
			foglio.setColumnWidth(i, Math.min(foglio.getColumnWidth(i) + 3 * 256, LARGHEZZA_MASSIMA));
		}
	}

	private static void cella(Cell cella, Object valore, CellStyle stile) {
		switch (valore) {
			case null -> cella.setBlank();
			case BigDecimal numero -> cella.setCellValue(numero.doubleValue());
			case Number numero -> cella.setCellValue(numero.doubleValue());
			case LocalDate data -> cella.setCellValue(data);
			case Boolean b -> cella.setCellValue(b ? "Sì" : "No");
			default -> cella.setCellValue(valore.toString());
		}
		if (stile != null && valore != null) {
			cella.setCellStyle(stile);
		}
	}

	private static Map<Tabella.Tipo, CellStyle> stili(XSSFWorkbook workbook) {
		Map<Tabella.Tipo, CellStyle> stili = new EnumMap<>(Tabella.Tipo.class);
		stili.put(Tabella.Tipo.IMPORTO, formato(workbook, "#,##0.00 \"€\";-#,##0.00 \"€\""));
		stili.put(Tabella.Tipo.PERCENTUALE, formato(workbook, "0.0\"%\""));
		stili.put(Tabella.Tipo.DECIMALE, formato(workbook, "0.##"));
		stili.put(Tabella.Tipo.INTERO, formato(workbook, "0"));
		stili.put(Tabella.Tipo.DATA, formato(workbook, "dd/mm/yyyy"));
		return stili;
	}

	private static CellStyle formato(XSSFWorkbook workbook, String formato) {
		CellStyle stile = workbook.createCellStyle();
		stile.setDataFormat(workbook.createDataFormat().getFormat(formato));
		return stile;
	}

	private static CellStyle intestazione(XSSFWorkbook workbook) {
		Font grassetto = workbook.createFont();
		grassetto.setBold(true);
		CellStyle stile = workbook.createCellStyle();
		stile.setFont(grassetto);
		stile.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		stile.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return stile;
	}

}
