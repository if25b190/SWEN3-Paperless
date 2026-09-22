import nextConfig from "../next.config";

describe("Next API proxy", () => {
  it("uses the private backend URL with a local fallback", async () => {
    delete process.env.BACKEND_URL;
    const rewrites = await nextConfig.rewrites?.();
    expect(rewrites).toEqual([{ source: "/api/:path*", destination: "http://localhost:8080/:path*" }]);
  });
});
