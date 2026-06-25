package com.cts.inward.dto;

public class CheckerDashboardStatsDTO {

	private long assignedBatches;
	private long pendingBatches;
	private long clearedBatches;
	private long pendingCheques;
	private long approvedCheques;
	private long returnedCheques;
	  // Default constructor
    public CheckerDashboardStatsDTO() {
    }
	
	
	public CheckerDashboardStatsDTO(long assignedBatches,
            long clearedBatches,
            long pendingBatches) {
this.assignedBatches = assignedBatches;
this.clearedBatches = clearedBatches;
this.pendingBatches = pendingBatches;
}

	

	public long getAssignedBatches() {
		return assignedBatches;
	}

	public void setAssignedBatches(long assignedBatches) {
		this.assignedBatches = assignedBatches;
	}

	public long getPendingBatches() {
		return pendingBatches;
	}

	public void setPendingBatches(long pendingBatches) {
		this.pendingBatches = pendingBatches;
	}

	

	public long getClearedBatches() {
		return clearedBatches;
	}


	public void setClearedBatches(long clearedBatches) {
		this.clearedBatches = clearedBatches;
	}


	public long getPendingCheques() {
		return pendingCheques;
	}

	public void setPendingCheques(long pendingCheques) {
		this.pendingCheques = pendingCheques;
	}

	public long getApprovedCheques() {
		return approvedCheques;
	}

	public void setApprovedCheques(long approvedCheques) {
		this.approvedCheques = approvedCheques;
	}

	public long getReturnedCheques() {
		return returnedCheques;
	}

	public void setReturnedCheques(long returnedCheques) {
		this.returnedCheques = returnedCheques;
	}

}