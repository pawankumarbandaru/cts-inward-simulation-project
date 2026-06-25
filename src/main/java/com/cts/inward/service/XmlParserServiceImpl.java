package com.cts.inward.service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.cts.inward.dto.ChequeDTO;

public class XmlParserServiceImpl implements XmlParserService {

	// ── CHANGE 2 : Added date formatters to support chequeDate parsing ────
	// Supports multiple date formats in XML so the parser is flexible
	private static final DateTimeFormatter[] DATE_FORMATS = { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd"),
			DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy") };
	// ─────────────────────────────────────────────────────────────────────

	@Override
	public List<ChequeDTO> parseXml(File xmlFile) {

		List<ChequeDTO> chequeList = new ArrayList<>();

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(xmlFile);
			document.getDocumentElement().normalize();

			// Read batchId from XML
			String batchId = "";
			NodeList batchNodes = document.getElementsByTagName("batchId");
			if (batchNodes.getLength() > 0) {
				batchId = batchNodes.item(0).getTextContent().trim();
			}

			NodeList chequeNodes = document.getElementsByTagName("cheque");

			for (int i = 0; i < chequeNodes.getLength(); i++) {

				Element chequeElement = (Element) chequeNodes.item(i);
				ChequeDTO dto = new ChequeDTO();

				dto.setBatchId(batchId);

				// Core fields — unchanged from your original parser
				dto.setChequeNumber(getTagValue(chequeElement, "chequeNumber"));
				dto.setAccountNumber(getTagValue(chequeElement, "accountNumber"));
				dto.setDrawerName(getTagValue(chequeElement, "drawerName"));
				dto.setPayeeName(getTagValue(chequeElement, "payeeName"));
				dto.setMicrCode(getTagValue(chequeElement, "micrCode"));
				dto.setTransactionCode(getTagValue(chequeElement, "transactionCode"));
				dto.setBranchName(getTagValue(chequeElement, "branchName"));
				dto.setPresentingBank(getTagValue(chequeElement, "presentingBank"));
				dto.setFrontImage(getTagValue(chequeElement, "frontImage"));
				dto.setBackImage(getTagValue(chequeElement, "backImage"));

				// Amount — parsed as Double
				String amount = getTagValue(chequeElement, "amount");
				if (amount != null && !amount.isEmpty()) {
					dto.setAmount(Double.parseDouble(amount));
				}

				// ── CHANGE 2 : Parse amountInWords, ifscCode, chequeDate ──
				// errorReason → NOT parsed (set only during MICR repair)
				// clearingDate → NOT parsed (set only after clearing)
				// decision → NOT parsed (set only by checker)
				dto.setAmountInWords(getTagValue(chequeElement, "amountInWords"));
				dto.setIfscCode(getTagValue(chequeElement, "ifscCode"));
				dto.setChequeDate(parseDate(getTagValue(chequeElement, "chequeDate")));
				// ─────────────────────────────────────────────────────────

				chequeList.add(dto);
			}

			System.out.println("Total Cheques Parsed : " + chequeList.size());

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to parse XML file : " + e.getMessage(), e);
		}

		return chequeList;
	}

	// ── Helper : safely read a tag value from an element ─────────────────
	private String getTagValue(Element element, String tagName) {
		NodeList nodes = element.getElementsByTagName(tagName);
		if (nodes == null || nodes.getLength() == 0 || nodes.item(0) == null) {
			return null;
		}
		String value = nodes.item(0).getTextContent().trim();
		return value.isEmpty() ? null : value;
	}

	// ── CHANGE 2 : Date parsing helper ────────────────────────────────────
	// Tries each format in DATE_FORMATS, then falls back to date-only + midnight
	private LocalDateTime parseDate(String value) {
		if (value == null || value.isEmpty())
			return null;

		for (DateTimeFormatter fmt : DATE_FORMATS) {
			try {
				return LocalDateTime.parse(value, fmt);
			} catch (DateTimeParseException ignored) {
			}
		}

		// Last fallback — date-only string, append midnight time
		try {
			return LocalDateTime.parse(value + " 00:00:00",
					DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.LENIENT));
		} catch (DateTimeParseException ignored) {
		}

		System.err.println("Warning : Could not parse date value: " + value);
		return null;
	}
	// ─────────────────────────────────────────────────────────────────────
}