import type { NextConfig } from "next";

const backend = process.env.BACKEND_URL || "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  // In production Nginx routes /api directly to the backend; this rewrite makes local dev and
  // single-container setups work too.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;
