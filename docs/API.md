# API contract

Agree on this early so frontend and backend can work in parallel with mock data.
Update this file BEFORE changing an endpoint, and warn whoever depends on it.

## Base URL
- Local: `http://localhost:8000`

## Endpoints

### `POST /example`
Request:
```json
{ "input": "string" }
```
Response:
```json
{ "result": "string", "score": 0.0 }
```
