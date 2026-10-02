export const connectAuthEmulator = jest.fn();
export const getAuth = jest.fn(() => ({ currentUser: null, authStateReady: jest.fn() }));
export const getReactNativePersistence = jest.fn(() => ({}));
export const initializeAuth = jest.fn(() => ({ currentUser: null, authStateReady: jest.fn() }));
export const signInWithEmailAndPassword = jest.fn();
export const signOut = jest.fn();
export const GoogleAuthProvider = { credential: jest.fn() };
export const signInWithCredential = jest.fn();
