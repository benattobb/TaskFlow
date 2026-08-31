# Security Policy

## Supported version

Security fixes are made on the current `main` branch. Please build from the latest source or use a release built from it.

## Reporting a vulnerability

Do not open a public issue for a suspected security vulnerability. Use GitHub's private vulnerability-reporting feature for this repository when it is available, or contact the repository owner privately with:

- a clear description and affected version or commit;
- minimal, safe reproduction steps;
- the impact you observed; and
- any suggested mitigation.

Please do not include Google OAuth tokens, task contents, or other personal data in a report. We will acknowledge a valid report, assess it, and coordinate a fix before public disclosure.

## Security boundaries

TaskFlow is a client application. It relies on Android's app sandbox, Google Play services OAuth flow, and Google API TLS validation. It intentionally does not collect analytics, use a cloud AI service, or embed API secrets. Users should install releases only from the repository owner or build from reviewed source.
