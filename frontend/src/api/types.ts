export type Role = 'CLIENT' | 'EMPLOYEE';

export type ApplicationStatus =
  | 'DRAFT' | 'SUBMITTED' | 'COMPLETENESS_CHECK' | 'NEEDS_INFO'
  | 'DATA_CHECK' | 'SCORING' | 'MANUAL_REVIEW' | 'APPROVED'
  | 'REJECTED' | 'OFFER_DECLINED' | 'SIGNED' | 'DISBURSED'
  | 'CANCELLED' | 'OFFER_EXPIRED';

export type Purpose = 'WORKING_CAPITAL' | 'EQUIPMENT' | 'CASH_GAP' | 'REAL_ESTATE' | 'REFINANCING' | 'OTHER';
export type ManualAction = 'APPROVE' | 'REJECT' | 'CHANGE_TERMS' | 'REQUEST_INFO';

export interface LoginResponse { token: string; role: Role; fullName: string; companyId: string | null; }

export interface ApplicationSummaryDto {
  id: string; companyId: string; companyName: string; productCode: string; productName: string;
  amount: number; termMonths: number; purpose: Purpose; purposeLabel: string;
  status: ApplicationStatus; statusLabel: string; clientStatus: string; actionRequired: boolean;
  version: number; createdAt: string; submittedAt: string | null; updatedAt: string;
}

export interface ObligationDto { creditorName: string; monthlyPayment: number; remainingDebt: number | null; }

export interface IssueDto {
  id: string; source: string; field: string; message: string; reason: string;
  resolved: boolean; createdAt: string;
}

export interface HistoryDto {
  id: number; fromStatus: ApplicationStatus | null; toStatus: ApplicationStatus;
  toStatusLabel: string; actorType: string; actorId: string | null;
  reason: string; comment: string | null; createdAt: string;
}

export interface OfferDto {
  id: string; amount: number; termMonths: number; rate: number; termsChanged: boolean;
  changes: Record<string, unknown> | null; status: string; validUntil: string; createdAt: string;
}

export interface ApplicationDetailDto {
  summary: ApplicationSummaryDto; fullName: string | null; phone: string | null;
  revenue6m: number | null; monthlyExpenses: number | null;
  obligations: ObligationDto[]; issues: IssueDto[]; history: HistoryDto[];
  offer: OfferDto | null; consents: string[];
  clientReasons: string[] | null;
}

export interface ProductDto {
  id: string; code: string; name: string; description: string | null; suitablePurpose: Purpose;
  minAmount: number; maxAmount: number; minTermMonths: number; maxTermMonths: number; baseRate: number;
}

export interface PreapprovedOfferDto {
  id: string; productId: string; productCode: string; productName: string;
  maxAmount: number; rateFrom: number; validUntil: string; reason: Record<string, unknown>;
}

export interface ContractDto {
  id: string; offerId: string; applicationId: string; amount: number; termMonths: number;
  rate: number; signedAt: string; disbursedAt: string | null; simulated: boolean; status: string;
}

export interface CheckDto {
  id: string; checkType: string; status: string; payload: Record<string, unknown>; checkedAt: string;
}

export interface CompanyDto {
  id: string; inn: string; name: string; okved: string; directorName: string; registeredAt: string;
}

export interface ManualDecisionDto {
  id: string; action: ManualAction; comment: string | null; employeeId: string; decidedAt: string;
}

export interface BankApplicationDetailDto {
  detail: ApplicationDetailDto; offer: OfferDto | null; company: CompanyDto;
  checks: CheckDto[]; decision: Record<string, unknown> | null; manualDecisions: ManualDecisionDto[];
}

export interface PagedModel<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number; };
}

export interface CreateApplicationRequest {
  productId: string; amount: number; termMonths: number; purpose: Purpose; preapprovedOfferId?: string;
}

export interface UpdateApplicationRequest {
  amount?: number; termMonths?: number; purpose?: Purpose; fullName?: string; phone?: string;
  revenue6m?: number; monthlyExpenses?: number; obligations?: ObligationDto[]; version: number;
}

export interface SubmitRequest { bkiConsent: boolean; personalDataConsent: boolean; version: number; }

export interface ManualDecisionRequest {
  action: ManualAction; comment?: string; amount?: number; termMonths?: number; rate?: number; version: number;
}
