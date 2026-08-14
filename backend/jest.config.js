module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  rootDir: '.',
  roots: ['<rootDir>/src'],
  testMatch: ['<rootDir>/src/**/__tests__/**/*.test.ts'],
  testPathIgnorePatterns: ['/node_modules/', '/.cargo/'],
  modulePathIgnorePatterns: ['<rootDir>/../'],
  verbose: true,
  forceExit: true
};
