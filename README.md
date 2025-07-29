# EventPlugin

Prototype plugin implementing basic event progress system for Paper 1.20.1.

## Features

* Start and stop events via `/event start <id>` and `/event stop <id>`.
* MythicMobs kills add random progress (0-5) and honour `Event Attrie` buff (+50%).
* Simple GUI to display player progress and configured rewards (`/event gui`).
* Admin GUI to configure rewards with `/event rewards`.
* Attrie item activation via right click (30 day buff).
* Event metadata (name, description, duration) stored in MySQL `events` table.
* MySQL connection configuration in `config.yml`.

This implementation is minimal and meant as a starting point based on the
conversation specification.
