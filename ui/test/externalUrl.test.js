const { isAllowedExternalUrl } = require('../src/externalUrl');

test('lets the Microsoft device-login page through', () => {
  expect(isAllowedExternalUrl('https://www.microsoft.com/link')).toBe(true);
  expect(isAllowedExternalUrl('https://microsoft.com/link')).toBe(true);
});

test('refuses a host that is not Microsoft', () => {
  expect(isAllowedExternalUrl('https://evil.example/link')).toBe(false);
});

// The obvious way to write this check is a substring test, and the obvious way to beat
// a substring test is to put the allowed name somewhere it does not govern.
test('refuses hosts that merely contain the allowed name', () => {
  expect(isAllowedExternalUrl('https://microsoft.com.evil.example/link')).toBe(false);
  expect(isAllowedExternalUrl('https://notmicrosoft.com/link')).toBe(false);
  expect(isAllowedExternalUrl('https://evil.example/?next=microsoft.com')).toBe(false);
  expect(isAllowedExternalUrl('https://evil.example/#microsoft.com')).toBe(false);
});

// Credentials in the authority section are the classic way to make a hostile URL read as
// a trusted one: everything before the @ is a username, not a host.
test('refuses a URL whose userinfo impersonates the allowed host', () => {
  expect(isAllowedExternalUrl('https://www.microsoft.com@evil.example/link')).toBe(false);
});

test('refuses plain http even on the allowed host', () => {
  expect(isAllowedExternalUrl('http://www.microsoft.com/link')).toBe(false);
});

// shell.openExternal will happily hand a file: URL to the OS, which on Windows means
// running a program. This is the one that turns an open-a-page channel into an
// execute-anything channel.
test('refuses non-http schemes outright', () => {
  expect(isAllowedExternalUrl('file:///C:/Windows/System32/cmd.exe')).toBe(false);
  expect(isAllowedExternalUrl('javascript:alert(1)')).toBe(false);
  expect(isAllowedExternalUrl('ms-msdt:/id')).toBe(false);
});

test('refuses anything that is not a parseable URL', () => {
  expect(isAllowedExternalUrl('C:\\Windows\\System32\\cmd.exe')).toBe(false);
  expect(isAllowedExternalUrl('www.microsoft.com/link')).toBe(false);
  expect(isAllowedExternalUrl('')).toBe(false);
});

test('refuses anything that is not a string', () => {
  expect(isAllowedExternalUrl(undefined)).toBe(false);
  expect(isAllowedExternalUrl(null)).toBe(false);
  expect(isAllowedExternalUrl({ toString: () => 'https://www.microsoft.com/link' })).toBe(false);
});
