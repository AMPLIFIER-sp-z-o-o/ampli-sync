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


