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

The runner creates two local devices:

`deviceA`
`deviceB`

Example flow:
```
deviceA prepopulate-db
deviceB prepopulate-db
deviceA insert/update/delete local SQLite records
deviceA push changes to backend
deviceB pull changes from backend
deviceB applies pulled changes locally
deviceB commit-sync
```
Expected result:
```
Device B inserted customer name: Inserted From Device A
Device B updated customer city: Wroclaw
Device B deleted customer is gone: 'customer-id'
```

## WiP: Automated Regression Tests with Testcontainers
