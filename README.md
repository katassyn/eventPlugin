# EventPlugin

Prototype plugin implementing basic event progress system for Paper 1.20.1.

## Features

* Start and stop events via `/event start <id>` and `/event stop <id>`.
* MythicMobs kills add progress according to per-event drop chances and honour `Event Attrie` buff (+50%).

* Simple GUI to display player progress and configured rewards (`/event gui`).
* Admin GUI to configure rewards with `/event rewards`.
* Attrie item activation via right click (30 day buff).
* Event metadata (name, description, duration) stored in MySQL `events` table.

* MySQL connection configuration in `config.yml`.

This implementation is minimal and meant as a starting point based on the
conversation specification.

## Example event configuration

`config.yml` defines events under the `events` section. Example:

```yaml
events:
  "1":
    name: "Monster Hunt"
    active: true
    duration_days: 14
    max_progress: 12000
    description: "Monster hunt has begun, kill all monsters to get rewards."
    drop_chances:
      "0": 75
      "1": 10
      "2": 10
      "3": 3
      "5": 2
```

Drop chances are percentages for each progress value when a configured MythicMob dies.
=======

