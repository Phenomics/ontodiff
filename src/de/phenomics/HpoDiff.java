package de.phenomics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFCreationHelper;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import ontologizer.ontology.Ontology;
import ontologizer.ontology.Term;
import util.OntologyUtil;

/**
 * 
 * 
 * 
 * @author Sebastian Koehler
 *
 */
public class HpoDiff {

	public static void main(String[] args) throws IOException {

		if (!(args.length == 2)) {
			throw new IllegalArgumentException("must provide two files!");
		}
		String hp1Str = args[0];
		String hp2Str = args[1];

		File f1 = new File(hp1Str);
		if (!(f1.exists())) {
			throw new IllegalArgumentException("file 1 does not exist!");
		}
		if (!(new File(hp2Str).exists())) {
			throw new IllegalArgumentException("file 2 does not exist!");
		}

		Ontology hp1 = OntologyUtil.parseOntology(hp1Str);
		Ontology hp2 = OntologyUtil.parseOntology(hp2Str);

		String hp1dataVersion = hp1.getTermMap().getDataVersion().replaceAll(".+/", "");
		String hp2dataVersion = hp2.getTermMap().getDataVersion().replaceAll(".+/", "");

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDate time1 = LocalDate.parse(hp1dataVersion, formatter);
		LocalDate time2 = LocalDate.parse(hp2dataVersion, formatter);

		/*
		 * Determine which is the older ontology
		 */
		Ontology olderOntology = null;
		Ontology youngerOntology = null;

		if (time1.isBefore(time2)) {
			olderOntology = hp1;
			youngerOntology = hp2;
		}
		else {
			olderOntology = hp2;
			youngerOntology = hp1;
		}

		String dv_old = olderOntology.getTermMap().getDataVersion().replaceAll("releases/", "").replaceAll("\\/", "_");
		String dv_new = youngerOntology.getTermMap().getDataVersion().replaceAll("releases/", "").replaceAll("\\/",
				"_");

		System.out.println("older ontology from " + dv_old);
		System.out.println("younger ontology from " + dv_new);

		String outfile = f1.getParentFile().getAbsolutePath() + File.separatorChar + "hpodiff_" + dv_old + "_to_"
				+ dv_new + ".xlsx";
		System.out.println("write diff to " + outfile);
		createDiff(olderOntology, youngerOntology, outfile);

	}

	private static void createDiff(Ontology olderOntology, Ontology youngerOntology, String outfile)
			throws IOException {

		int defaultColumnWidth = 25;
		XSSFWorkbook wb = new XSSFWorkbook();
		FileOutputStream fileOut = new FileOutputStream(outfile);
		XSSFCreationHelper createHelper = wb.getCreationHelper();

		XSSFFont bold = wb.createFont();
		bold.setBold(true);
		XSSFCellStyle style = wb.createCellStyle();
		style.setFont(bold);
		/*
		 * New terms report
		 */
		Sheet sheet0 = wb.createSheet("term additions report");
		int rowIndex = 0;
		String[] headersTermAdd = new String[] { "change type", "term id", "term label" };
		rowIndex = createHeaderRow(createHelper, style, rowIndex, sheet0, headersTermAdd);

		for (Term newTerm : youngerOntology) {
			// check if old ontology knew about this term
			if (olderOntology.getTermIncludingAlternatives(newTerm.getIDAsString()) == null) {
				Row row = sheet0.createRow((short) rowIndex++);
				int columnIndex = 0;

				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString("new term"));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(newTerm.getIDAsString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(newTerm.getName()));

			}

		}

		/*
		 * Obsoletions
		 */
		Sheet sheet1 = wb.createSheet("obsoletions report");
		rowIndex = 0;

		String[] headersObsoletions = new String[] { "change type", "old term id", "old term label", "term id updated",
				"term label updated" };
		rowIndex = createHeaderRow(createHelper, style, rowIndex, sheet1, headersObsoletions);

		for (Term oldTerm : olderOntology.getAllTerms()) {
			Term correspondingNewTerm = youngerOntology.getTermIncludingAlternatives(oldTerm.getIDAsString());
			if (correspondingNewTerm == null) {
				correspondingNewTerm = youngerOntology.getTermFromObsoletes(oldTerm.getIDAsString());
				if (correspondingNewTerm == null) {
					throw new RuntimeException(
							"Fatal: cannot find term with ID " + oldTerm.getIDAsString() + " in new ontology");
				}
			}

			boolean isOldObsolete = oldTerm.isObsolete();
			boolean isNewObsolete = correspondingNewTerm.isObsolete();

			if (!(isNewObsolete == isOldObsolete)) {
				Row row = sheet1.createRow((short) rowIndex++);
				int columnIndex = 0;
				if (!isOldObsolete && isNewObsolete) { // normal procedure -> a
														// term got obsoleted

					row.createCell(columnIndex)
							.setCellValue(createHelper.createRichTextString("valid term obsoletion"));

				}
				else {
					row.createCell(columnIndex)
							.setCellValue(createHelper.createRichTextString("invalid term obsoletion"));

				}
				columnIndex++;
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getIDAsString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getName()));
				row.createCell(columnIndex++)
						.setCellValue(createHelper.createRichTextString(correspondingNewTerm.getIDAsString()));
				row.createCell(columnIndex++)
						.setCellValue(createHelper.createRichTextString(correspondingNewTerm.getName()));
			}
		}

		Sheet sheet2 = wb.createSheet("primary labels report");
		rowIndex = 0;

		String[] headersLabel = new String[] { "change type", "term id", "term label", "term id updated",
				"term label updated" };
		rowIndex = createHeaderRow(createHelper, style, rowIndex, sheet2, headersLabel);

		// label changes
		for (Term oldTerm : olderOntology) {
			Term correspondingNewTerm = youngerOntology.getTerm(oldTerm.getIDAsString());

			if (correspondingNewTerm == null) {
				correspondingNewTerm = youngerOntology.getTermFromObsoletes(oldTerm.getIDAsString());
				if (correspondingNewTerm == null) {
					throw new RuntimeException(
							"Fatal: cannot find term with ID " + oldTerm.getIDAsString() + " in new ontology");
				}
			}

			if (correspondingNewTerm.isObsolete())
				continue;
			String oldLabel = oldTerm.getName();
			String newLabel = correspondingNewTerm.getName();
			if (!oldLabel.equals(newLabel)) {
				Row row = sheet2.createRow((short) rowIndex++);
				int columnIndex = 0;
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString("term label change"));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getIDAsString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getName()));
				row.createCell(columnIndex++)
						.setCellValue(createHelper.createRichTextString(correspondingNewTerm.getIDAsString()));
				row.createCell(columnIndex++)
						.setCellValue(createHelper.createRichTextString(correspondingNewTerm.getName()));
			}

		}

		Sheet sheet3 = wb.createSheet("synonym report");
		rowIndex = 0;
		String[] headers = new String[] { "change type", "term id", "term label", "previous synonyms",
				"recent synonyms", "synonyms in common", "synonyms only in previous version",
				"synonyms only in recent version" };
		rowIndex = createHeaderRow(createHelper, style, rowIndex, sheet3, headers);

		// synonym changes
		for (Term oldTerm : olderOntology) {
			Term correspondingNewTerm = youngerOntology.getTerm(oldTerm.getIDAsString());
			if (correspondingNewTerm == null) {
				correspondingNewTerm = youngerOntology.getTermFromObsoletes(oldTerm.getIDAsString());
				if (correspondingNewTerm == null) {
					throw new RuntimeException(
							"Fatal: cannot find term with ID " + oldTerm.getIDAsString() + " in new ontology");
				}
			}
			if (correspondingNewTerm.isObsolete())
				continue;
			HashSet<String> oldSynonyms = new HashSet<>(oldTerm.getSynonymsArrayList());
			HashSet<String> newSynonyms = new HashSet<>(correspondingNewTerm.getSynonymsArrayList());
			List<String> result = newSynonyms.stream().filter(elem -> oldSynonyms.contains(elem))
					.collect(Collectors.toList());
			SetView<String> res2 = Sets.difference(oldSynonyms, newSynonyms);
			SetView<String> res3 = Sets.difference(newSynonyms, oldSynonyms);

			if (res2.size() != 0 || res3.size() != 0) {

				Row row = sheet3.createRow((short) rowIndex++);
				int columnIndex = 0;
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString("synonym set change"));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getIDAsString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getName()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldSynonyms.toString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(newSynonyms.toString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(result.toString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(res2.toString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(res3.toString()));

			}

		}

		Sheet sheet4 = wb.createSheet("textdefinition report");
		rowIndex = 0;
		String[] headersTextDef = new String[] { "change type", "term id", "term label", "previous defintion",
				"recent definition" };
		rowIndex = createHeaderRow(createHelper, style, rowIndex, sheet4, headersTextDef);

		// definition changes
		for (Term oldTerm : olderOntology) {
			Term correspondingNewTerm = youngerOntology.getTerm(oldTerm.getIDAsString());
			if (correspondingNewTerm == null) {
				correspondingNewTerm = youngerOntology.getTermFromObsoletes(oldTerm.getIDAsString());
				if (correspondingNewTerm == null) {
					throw new RuntimeException(
							"Fatal: cannot find term with ID " + oldTerm.getIDAsString() + " in new ontology");
				}
			}
			if (correspondingNewTerm.isObsolete())
				continue;

			String def1 = oldTerm.getDefinition();
			String def2 = correspondingNewTerm.getDefinition();
			if (def1 == null)
				def1 = "";
			if (def2 == null)
				def2 = "";

			if (def1.equals(def2))
				continue;

			Row row = sheet4.createRow((short) rowIndex++);
			int columnIndex = 0;
			row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString("text-definition change"));
			row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getIDAsString()));
			row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getName()));
			row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(def1));
			row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(def2));
		}

		Sheet sheet5 = wb.createSheet("subclass structure report");
		rowIndex = 0;

		String[] headersSuperClasses = new String[] { "change type", "term id", "term label",
				"superclasses previous version", "superclasses recent version", "unchanged superclasses",
				"only in previous version", "only in recent version" };
		rowIndex = createHeaderRow(createHelper, style, rowIndex, sheet5, headersSuperClasses);

		// parent changes
		for (Term oldTerm : olderOntology) {
			Term correspondingNewTerm = youngerOntology.getTerm(oldTerm.getIDAsString());
			if (correspondingNewTerm == null) {
				correspondingNewTerm = youngerOntology.getTermFromObsoletes(oldTerm.getIDAsString());
				if (correspondingNewTerm == null) {
					throw new RuntimeException(
							"Fatal: cannot find term with ID " + oldTerm.getIDAsString() + " in new ontology");
				}
			}
			if (correspondingNewTerm.isObsolete())
				continue;

			Set<Term> parentsOld = olderOntology.getTermParents(oldTerm);
			Set<Term> parentsNew = youngerOntology.getTermParents(correspondingNewTerm);
			List<Term> result = parentsNew.stream().filter(elem -> parentsOld.contains(elem))
					.collect(Collectors.toList());
			SetView<Term> res2 = Sets.difference(parentsOld, parentsNew);
			SetView<Term> res3 = Sets.difference(parentsNew, parentsOld);

			if (res2.size() != 0 || res3.size() != 0) {

				Row row = sheet5.createRow((short) rowIndex++);
				int columnIndex = 0;
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString("superclasses change"));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getIDAsString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(oldTerm.getName()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(parentsOld.toString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(parentsNew.toString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(result.toString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(res2.toString()));
				row.createCell(columnIndex++).setCellValue(createHelper.createRichTextString(res3.toString()));

			}

		}

		for (int i = 0; i < wb.getNumberOfSheets(); i++) {
			wb.getSheetAt(i).setDefaultColumnWidth(defaultColumnWidth);
		}

		wb.write(fileOut);
		fileOut.close();
	}

	private static int createHeaderRow(XSSFCreationHelper createHelper, XSSFCellStyle style, int rowIndex, Sheet sheet,
			String[] strings) {
		Row headerrow = sheet.createRow((short) rowIndex++);
		int colIndex = 0;
		for (String s : strings) {
			headerrow.createCell(colIndex++).setCellValue(createHelper.createRichTextString(s));
		}
		for (int i = 0; i < headerrow.getLastCellNum(); i++) {
			Cell cell = headerrow.getCell(i);
			cell.setCellStyle(style);
		}
		return rowIndex;
	}
}
