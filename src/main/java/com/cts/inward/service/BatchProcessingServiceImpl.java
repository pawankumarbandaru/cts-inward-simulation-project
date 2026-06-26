package com.cts.inward.service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.cts.inward.dao.InwardBatchDao;
import com.cts.inward.dao.InwardBatchDaoImpl;
import com.cts.inward.dao.InwardChequeDao;
import com.cts.inward.dao.InwardChequeDaoImpl;
import com.cts.inward.dto.ChequeDTO;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.enums.ChequeStatus;
import com.cts.util.PropertyUtil;

public class BatchProcessingServiceImpl implements BatchProcessingService {

    private final XmlParserService    xmlService     = new XmlParserServiceImpl();
    private final ZipExtractorService zipService     = new ZipExtractorServiceImpl();
    private final StorageService      storageService = new StorageServiceImpl();
    private final InwardChequeDao     chequeDao      = new InwardChequeDaoImpl();
    private final InwardBatchDao      batchDao       = new InwardBatchDaoImpl();

    @Override
    public String processBatch(File xmlFile, File zipFile, ProgressListener listener) {

        if (xmlFile == null || !xmlFile.exists()) {
            throw new RuntimeException("XML file is missing or not found");
        }
        if (zipFile == null || !zipFile.exists()) {
            throw new RuntimeException("ZIP file is missing or not found");
        }

        // Variables declaration - finally block can access them
        Map<String, File> images=null;
        
        try {
            // STEP 1 : Parse XML
            List<ChequeDTO> cheques = xmlService.parseXml(xmlFile);
            if (cheques == null || cheques.isEmpty()) {
                throw new RuntimeException("No cheques found in XML file");
            }

            int total = cheques.size();

            // STEP 2 : Resolve Batch ID
            String batchId = resolveBatchId(cheques);

            // STEP 3 : Extract ZIP 
            String extractionFolder = PropertyUtil.getProperty("extraction.folder");
            images = zipService.extractZip(zipFile, extractionFolder);
            if (images == null || images.isEmpty()) {
                throw new RuntimeException("No images found in ZIP file");
            }

            // STEP 4 : Create / fetch batch
            InwardBatch existing = batchDao.findByBatchId(batchId);
            if (existing == null) {
                InwardBatch newBatch = new InwardBatch();
                newBatch.setBatchId(batchId);
                newBatch.setBatchStatus(BatchStatus.Draft);
                newBatch.setTotalCheques(total);
                newBatch.setSuccessCount(0);
                newBatch.setMicrRepairCount(0);
                batchDao.save(newBatch);
            }

            int inserted = 0;
            int skipped  = 0;
            int failed   = 0;

            // STEP 5 : Process cheques one by one
            for (ChequeDTO dto : cheques) {

                if (dto == null) continue;

                String chequeNo = dto.getChequeNumber();

                try {
                    if (chequeDao.existsByChequeNumber(chequeNo)) {
                        skipped++;
                        // Still report progress even for skipped cheques
                        if (listener != null) {
                            listener.onProgress(inserted + skipped + failed, total);
                        }
                        continue;
                    }
                } catch (Exception e) {
                    failed++;
                    if (listener != null) {
                        listener.onProgress(inserted + skipped + failed, total);
                    }
                    continue;
                }

                try {
                    InwardCheque cheque = new InwardCheque();

                    InwardBatch batchRef = new InwardBatch();
                    batchRef.setBatchId(batchId);
                    cheque.setBatch(batchRef);

                    cheque.setChequeNumber(dto.getChequeNumber());
                    cheque.setAccountNumber(dto.getAccountNumber());
                    cheque.setAmount(dto.getAmount());
                    cheque.setDrawerName(dto.getDrawerName());
                    cheque.setPayeeName(dto.getPayeeName());
                    cheque.setMicrCode(dto.getMicrCode());
                    cheque.setTransactionCode(dto.getTransactionCode());
                    cheque.setBranchName(dto.getBranchName());
                    cheque.setPresentingBank(dto.getPresentingBank());
                    cheque.setAmountInWords(dto.getAmountInWords());
                    cheque.setIfscCode(dto.getIfscCode());
                    cheque.setChequeDate(dto.getChequeDate());
                    cheque.setFrontImagePath(uploadImage(images, dto.getFrontImage(), batchId));
                    cheque.setRearImagePath(uploadImage(images, dto.getBackImage(), batchId));
                    validateCheque(cheque);
                    chequeDao.save(cheque);
                    System.out.println("Cheque Saved : " + cheque.getChequeNumber() + " | Status : " + cheque.getChequeStatus());
                    inserted++;

                } catch (Exception e) {
                    System.out.println("Failed Cheque : " + chequeNo + " | " + e.getMessage());
                    failed++;
                }

                // ── Report progress after every cheque ──────────────────────
                if (listener != null) {
                    listener.onProgress(inserted + skipped + failed, total);
                }
            }
            
         // ── All cheques processed — print final counts ──────────────────
            System.out.println("========================================");
            System.out.println("Batch ID        : " + batchId);
            System.out.println("Total Cheques   : " + total);
            System.out.println("Inserted        : " + inserted);
            System.out.println("Duplicate       : " + skipped);
            System.out.println("Failed          : " + failed);
            System.out.println("========================================");

         // STEP 6 : Update batch
         // Fetch fresh from DB — existing session from STEP 4 is already closed.
         // We fetch again to get a live managed object safe for update.
         InwardBatch batchToUpdate = batchDao.findByBatchId(batchId);
         if (batchToUpdate != null) {

             // Add current run's inserted count to whatever is already in DB.
             // Example : DB has successCount = 7 (from previous run)
             //           This run inserted    = 3
             //           Final successCount   = 7 + 3 = 10
             // This handles the case where batch is re-processed after partial failure.
             int updatedSuccessCount = batchToUpdate.getSuccessCount() + inserted;
             batchToUpdate.setSuccessCount(updatedSuccessCount);

             // Total cheques always comes from XML — never changes across runs.
             // It is always the full count of cheques present in the XML file.
             batchToUpdate.setTotalCheques(total);

             // Update createdAt to current date and time of this run.
             // This reflects the latest processing time of the batch.
             // Example : First run  -> 2026-06-25 10:29:56
             //           Second run -> 2026-06-25 11:45:30  (updated to this)
             batchToUpdate.setCreatedAt(LocalDateTime.now());

             batchDao.update(batchToUpdate);
         }
            

            return batchId + "|" + total + "|" + inserted + "|" + skipped + "|" + failed;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Batch Processing Failed : " + e.getMessage(), e);
        } finally {
            // Runs always — success or failure
            // At this point images are already saved to Supabase Storage
        	cleanupTempFiles(xmlFile, zipFile, images);
        }
    }

    // ==========================
    // BATCH ID RESOLUTION
    // ==========================
    private String resolveBatchId(List<ChequeDTO> cheques) {

        if (cheques != null && !cheques.isEmpty()) {
            String batchIdFromXml = cheques.get(0).getBatchId();
            if (batchIdFromXml != null && !batchIdFromXml.trim().isEmpty()) {
                System.out.println("Batch ID read from XML : " + batchIdFromXml);
                return batchIdFromXml.trim();
            }
        }

        String generated = "BATCH-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        System.out.println("Batch ID auto-generated : " + generated);
        return generated;
    }

    // ==========================
    // IMAGE UPLOAD HELPER
    // ==========================
    private String uploadImage(Map<String, File> images,
                               String fileName,
                               String batchId) {

        if (fileName == null || fileName.trim().isEmpty()) {
            System.out.println("Warning : Image name missing in XML, skipping upload");
            return null;
        }

        File imageFile = images.get(fileName.trim());

        if (imageFile == null) {
            System.out.println("Warning : Image not found in ZIP : "
                    + fileName + " | Available : " + images.keySet());
            return null;
        }

        return storageService.uploadFile(imageFile, batchId);
    }
    
    /**
     * Validates all business rules for a cheque.
     *
     * Rules:
     * 1. All mandatory fields must be present.
     * 2. Cheque number must contain exactly 6 digits.
     * 3. Account number must contain exactly 15 digits.
     * 4. IFSC code must contain exactly 11 characters and the 5th character must be '0'.
     * 5. Cheque date cannot be a future date (Post Dated Cheque).
     * 6. Cheque date cannot be older than 90 days from the current date.
     * 7. If front or rear image path is missing, cheque status is forced to Reject
     *    regardless of other validations. Cheque is still inserted into DB.
     *    Maker will decide whether to process or return the cheque.
     *
     * If any validation (rules 1-6) fails:
     *      chequeStatus = Repair
     *
     * If all validations pass:
     *      chequeStatus = Normal
     *
     * Special Rule (Rule 7):
     *      If frontImagePath or rearImagePath is null or blank,
     *      chequeStatus is FORCED to Reject — cheque is still inserted into DB.
     */
    private void validateCheque(InwardCheque cheque) {

        boolean valid = true;

        // =====================================================
        // STEP 1 : Mandatory Field Validation
        // =====================================================
        if (isBlank(cheque.getChequeNumber())) {
            System.out.println("Cheque Number is blank");
            valid = false;
        }

        if (isBlank(cheque.getAccountNumber())) {
            System.out.println("Account Number is blank");
            valid = false;
        }

        if (cheque.getAmount() == null) {
            System.out.println("Amount is null");
            valid = false;
        }

        if (isBlank(cheque.getAmountInWords())) {
            System.out.println("Amount In Words is blank");
            valid = false;
        }

        if (isBlank(cheque.getIfscCode())) {
            System.out.println("IFSC is blank");
            valid = false;
        }

        if (isBlank(cheque.getDrawerName())) {
            System.out.println("Drawer Name is blank");
            valid = false;
        }

        if (isBlank(cheque.getPayeeName())) {
            System.out.println("Payee Name is blank");
            valid = false;
        }

        if (isBlank(cheque.getMicrCode())) {
            System.out.println("MICR Code is blank");
            valid = false;
        }

        if (isBlank(cheque.getTransactionCode())) {
            System.out.println("Transaction Code is blank");
            valid = false;
        }

        if (isBlank(cheque.getBranchName())) {
            System.out.println("Branch Name is blank");
            valid = false;
        }

        if (isBlank(cheque.getPresentingBank())) {
            System.out.println("Presenting Bank is blank");
            valid = false;
        }

        if (cheque.getChequeDate() == null) {
            System.out.println("Cheque Date is null");
            valid = false;
        }

        // =====================================================
        // STEP 2 : Cheque Number Validation
        // Rule : Must be exactly 6 digits
        // Example : 100456
        // =====================================================
        if (!isBlank(cheque.getChequeNumber())
                && !cheque.getChequeNumber().matches("\\d{6}")) {

            valid = false;
            System.out.println("Incorrect Cheque Number : " + cheque.getChequeNo());
        }

        // =====================================================
        // STEP 3 : Account Number Validation
        // Rule : Must be exactly 15 digits
        // Example : 078902000018888
        // =====================================================
        if (!isBlank(cheque.getAccountNumber())
                && !cheque.getAccountNumber().matches("\\d{15}")) {

            valid = false;
            System.out.println("Incorrect Account Number : " + cheque.getChequeNo());
        }

        // =====================================================
        // STEP 4 : IFSC Validation
        // Rules:
        // 1. Length must be 11
        // 2. 5th character (index 4) must be '0'
        //
        // Example:
        // NVBK0123456  -> Valid
        // ABCD0123456  -> Valid
        // =====================================================
        if (!isBlank(cheque.getIfscCode())) {

            String ifsc = cheque.getIfscCode();

            if (ifsc.length() != 11 || ifsc.charAt(4) != '0') {
                valid = false;
                System.out.println("Incorrect IFSC Code : " + cheque.getChequeNo());
            }
        }

        // =====================================================
        // STEP 5 : MICR Code Validation
        // Rules:
        // 1. MICR Code should not be null or blank
        // 2. MICR Code must contain exactly 9 digits
        // Example : 400002123
        // =====================================================
        if (!isBlank(cheque.getMicrCode())) {

            String micr = cheque.getMicrCode().trim();

            if (!micr.matches("\\d{9}")) {
                valid = false;
                System.out.println("Invalid MICR Code : " + cheque.getChequeNumber());
            }
        }

        // =====================================================
        // STEP 6 : Cheque Date Validation
        // Rules:
        // 1. Future date is not allowed (Post Dated Cheque)
        // 2. Date should not be older than 90 days
        // =====================================================
        if (cheque.getChequeDate() != null) {

            LocalDateTime currentDate = LocalDateTime.now();
            LocalDateTime chequeDate  = cheque.getChequeDate();

            // Check for Post Dated Cheque
            if (chequeDate.isAfter(currentDate)) {
                valid = false;
                System.out.println("Cheque have future date : " + cheque.getChequeNo());
            }

            // Calculate age of cheque in days
            long daysDifference =
                    ChronoUnit.DAYS.between(chequeDate, currentDate);

            // Cheque validity exceeded 90 days
            if (daysDifference > 90) {
                valid = false;
                System.out.println("Cheque exceded 90 days: " + cheque.getChequeNo());
            }
        }
        
	     // =====================================================
	     // STEP 7 : Image Path Validation
	     // Rule : Front and Rear image paths must not be blank
	     // =====================================================
	     if (isBlank(cheque.getFrontImagePath()) || isBlank(cheque.getRearImagePath())) {
	         System.out.println("Image path missing : " + cheque.getChequeNo());
	         valid = false;
	     }

        // =====================================================
        // STEP 8 : Set Final Cheque Status based on valid flag
        // =====================================================
        if (valid) {
            cheque.setChequeStatus(ChequeStatus.Normal);
        } else {
            cheque.setChequeStatus(ChequeStatus.Repair);
        }

    }

    /**
     * Utility method to check whether a String
     * is null, empty or contains only spaces.
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    
 // ============================================================
 // TEMP FILE CLEANUP
 // Deletes the uploaded XML file and all extracted
 // image files after batch processing is complete.
 // Called from finally block to ensure cleanup always happens.
 // ============================================================
	private void cleanupTempFiles(File xmlFile, File zipFile, Map<String, File> images) {

		System.out.println("----------------------------------------");
		System.out.println("Cleanup Started...");

		// ── 1. Delete XML File ───────────────────────────────────
		if (xmlFile != null && xmlFile.exists()) {
			boolean deleted = xmlFile.delete();
			if (deleted) {
				System.out.println("XML Deleted      : " + xmlFile.getName());
			} else {
				System.out.println("XML Delete Failed: " + xmlFile.getName());
			}
		}

		// ── 2. Delete ZIP File ───────────────────────────────────
		if (zipFile != null && zipFile.exists()) {
			boolean deleted = zipFile.delete();
			if (deleted) {
				System.out.println("ZIP Deleted      : " + zipFile.getName());
			} else {
				System.out.println("ZIP Delete Failed: " + zipFile.getName());
			}
		}

		// ── 3. Delete Extracted Image Files ─────────────────────
		if (images != null && !images.isEmpty()) {
			for (Map.Entry<String, File> entry : images.entrySet()) {
				File imageFile = entry.getValue();
				if (imageFile != null && imageFile.exists()) {
					boolean deleted = imageFile.delete();
					if (deleted) {
						System.out.println("Image Deleted    : " + imageFile.getName());
					} else {
						System.out.println("Image Del Failed : " + imageFile.getName());
					}
				}
			}
		}

		System.out.println("Cleanup Completed.");
		System.out.println("----------------------------------------");
	}

	@Override
	public InwardBatch findByBatchId(String batchId) {
		return batchDao.findByBatchId(batchId);
	}


	@Override
	public Long resolveBatchDbId(String batchId) {
	    try {
	        InwardBatch batch = batchDao.findByBatchId(batchId);
	        return batch != null ? batch.getId() : null;
	    } catch (Exception e) {
	        return null;
	    }
	}
	
	@Override
	public void updateBatchStatusIfCompleted(Long batchId) {
	    batchDao.updateBatchStatusIfCompleted(batchId);
	}
    
}