import { describe, expect, it } from "vitest";
import { copyName } from "../copyName";

describe("copyName", () => {
  it("keeps the suffix visible within the 150 character limit", () => {
    expect(copyName("Offerta fissa")).toBe("Offerta fissa · copia");
    const long = copyName("N".repeat(150));
    expect(long).toHaveLength(150);
    expect(long.endsWith(" · copia")).toBe(true);
  });
});
