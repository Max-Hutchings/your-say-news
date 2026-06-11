import { useAuthStore } from "../services/authContext";

/**
 * Hook for making authenticated API requests.
 * Automatically attaches the bearer token from the auth store and logs the
 * user out if the token is missing or rejected (401).
 */
export function useFetchWithAuth() {
  const getAccessToken = useAuthStore((state) => state.getAccessToken);
  const logout = useAuthStore((state) => state.logout);

  return async (
    url: string,
    options: RequestInit = {}
  ): Promise<Response> => {
    const accessToken = getAccessToken();

    if (!accessToken) {
      // User is not authenticated (or token couldn't be loaded)
      logout();
      throw new Error("Not authenticated");
    }

    const headers = {
      ...options.headers,
      Authorization: `Bearer ${accessToken}`,
    };

    const response = await fetch(url, {
      ...options,
      headers,
    });

    // Handle 401 Unauthorized
    if (response.status === 401) {
      // Token is invalid or expired
      logout();
      throw new Error("Session expired. Please login again.");
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
