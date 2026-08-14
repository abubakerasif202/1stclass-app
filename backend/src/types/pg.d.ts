declare module 'pg' {
  export interface QueryResult { rowCount: number | null; rows: any[] }
  export interface PoolClient {
    query(text: string, values?: unknown[]): Promise<QueryResult>;
    release(): void;
  }
  export class Pool {
    constructor(options: { connectionString: string; max?: number });
    query(text: string, values?: unknown[]): Promise<QueryResult>;
    connect(): Promise<PoolClient>;
    end(): Promise<void>;
  }
}
