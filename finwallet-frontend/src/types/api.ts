/** Integer amount in minor units (sen). RM 12.34 is 1234. Never use floats for money. */
export type MinorUnits = number;

export type CurrencyCode = "MYR";

/** ISO-8601 timestamp string as returned by the API. */
export type IsoDateString = string;

/** Error body shape expected from the NestJS backend (default HttpException format). */
export interface ApiErrorBody {
  statusCode: number;
  message: string | string[];
  error?: string;
  code?: string;
}

export interface PaginationMeta {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface Paginated<T> {
  data: T[];
  meta: PaginationMeta;
}
