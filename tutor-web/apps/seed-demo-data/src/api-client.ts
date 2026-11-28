import { demoTokenRequest, type DemoUser } from '@tutor-web/e2e-support';

const API_URL = 'http://localhost:9190';
const KEYCLOAK_URL = 'http://localhost:8180';

interface Page<T> {
  content: T[];
  totalPages: number;
}

export class SeedApi {
  private readonly tokens = new Map<
    DemoUser,
    { value: string; expires: number }
  >();

  async checkServices(): Promise<void> {
    await this.request('API', `${API_URL}/actuator/health`);
    await this.request(
      'Keycloak',
      `${KEYCLOAK_URL}/realms/tutor/.well-known/openid-configuration`,
    );
  }

  async get<T>(user: DemoUser, path: string): Promise<T> {
    return this.api(user, path, 'GET');
  }

  async post<T>(user: DemoUser, path: string, body?: object): Promise<T> {
    return this.api(user, path, 'POST', body);
  }

  async find<T>(
    user: DemoUser,
    path: string,
    matches: (item: T) => boolean,
  ): Promise<T | undefined> {
    for (let page = 0; ; page++) {
      const separator = path.includes('?') ? '&' : '?';
      const response = await this.get<Page<T>>(
        user,
        `${path}${separator}page=${page}&size=100`,
      );
      const item = response.content.find(matches);
      if (item || page + 1 >= response.totalPages) {
        return item;
      }
    }
  }

  private async api<T>(
    user: DemoUser,
    path: string,
    method: string,
    body?: object,
  ): Promise<T> {
    const token = await this.token(user);
    const response = await this.request('API', `${API_URL}/api${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return response.json() as Promise<T>;
  }

  private async token(user: DemoUser): Promise<string> {
    const token = this.tokens.get(user);
    if (token && token.expires > Date.now()) {
      return token.value;
    }
    const { url, body } = demoTokenRequest(KEYCLOAK_URL, user);
    const response = await this.request('Keycloak', url, {
      method: 'POST',
      body: new URLSearchParams(body),
    });
    const { access_token, expires_in } = (await response.json()) as {
      access_token: string;
      expires_in: number;
    };
    this.tokens.set(user, {
      value: access_token,
      expires: Date.now() + expires_in * 1000 - 30_000,
    });
    return access_token;
  }

  private async request(
    service: string,
    url: string,
    init: RequestInit = {},
  ): Promise<Response> {
    let response: Response;
    try {
      response = await fetch(url, {
        ...init,
        signal: AbortSignal.timeout(10_000),
        redirect: 'error',
      });
    } catch (cause) {
      throw new Error(
        `${service} is unavailable at ${new URL(url).origin}. Start the local stack with ./dev.sh.`,
        { cause },
      );
    }
    if (!response.ok) {
      throw new Error(
        `${service} ${init.method ?? 'GET'} ${new URL(url).pathname} failed (HTTP ${response.status}).`,
      );
    }
    return response;
  }
}
