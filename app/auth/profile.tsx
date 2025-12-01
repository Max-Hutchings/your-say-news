import React from 'react';
import { StyleSheet, TouchableOpacity, SafeAreaView } from 'react-native';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAuth } from '@/context/AuthContext';
import { useRouter } from 'expo-router';

export default function ProfileScreen() {
  const { user, logout } = useAuth();
  const router = useRouter();

  const handleLogout = async () => {
    try {
      await logout();
      router.replace('/auth/login');
    } catch (error) {
      console.error('Logout error:', error);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ThemedView style={styles.content}>
        <ThemedText style={styles.title}>Profile</ThemedText>

        {user ? (
          <ThemedView style={styles.userInfo}>
            <ThemedText style={styles.label}>Name:</ThemedText>
            <ThemedText style={styles.value}>{user.name}</ThemedText>

            <ThemedText style={styles.label}>Username:</ThemedText>
            <ThemedText style={styles.value}>{user.preferred_username}</ThemedText>

            <ThemedText style={styles.label}>Email:</ThemedText>
            <ThemedText style={styles.value}>{user.email}</ThemedText>

            <ThemedText style={styles.label}>Email Verified:</ThemedText>
            <ThemedText style={styles.value}>
              {user.email_verified ? 'Yes' : 'No'}
            </ThemedText>

            <TouchableOpacity
              style={styles.logoutButton}
              onPress={handleLogout}
            >
              <ThemedText style={styles.logoutButtonText}>
                Logout
              </ThemedText>
            </TouchableOpacity>
          </ThemedView>
        ) : (
          <ThemedText>No user information available</ThemedText>
        )}
      </ThemedView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
    padding: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 24,
  },
  userInfo: {
    marginBottom: 24,
  },
  label: {
    fontSize: 12,
    fontWeight: '600',
    opacity: 0.7,
    marginTop: 16,
    marginBottom: 4,
  },
  value: {
    fontSize: 16,
    fontWeight: '500',
  },
  logoutButton: {
    backgroundColor: '#FF3B30',
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 32,
  },
  logoutButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
});

