import { describe, expect, it } from "vitest";
import { isPrivateIp, validateUrl } from "./parse";

describe("isPrivateIp", () => {
  it("blocks private and loopback ranges", () => {
    expect(isPrivateIp("127.0.0.1")).toBe(true);
    expect(isPrivateIp("10.0.0.1")).toBe(true);
    expect(isPrivateIp("172.16.0.1")).toBe(true);
    expect(isPrivateIp("192.168.1.1")).toBe(true);
    expect(isPrivateIp("169.254.169.254")).toBe(true);
    expect(isPrivateIp("100.64.0.1")).toBe(true);
    expect(isPrivateIp("localhost")).toBe(true);
    expect(isPrivateIp("app.localhost")).toBe(true);
    expect(isPrivateIp("8.8.8.8")).toBe(false);
    expect(isPrivateIp("example.com")).toBe(false);
  });

  it("allows the outer edges of the 172.16/12 block but not beyond", () => {
    expect(isPrivateIp("172.15.255.255")).toBe(false);
    expect(isPrivateIp("172.32.0.0")).toBe(false);
  });
});

describe("validateUrl", () => {
  it("rejects non-http(s) schemes", async () => {
    await expect(validateUrl("file:///etc/passwd")).rejects.toThrow("Invalid scheme");
    await expect(validateUrl("ftp://example.com/recipe")).rejects.toThrow("Invalid scheme");
  });

  it("blocks localhost and private IPs", async () => {
    await expect(validateUrl("http://127.0.0.1/recipe")).rejects.toThrow("SSRF blocked");
    await expect(validateUrl("http://localhost/recipe")).rejects.toThrow("SSRF blocked");
    await expect(validateUrl("http://10.0.0.1/internal")).rejects.toThrow("SSRF blocked");
    await expect(validateUrl("http://192.168.0.1/")).rejects.toThrow("SSRF blocked");
    await expect(validateUrl("http://169.254.169.254/latest/meta-data")).rejects.toThrow("SSRF blocked");
    await expect(validateUrl("https://172.31.0.1/")).rejects.toThrow("SSRF blocked");
  });

  it("blocks redirect-style hostnames resolving to private/loopback ranges", async () => {
    await expect(validateUrl("http://127.0.0.1:8080/redirect")).rejects.toThrow("SSRF blocked");
    await expect(validateUrl("https://metadata.localhost/")).rejects.toThrow("SSRF blocked");
  });
});
