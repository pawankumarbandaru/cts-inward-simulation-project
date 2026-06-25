package com.cts.inward.dto;

import java.time.LocalDateTime;

public class ChequeDTO {

	private String batchId;
	private String chequeNumber;
	private String accountNumber;
	private Double amount;
	private String drawerName;
	private String payeeName;
	private String micrCode;
	private String transactionCode;
	private String branchName;
	private String presentingBank;
	private String frontImage;
	private String backImage;

	// new fields
	private String amountInWords;
	private String ifscCode;
	private LocalDateTime chequeDate;
	private LocalDateTime clearingDate;

	public String getBatchId() {
		return batchId;
	}

	public void setBatchId(String batchId) {
		this.batchId = batchId;
	}

	public String getChequeNumber() {
		return chequeNumber;
	}

	public void setChequeNumber(String chequeNumber) {
		this.chequeNumber = chequeNumber;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNumber = accountNumber;
	}

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public String getDrawerName() {
		return drawerName;
	}

	public void setDrawerName(String drawerName) {
		this.drawerName = drawerName;
	}

	public String getPayeeName() {
		return payeeName;
	}

	public void setPayeeName(String payeeName) {
		this.payeeName = payeeName;
	}

	public String getMicrCode() {
		return micrCode;
	}

	public void setMicrCode(String micrCode) {
		this.micrCode = micrCode;
	}

	public String getTransactionCode() {
		return transactionCode;
	}

	public void setTransactionCode(String transactionCode) {
		this.transactionCode = transactionCode;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public String getPresentingBank() {
		return presentingBank;
	}

	public void setPresentingBank(String presentingBank) {
		this.presentingBank = presentingBank;
	}

	public String getFrontImage() {
		return frontImage;
	}

	public void setFrontImage(String frontImage) {
		this.frontImage = frontImage;
	}

	public String getBackImage() {
		return backImage;
	}

	public void setBackImage(String backImage) {
		this.backImage = backImage;
	}

	public String getAmountInWords() {
		return amountInWords;
	}

	public void setAmountInWords(String amountInWords) {
		this.amountInWords = amountInWords;
	}

	public String getIfscCode() {
		return ifscCode;
	}

	public void setIfscCode(String ifscCode) {
		this.ifscCode = ifscCode;
	}

	public LocalDateTime getChequeDate() {
		return chequeDate;
	}

	public void setChequeDate(LocalDateTime chequeDate) {
		this.chequeDate = chequeDate;
	}

	public LocalDateTime getClearingDate() {
		return clearingDate;
	}

	public void setClearingDate(LocalDateTime clearingDate) {
		this.clearingDate = clearingDate;
	}
}