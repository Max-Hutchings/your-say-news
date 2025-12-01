import { useAuth } from '@/context/AuthContext';

/**
 * Hook for making authenticated API requests
 * Automatically includes authorization header and handles token refresh
 */
export function useFetchWithAuth() {
  const { getAuthHeader, logout } = useAuth();

  return async (
    url: string,
    options: RequestInit = {}
  ): Promise<Response> => {
    const authHeader = await getAuthHeader();

    if (!authHeader) {
      // User is not authenticated or token couldn't be refreshed
      await logout();
      throw new Error('Not authenticated');
    }

    const headers = {
      ...options.headers,
      ...authHeader,
    };

    const response = await fetch(url, {
      ...options,
      headers,
    });

    // Handle 401 Unauthorized
    if (response.status === 401) {
      // Token is invalid or expired
      await logout();
      throw new Error('Session expired. Please login again.');
    }

    return response;
  };
}

/**
 * Example usage in a component:
 *
 * const fetchWithAuth = useFetchWithAuth();
 *
 * // GET request
 * const response = await fetchWithAuth('http://your-api.com/posts');
 * const data = await response.json();
 *
 * // POST request
 * const response = await fetchWithAuth('http://your-api.com/posts', {
 *   method: 'POST',
 *   headers: {
 *     'Content-Type': 'application/json',
 *   },
 *   body: JSON.stringify({ title: 'New Post' }),
 * });
 */

