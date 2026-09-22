import "@testing-library/jest-dom";
Object.defineProperty(URL, "createObjectURL", { writable: true, value: jest.fn(() => "blob:test") });
Object.defineProperty(URL, "revokeObjectURL", { writable: true, value: jest.fn() });
