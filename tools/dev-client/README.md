# AMPLI-SYNC Dev Client

Java client for checking the ampli-sync backend synchronization flow without using the mobile client

Idea: follow the same sync flow as the React Native example, but in a simpler form for manual checks and regression tests.

## Implemented so far

- backend health check,
- download SQLite database archive from `prepopulate-db`,
- unpack downloaded database archive,
- open local SQLite database,
- check that demo tables exist, print content in runner

## Planned scope


1. Add local SQLite operations: insert, update, delete

2. Build payload with changes from local SQLite, following the logic:
   - rows with `rowid is null` as inserts,
   - rows with `mergeupdate > 0 and rowid is not null` as updates,
   - rows from `mergedelete` as deletes.

3. Send local changes to backend:
   - call `receive-changes/`,
   - verify that pushed data appears in PostgreSQL.

4. Pull changes from backend:
   - call sync endpoint for selected tables,
   - apply returned changes to local SQLite.

5. Use this client in full integration and regression tests:
 

## Structure

- `SyncDevClient` handles  communication with the ampli-sync backend.
- `SqliteDatabase` handles local SQLite access.
- Push payload builder - work in progress

## Test - Manual check

With the local Docker backend running, I run `DevClientRunner`.

Output:

```text
API[bb924e2] OK! Database connected!
demo_customers exists: true
Demo customers:
0b8e9b8e-0fb5-4f2d-8d4c-3c57e7dc8e47 | North Coast Shop | hello@northcoast.example | Gdansk
5e7b16b0-0c2f-4e9d-9f74-2d7a8f4c0b21 | Acme Retail | orders@acme.example | Warsaw
8fb5f9c7-9929-4f87-8fcb-19f2092f0a5d | Green Market | contact@greenmarket.example | Poznan
ad6510d7-c44f-4cfa-94c3-3f56a32a4c89 | Metro Office | office@metro.example | Krakow
```