export type Mode = "LIVE" | "DEMO";
export type AuditStatus = "CREATED" | "GENERATING_PERSONAS" | "PERSONAS_READY" | "TESTING" | "ANALYZING" | "REPORT_READY" | "FAILED";
export type JourneyStatus = "PENDING" | "RUNNING" | "SUCCESS" | "PARTIAL" | "FAILED" | "ERROR";

export interface Business { id: string; name: string; websiteUrl: string; category: string; description?: string; createdAt: string }
export interface AuditSession {
  id: string; businessId: string; status: AuditStatus; mode: Mode; overallScore?: number; scoreExplanation?: string;
  progressMessage?: string; errorMessage?: string; baselineScreenshot?: string; reportSummary?: string;
  startedAt?: string; completedAt?: string; createdAt: string;
}
export interface JourneyTask { id: string; description: string; successCriteria: string; status: JourneyStatus; actionCount: number; outcomeSummary?: string }
export interface PersonaView { id: string; orderIndex: number; name: string; background: string; objective: string; lookFor: string[]; task?: JourneyTask }
export interface AuditView { audit: AuditSession; business: Business; personas: PersonaView[]; preview?: { id: string; status: string }; maxActions: number }
export interface Step {
  id: string; step: number; url: string; title?: string; action: string; actionInput?: string; reasoning?: string;
  observation?: string; screenshot?: string; success: boolean; createdAt: string;
}
export interface JourneyProgress {
  personaId: string; personaName: string; taskId: string; task: string; status: JourneyStatus; actionCount: number; outcomeSummary?: string; steps: Step[];
}
export interface Progress {
  status: AuditStatus; mode: Mode; progressMessage?: string; errorMessage?: string; maxActions: number;
  journeys: JourneyProgress[]; completedJourneys: number; totalJourneys: number;
}
export interface Finding {
  id: string; title: string; description: string; severity: "high" | "medium" | "low"; source: string; affectedUrl?: string;
  personaName?: string; evidenceSummary?: string; screenshotPath?: string; frictionReason?: string; recommendation: string;
}
export interface Report {
  audit: AuditSession; business: Business; journeys: JourneyProgress[];
  stats: { journeysTested: number; successful: number; partialOrFailed: number };
  findings: { finding: Finding; evidence: Step[] }[]; highPriority: number;
}
export interface ProposedChange { id: string; orderIndex: number; title: string; description: string; relatedFinding?: string; needsVerification: boolean; status: "PENDING" | "APPROVED" | "REJECTED" }
export interface Preview {
  id: string; auditSessionId: string; status: "GENERATING" | "DRAFT" | "APPROVED" | "REJECTED" | "FAILED"; mode: Mode;
  verificationNotes?: string; errorMessage?: string; createdAt: string; changes: ProposedChange[];
  approvals: { id: string; status: string; notes?: string; reviewedAt: string }[]; baselineScreenshot?: string;
}
export interface SystemStatus { claudeConfigured: boolean; defaultMode: Mode; model?: string; workerHealthy: boolean; maxActionsPerJourney: number }
