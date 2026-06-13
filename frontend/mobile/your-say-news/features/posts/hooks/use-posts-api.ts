import { useState } from 'react';
import Constants from 'expo-constants';
import { YsnHttpClient } from '@/features/auth';
import type { Post } from '../types';

/**
 * Posts API hook. Talks to post-service through the shared authenticated
 * HTTP client (bearer + token refresh handled by YsnHttpClient).
 */

const extra = Constants.expoConfig?.extra ?? {};
const POSTS_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/posts`;

export function usePostsApi() {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchPosts = async () => {
    try {
      setLoading(true);
      setError(null);
      const { data } = await YsnHttpClient.getSecure().get<Post[]>(POSTS_URL);
      setPosts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  const createPost = async (title: string, content: string) => {
    try {
      setLoading(true);
      setError(null);
      const { data } = await YsnHttpClient.getSecure().post<Post>(POSTS_URL, { title, content });
      setPosts((current) => [data, ...current]);
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const deletePost = async (postId: string) => {
    try {
      setLoading(true);
      setError(null);
      await YsnHttpClient.getSecure().delete(`${POSTS_URL}/${postId}`);
      setPosts((current) => current.filter((p) => p.id !== postId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  return {
    posts,
    loading,
    error,
    fetchPosts,
    createPost,
    deletePost,
  };
}
