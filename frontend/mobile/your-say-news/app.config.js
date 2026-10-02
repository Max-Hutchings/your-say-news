// app.config.js
import devConfig from "./app.config.dev.js";
import prodConfig from "./app.config.prod.js";

export default ({ config }) => {
    const env = process.env.APP_ENV ?? "dev";

    const envConfig = env === "development" || env === "prod" ? prodConfig : devConfig;

    return {
        // base Expo config from app.json / defaults
        ...config,
        // override/extend with env-specific values
        ...envConfig,
        android: {
            ...config.android,
            ...envConfig.android,
        },
        extra: {
            ...config.extra,
            ...envConfig.extra,
        },
    };
};
