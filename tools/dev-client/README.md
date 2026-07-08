# AMPLI-SYNC Dev Client

Java client for checking the ampli-sync backend synchronization flow without using the mobile client

Idea: follow the same sync flow as the React Native example, but in a simpler form for manual checks and regression tests.

## What it does

- backend health check,
- download SQLite database archive from `prepopulate-db`,
- unpack downloaded database archive,
- open local SQLite database,
- insert, update and delete local rows,
- build push payload from local SQLite changes,
- send local changes to the backend through `receive-changes`,
- clear local change markers after successful push,
- pull changes from the backend through `sync-compressed`,
- apply pulled inserts, updates and deletes to local SQLite,
- call `commit-sync` after applying pulled changes.

## Structure

- `SyncDevClient` handles  communication with the ampli-sync backend.
- `SqliteDatabase` handles local SQLite access.
- `PayloadBuilder` builds the push payload from local SQLite changes.
- `SyncDevice` represents one local sync device with its device id and SQLite database.
- `DevClientRunner` is a simple manual runner for checking the flow locally.

Helper records:

- `TableChanges` represents inserts and updates for one synchronized table.
- `DeletedRecord` - one deleted record sent in the push payload.
- `PayloadBuildResult` groups the push payload with cleanup statements.
- `ProcessedSqlStatement` SQL cleanup statement with arguments.
- `PullChanges` represents one table change package returned by `sync-compressed`.
- `PullRecords` contains pulled inserts, updates and deletes.


## Test - Manual check

Start the local backend from deploy-dev/docker.

Then run:

```
cd tools/dev-client
mvn compile
```

Run `DevClientRunner`.

## Automated Regression Tests with Testcontainers

The dev client contains Testcontainers regression tests.
These tests start the local ampli-sync backend and PostgreSQL from `deploy-dev/docker/docker-compose.test.yml`, then run sync scenarios with backend endpoints.

Before first tests, rebuild the backend WAR used by the Docker setup:

```bash
cd ../..
./deploy-dev/build-dev.sh
cd tools/dev-client
mvn test
```
  Manual docker compose up is not required. Testcontainers starts and stops the Docker Compose environment automatically.

### Current coverage:

- insert propagates to second device,
- update propagates to second device,
- delete propagates to second device,
- insert/update/delete in one push,
- fresh insert requires pull after push, to receive backend rowid,
- repeated pull without new backend changes does not change local SQLite,
- two devices of the same dev user synchronize tenant data.


### Future coverage

- direct PostgreSQL assertions after push,
- failed push does not clear local change markers,
- update/update conflict on the same record,
- delete/update conflict on the same record,
- insert then delete before push,
- multi-table synchronization.

### Notes:

- tests currently use dev user 1
  - tests create their own rows with UUIDs and should not depend on hardcoded seed row ids,
  - backend PostgreSQL assertions are not included yet,

