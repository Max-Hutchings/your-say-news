
import {
    createContext,
    useContext,
    useEffect,
    useState,
    type ReactNode,
} from "react";

type User = {
    id: string;
    email: string;
    hasCharacteristics: boolean;
};

type AuthContextType = {
    user: User | null;
    loading: boolean;
    login: (token: string) => Promise<void>;
    logout: () => void;
    setHasCharacteristics: (value: boolean) => void;
};

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const bootstrap = async () => {
            try {

            } finally {
                setLoading(false);
            }
        };
        bootstrap();
    }, []);

    const login = async (token: string) => {

        const fakeUser: User = {
            id: "1",
            email: "test@example.com",
            hasCharacteristics: false,
        };
        setUser(fakeUser);
    };

    const logout = () => {
        setUser(null);
    };

    const setHasCharacteristics = (value: boolean) => {
        setUser((prev) => (prev ? { ...prev, hasCharacteristics: value } : prev));
    };

    return (
        <AuthContext.Provider
            value={{ user, loading, login, logout, setHasCharacteristics }}
        >
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within AuthProvider");
    return ctx;
}
