package com.cts.inward.service;

import java.io.File;

import com.cts.inward.entity.InwardBatch;


public interface BatchProcessingService {


	//Accepts a listener that reports progress
    String processBatch(File xmlFile, File zipFile, ProgressListener listener);

	InwardBatch findByBatchId(String batchId);
	
	// ── Inner interface ──────────────────────────────────────────────────
    // Called by the service after each cheque is processed.
    // The composer implements this to update the UI.
    interface ProgressListener {
        void onProgress(int current, int total);
    }
    Long resolveBatchDbId(String batchId);
    
    void updateBatchStatusIfCompleted(Long batchId);
}