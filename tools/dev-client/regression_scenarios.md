# Dev Client Regression Scenarios

## Scenario 1: Insert, Propagates To Second Device

### Arrange
- deviceA prepopulate
- deviceB prepopulate

### Act
- deviceA inserts a new `demo_customers` row
- deviceA pushes local changes
- deviceA pulls `demo_customers` (receiving missing rowid in inserted row)
- deviceB pulls `demo_customers` (receiving missing row)

### Assert: Device State
- deviceB has the inserted row
- inserted row fields match expected values
- deviceA has no local pending changes after pull
- deviceB has no local pending changes

### Assert: Backend State
- PostgreSQL has the inserted row
- inserted row fields match expected values

## Scenario 2: Update, Propagates to Second Device

### Arrange
- device A prepopulate
- device B prepopulate
- choose existing `demo_customers` row

### Act
- deviceA updates chosen row
- deviceA pushes local changes
- deviceB pulls `demo_customers`

### Assert: Device State
- deviceB has updated values
- row count did not change
- deviceA has no pending update/delete markers after push
- - deviceB has no local pending changes

### Assert: Backend State
- PostgreSQL has updated values for selected row
- PostgreSQL row count did not change

## Scenario 3: Delete, Propagates To Second Device

## Scenario 4: Insert, Update And Delete In One Push

## Scenario 5: Fresh Insert Requires Pull After Push

## Scenario 6: Pull Without Changes

## Scenario 7: Pulled Changes Do Not Become Local Changes

## Scenario 8: Two Devices Of The Same User Synchronize Same Tables

## Scenario 10: Update Conflict On Same Record

## Scenario 11: Delete, then Update On Same Record

## Scenario 12: Insert Then Delete Before Push

## Scenario 13: Multiple Tables Sync

## Scenario 15: Multiple pulls

## Scenario 16: Multiple payload push

## Scenario 18: Multiple Users With Multiple Devices

## Scenario 20: Data Types And Null Values




