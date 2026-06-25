package com.cts.inward.dto;

public class DashboardStatsDTO {

	private long totalBatches;
	private long clearedBatches;
	private long pendingBatches;
	private long draftBatches;

	public DashboardStatsDTO() {
	}

	public DashboardStatsDTO(long totalBatches, long clearedBatches, long pendingBatches, long draftBatches) {
		this.totalBatches = totalBatches;
		this.clearedBatches = clearedBatches;
		this.pendingBatches = pendingBatches;
		this.draftBatches = draftBatches;
	}

	public DashboardStatsDTO(long totalBatches, long clearedBatches, long pendingBatches) {
		this.totalBatches = totalBatches;
		this.clearedBatches = clearedBatches;
		this.pendingBatches = pendingBatches;
	}

	public long getTotalBatches() {
		return totalBatches;
	}

	public void setTotalBatches(long totalBatches) {
		this.totalBatches = totalBatches;
	}

	public long getClearedBatches() {
		return clearedBatches;
	}

	public void setClearedBatches(long clearedBatches) {
		this.clearedBatches = clearedBatches;
	}

	public long getPendingBatches() {
		return pendingBatches;
	}

	public void setPendingBatches(long pendingBatches) {
		this.pendingBatches = pendingBatches;
	}

	public long getDraftBatches() {
		return draftBatches;
	}

	public void setDraftBatches(long draftBatches) {
		this.draftBatches = draftBatches;
	}

}