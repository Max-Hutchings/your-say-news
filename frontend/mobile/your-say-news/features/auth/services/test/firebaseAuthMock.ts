export const connectAuthEmulator = jest.fn();
export const getAuth = jest.fn(() => ({ currentUser: null, authStateReady: jest.fn() }));
export const getReactNativePersistence = jest.fn(() => ({}));
export const initializeAuth = jest.fn(() => ({ currentUser: null, authStateReady: jest.fn() }));
export const signInWithEmailAndPassword = jest.fn();
export const signOut = jest.fn();
