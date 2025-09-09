interface PrivacyPolicy {
  version: string;
  lastUpdated: string;
  content: string;
}

export interface IStorage {
  getPrivacyPolicy(): Promise<PrivacyPolicy | null>;
}

export class MemStorage implements IStorage {
  private privacyPolicy: PrivacyPolicy = {
    version: "2.0",
    lastUpdated: new Date().toISOString(),
    content: ""
  };

  async getPrivacyPolicy(): Promise<PrivacyPolicy | null> {
    return this.privacyPolicy;
  }
}