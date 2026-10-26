import { api } from './client';
import type {
  LoginResponse,
  ProductDto,
  PreapprovedOfferDto,
  ApplicationSummaryDto,
  ApplicationDetailDto,
  CreateApplicationRequest,
  UpdateApplicationRequest,
  SubmitRequest,
  OfferDto,
  ContractDto,
  PagedModel,
  BankApplicationDetailDto,
  ManualDecisionRequest,
} from './types';

export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await api.post<LoginResponse>('/auth/login', { login: username, password });
  return res.data;
}

export async function getMe() {
  const res = await api.get('/me');
  return res.data;
}

export async function getProducts(): Promise<ProductDto[]> {
  const res = await api.get<ProductDto[]>('/products');
  return res.data;
}

export async function getPreapproved(): Promise<PreapprovedOfferDto[]> {
  const res = await api.get<PreapprovedOfferDto[]>('/offers/preapproved');
  return res.data;
}

export async function getApplications(): Promise<ApplicationSummaryDto[]> {
  const res = await api.get<ApplicationSummaryDto[]>('/applications');
  return res.data;
}

export async function getApplication(id: string): Promise<ApplicationDetailDto> {
  const res = await api.get<ApplicationDetailDto>(`/applications/${id}`);
  return res.data;
}

export async function createApplication(r: CreateApplicationRequest): Promise<ApplicationDetailDto> {
  const res = await api.post<ApplicationDetailDto>('/applications', r);
  return res.data;
}

export async function updateApplication(id: string, r: UpdateApplicationRequest): Promise<ApplicationDetailDto> {
  const res = await api.put<ApplicationDetailDto>(`/applications/${id}`, r);
  return res.data;
}

export async function submitApplication(id: string, r: SubmitRequest): Promise<ApplicationDetailDto> {
  const res = await api.post<ApplicationDetailDto>(`/applications/${id}/submit`, r);
  return res.data;
}

export async function cancelApplication(id: string): Promise<ApplicationDetailDto> {
  const res = await api.post<ApplicationDetailDto>(`/applications/${id}/cancel`);
  return res.data;
}

export async function getOffer(applicationId: string): Promise<OfferDto> {
  const res = await api.get<OfferDto>(`/applications/${applicationId}/offer`);
  return res.data;
}

export async function acceptOffer(offerId: string): Promise<ContractDto> {
  const res = await api.post<ContractDto>(`/offers/${offerId}/accept`);
  return res.data;
}

export async function declineOffer(offerId: string): Promise<void> {
  await api.post(`/offers/${offerId}/decline`);
}

export async function getContracts(): Promise<ContractDto[]> {
  const res = await api.get<ContractDto[]>('/contracts');
  return res.data;
}

export async function bankGetQueue(
  status?: string,
  page = 0,
  size = 20
): Promise<PagedModel<ApplicationSummaryDto>> {
  const params: Record<string, string | number> = { page, size };
  if (status) params.status = status;
  const res = await api.get<PagedModel<ApplicationSummaryDto>>('/bank/applications', { params });
  return res.data;
}

export async function bankGetApplication(id: string): Promise<BankApplicationDetailDto> {
  const res = await api.get<BankApplicationDetailDto>(`/bank/applications/${id}`);
  return res.data;
}

export async function bankDecide(id: string, r: ManualDecisionRequest): Promise<BankApplicationDetailDto> {
  const res = await api.post<BankApplicationDetailDto>(`/bank/applications/${id}/decision`, r);
  return res.data;
}
