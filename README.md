# EventPlugin

Prototype plugin implementing basic event progress system for Paper 1.20.1.

## Features

* Toggleable progress event with `/event toggle`.
* MythicMobs kills add random progress (0-5) and honour `Event Attrie` buff (+50%).
* Simple GUI to display player progress and configured rewards (`/event gui`).
* Admin command to add rewards from held item: `/event addreward <progress>`.
* Attrie item activation via right click (30 day buff).
* MySQL connection configuration in `config.yml`.

This implementation is minimal and meant as a starting point based on the
conversation specification.
