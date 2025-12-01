import { useEffect, useState } from 'react';
import { useFetchWithAuth } from '@/hooks/use-fetch-with-auth';

/**
 * Example: Fetch posts from your backend API
 * This demonstrates how to use the authenticated fetch hook
 */

interface Post {
  id: string;
  title: string;
  content: string;
  author: string;
  createdAt: string;
}

export function usePostsApi() {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fetchWithAuth = useFetchWithAuth();

  // Fetch all posts
  const fetchPosts = async () => {
    try {
      setLoading(true);
      setError(null);

      // API_URL should be your backend server address
      const API_URL = 'http://localhost:8080/api/posts';

      const response = await fetchWithAuth(API_URL);

      if (!response.ok) {
        throw new Error(`Failed to fetch posts: ${response.statusText}`);
      }

      const data = await response.json();
      setPosts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      console.error('Error fetching posts:', err);
    } finally {
      setLoading(false);
    }
  };

  // Create a new post
  const createPost = async (title: string, content: string) => {
    try {
      setLoading(true);
      setError(null);

      const API_URL = 'http://localhost:8080/api/posts';

      const response = await fetchWithAuth(API_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ title, content }),
      });

      if (!response.ok) {
        throw new Error(`Failed to create post: ${response.statusText}`);
      }

      const newPost = await response.json();
      setPosts([newPost, ...posts]);
      return newPost;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      console.error('Error creating post:', err);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  // Update a post
  const updatePost = async (postId: string, title: string, content: string) => {
    try {
      setLoading(true);
      setError(null);

      const API_URL = `http://localhost:8080/api/posts/${postId}`;

      const response = await fetchWithAuth(API_URL, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ title, content }),
      });

      if (!response.ok) {
        throw new Error(`Failed to update post: ${response.statusText}`);
      }

      const updatedPost = await response.json();
      setPosts(posts.map(p => p.id === postId ? updatedPost : p));
      return updatedPost;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      console.error('Error updating post:', err);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  // Delete a post
  const deletePost = async (postId: string) => {
    try {
      setLoading(true);
      setError(null);

      const API_URL = `http://localhost:8080/api/posts/${postId}`;

      const response = await fetchWithAuth(API_URL, {
        method: 'DELETE',
      });

      if (!response.ok) {
        throw new Error(`Failed to delete post: ${response.statusText}`);
      }

      setPosts(posts.filter(p => p.id !== postId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      console.error('Error deleting post:', err);
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
    updatePost,
    deletePost,
  };
}

/**
 * Usage Example in a Component:
 *
 * import { usePostsApi } from '@/hooks/use-posts-api';
 *
 * export function PostsScreen() {
 *   const { posts, loading, error, fetchPosts, createPost, deletePost } = usePostsApi();
 *
 *   useEffect(() => {
 *     fetchPosts();
 *   }, []);
 *
 *   if (loading) return <LoadingSpinner />;
 *   if (error) return <ErrorMessage error={error} />;
 *
 *   return (
 *     <FlatList
 *       data={posts}
 *       renderItem={({ item }) => (
 *         <PostCard
 *           post={item}
 *           onDelete={() => deletePost(item.id)}
 *         />
 *       )}
 *       keyExtractor={item => item.id}
 *     />
 *   );
 * }
 */

/**
 * Configuration Guide:
 *
 * 1. Update API_URL in each function to match your backend
 *    - Development: http://localhost:8080/api
 *    - Production: https://your-api.example.com/api
 *
 * 2. The Authorization header is automatically added by useFetchWithAuth()
 *    - Bearer token: Authorization: Bearer {access_token}
 *
 * 3. Backend should validate the token before processing requests
 *
 * 4. If you get 401 errors:
 *    - Token might be expired (refreshes automatically)
 *    - User session ended (redirect to login)
 */

